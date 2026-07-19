# 🏛️ RepoDoctor Architecture Documentation

This document outlines the high-level system architecture, data flows, and internal pipelines powering RepoDoctor.

---

## 1. High-Level System Architecture

RepoDoctor is built using a decoupled Client-Server architecture. The frontend is a Single Page Application (SPA) served via a lightweight development server or CDN, communicating with a backend REST API built on Spring Boot. The backend orchestrates external requests to the GitHub API, caches responses, analyzes data concurrently, and persists analysis results to a file-based H2 database.

```mermaid
graph TD
    Client[React SPA Client] -->|HTTP/REST| API(Spring Boot REST API)
    API -->|JPA/Hibernate| DB[(H2 Database)]
    API <-->|HTTP/Bearer Token| GitHub(GitHub API)
    
    subgraph Backend Server
        API --> Engine(Analysis Engine)
        Engine --> Cache[Caffeine Cache]
    end
```

---

## 2. Backend Data Flow

The Spring Boot backend is divided into several layers to enforce separation of concerns:
- **Controllers:** Handle HTTP requests and input validation.
- **Services:** Execute business logic and orchestrate analysis tasks.
- **Analyzers:** Specialized classes evaluating specific dimensions (e.g., Code Quality, Structure).
- **Repositories:** Interfaces interacting with the PostgreSQL database.

```mermaid
sequenceDiagram
    participant User as Client
    participant Controller as AnalyzeController
    participant Orchestrator as AnalysisOrchestrator
    participant Analyzers as DimensionAnalyzers (6x)
    participant GitHub as GitHub API
    participant DB as H2 Database

    User->>Controller: POST /api/analyze {repoUrl}
    Controller->>Orchestrator: Initiate Synchronous Analysis (90s timeout)
    
    par Parallel Dimension Analysis
        Orchestrator->>Analyzers: Analyze Hygiene
        Orchestrator->>Analyzers: Analyze Code Quality
        Orchestrator->>Analyzers: Analyze Documentation...
    end
    
    Analyzers->>GitHub: Fetch Trees/Files (Parallel)
    GitHub-->>Analyzers: Return Raw Content
    Analyzers-->>Orchestrator: Return DimensionResults
    
    Orchestrator->>Orchestrator: Calculate Overall Score
    Orchestrator->>DB: Save AnalysisSnapshot (JSON)
    
    Orchestrator-->>Controller: Return AnalysisResponse
    Controller-->>User: Return 200 OK (Completed AnalysisResponse)
```

---

## 3. Analysis Engine Pipeline

RepoDoctor's core is the Analysis Engine. To handle large repositories without hitting API timeouts, the engine employs dynamic weight redistribution and asynchronous file fetching.

```mermaid
flowchart TD
    Start([Receive Repository URL]) --> Parse[Parse Owner & Repo Name]
    Parse --> Fetch[Fetch Repository Metadata]
    Fetch --> Type{Determine Repo Type}
    
    Type -->|Standard| AnalyzeAll[Trigger All 6 Analyzers]
    Type -->|Documentation| ExcludeCode[Exclude Code Quality Analyzer]
    Type -->|Empty| Skip[Skip Deep Analysis]
    
    AnalyzeAll --> CQA(Code Quality Analyzer)
    AnalyzeAll --> HYG(Hygiene Analyzer)
    AnalyzeAll --> DOC(Documentation Analyzer)
    
    CQA -->|Fetch max 50 files| ParallelFetch[Parallel CompletableFuture Fetch]
    ParallelFetch --> Regex[Regex/Keyword Scanning]
    Regex --> Result[Score & Evidence Generation]
    
    Result --> Merge[Merge Dimension Scores]
    ExcludeCode --> Merge
    Skip --> Merge
    
    Merge --> Recs[Recommendation Engine]
    Recs --> Persist[(Save to DB)]
```

---

## 4. Database Schema

The database uses a NoSQL-in-SQL approach. While relational structures are used for indexing, the complex, highly dynamic analysis results are stored as a JSON string snapshot to allow maximum flexibility without rigid schema migrations.

```mermaid
erDiagram
    ANALYSIS_RESULT {
        UUID id PK
        String repositoryUrl
        String owner
        String repositoryName
        Timestamp analysisDate
        String status
        Integer overallScore
        String grade
        String repositoryHealth
        String analysisSnapshot "JSON Payload (Dimensions, Recommendations)"
    }
```
