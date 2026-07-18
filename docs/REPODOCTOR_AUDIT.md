# RepoDoctor — Architecture & Defect Audit Report

> **Audit Date:** 2026-07-15  
> **Scope:** Full backend (Spring Boot) + frontend (React/Vite)  
> **Mode:** Read-only inspection — no code was modified.

---

## 1. Complete Data-Flow Map

```
GitHub URL (frontend input)
  │
  ▼
POST /api/analyze  (AnalyzeController)
  │
  ├─► RepositoryParserService.parseRepositoryUrl()     → owner, repo
  ├─► GitHubService.getRepository()                    → repoData (stars, language, branch …)
  ├─► GitHubService.getRepositoryTree()                → full file tree
  ├─► RepositoryFileFilterService.filterUsefulFiles()  → usefulFiles[]
  │
  ├─► RepositoryHygieneService.detectHygieneIssues()   → hygieneIssues[]
  ├─► RepositoryHygieneScoreService.calculateScore()   → hygieneScore (0-100)
  │
  ├─► GitHubService.getReadme()                        → readmeData (Base64)
  ├─► ReadmeAnalyzerService.analyzeReadme()            → readmeScore (0-100), readmeChecks{}
  │
  ├─► ProjectStructureAnalyzerService.analyzeStructure()  → structureScore (0-100) + clutter lists
  │
  ├─► GitHubService.getCommits()                       → commits[] (last 100)
  ├─► CommitQualityAnalyzerService.analyzeCommits()    → commitQualityScore (0-100) + counts
  │
  ├─► DocumentationQualityAnalyzerService.analyzeDocumentation()
  │     └─► RepositoryContentService.getRawFileContent() × N  (raw.githubusercontent.com)
  │     → documentationScore (0-100)
  │
  ├─► CodeQualityAnalyzerService.analyzeCodeQuality()
  │     └─► RepositoryContentService.getRawFileContent() × N
  │     → codeQualityScore (0-100)
  │
  ├─► OverallRepositoryScoreService.calculateOverallScore()  → overallScore, grade, health
  ├─► RepositoryRecommendationService.generateRecommendations() → recommendations[]
  │
  ├─► AnalysisResultService.saveAnalysis()             → PostgreSQL (analysis_results table)
  │
  └─► ResponseEntity<Map>  ← single flat JSON blob returned to frontend

GET /api/analyses/{owner}/{repo}/summary              → RepositorySummaryService
GET /api/analyses/{owner}/{repo}/insights             → RepositoryInsightsService
GET /api/analyses/{owner}/{repo}/risk                 → RepositoryRiskAssessmentService
GET /api/analyses/{owner}/{repo}/recommendations      → RecommendationFilterService
GET /api/analyses/{owner}/{repo}/action-plan          → RepositoryActionPlanService
GET /api/analyses/{owner}/{repo}/progress             → RepositoryProgressService
GET /api/analyses/{owner}/{repo}/compare              → RepositoryComparisonService
GET /api/analyses/{owner}/{repo}/issues/evolution     → RepositoryIssueEvolutionService
GET /api/analyses/{owner}/{repo}/report/pdf           → RepositoryPdfReportService
GET /api/analyses/{owner}/{repo}/history              → AnalysisController (separate)

React frontend fetches all of the above in parallel with Promise.allSettled()
after /api/analyze completes.
```

---

## 2. Defect Catalogue

### 2.1 Fake / Heuristic Scoring

| # | Location | Defect |
|---|----------|--------|
| H-1 | `ReadmeAnalyzerService` L94–102 | Score is a pure **keyword-presence checklist** — headings are matched by regex against fixed strings (`"installation"`, `"usage"`, `"license"` …). A README with those headings but completely empty bodies scores identically to one with rich content. The scoring is therefore **fake relative to content quality**. |
| H-2 | `CommitQualityAnalyzerService` L380, L397 | Commit "quality" is decided by message **character length** (`< 8` → GENERIC, `< 15` → WEAK). An 8-character message `"fix typo"` is classified GOOD if it contains an action verb. Length is a heuristic, not a quality signal. |
| H-3 | `CodeQualityAnalyzerService` L246–281 | Code quality score starts at **100 and only deducts penalties**. There is no positive signal from good code (e.g., test coverage, naming quality). A repo with no detectable problems receives 100/100 regardless of actual quality. |
| H-4 | `RepositoryHygieneScoreService` L14–43 | Score starts at **100**; deductions are hard-coded per keyword string match inside the issue message (e.g., `issue.contains(".gitignore")`). If the issue message wording ever changes, the deduction silently disappears. |
| H-5 | `DocumentationQualityAnalyzerService` L125–127 | When `sourceFiles.isEmpty()`, `fileDocumentationCoverage` is hard-coded to **100.0**, giving a perfect score even when the repo has no analysable source. |
| H-6 | `DocumentationQualityAnalyzerService` L133–135 | When `totalDocumentableElements == 0`, `elementDocumentationCoverage` is **100.0** — the same default-perfect problem for projects with no functions/classes. |

---

### 2.2 Default Perfect Scores

| # | Location | Defect |
|---|----------|--------|
| P-1 | `DocumentationQualityAnalyzerService` L125–127 | `fileDocumentationCoverage = 100.0` when no source files found. |
| P-2 | `DocumentationQualityAnalyzerService` L133–135 | `elementDocumentationCoverage = 100.0` when no documentable elements found. |
| P-3 | `CodeQualityAnalyzerService` L246 | `score = 100` before any deductions; a repo with zero source files yields code quality score = **100**. |
| P-4 | `RepositoryHygieneScoreService` L14 | `score = MAX_SCORE (100)` start; a repo with no hygiene issues (which is fine) still silently produces 100. The problem is the asymmetry — even empty repos with nothing to check score 100. |

---

### 2.3 Default Zero Scores

| # | Location | Defect |
|---|----------|--------|
| Z-1 | `AnalyzeController` L500–511 (helper `getInteger`) | If any sub-analyzer returns a `null` or non-numeric `score`, the helper returns **0** and that zero propagates silently into the overall weighted score and into persistence. No warning is logged. |
| Z-2 | `CommitQualityAnalyzerService` L500–502 | If `qualityRelevantCommits <= 0` (all commits are merge commits), returns **score = 0** — even though the repo may have excellent code in all other dimensions. |
| Z-3 | `RepositoryInsightsService` L276–280 (`safeScore`) | Null scores from DB are treated as **0** when finding strongest/weakest area, making a null-score area appear as the weakest. |
| Z-4 | `RepositoryRiskAssessmentService` L186–189 (`addRiskArea`) | Null scores → 0, so null data is classified as **CRITICAL risk** (score < 40). |

---

### 2.4 N/A Fallback Branches

| # | Location | Defect |
|---|----------|--------|
| N-1 | `RepositoryPdfReportService` L502–506 | `addScoreRow()` renders `"N/A"` in the PDF table when a score is null. This is correct defensively but there is **no upstream guardrail** preventing null scores from reaching the entity. The PDF consumer must compensate for a broken persistence layer. |
| N-2 | `AnalysisReportService` L52–87 | Branch `snapshot == null` emits `status = "LEGACY_ANALYSIS_SNAPSHOT_UNAVAILABLE"`. The frontend does **not** handle this status — it will attempt to read keys that do not exist. |
| N-3 | `App.jsx` L6–53 (`renderValue`) | `null/undefined` renders as string `"N/A"` — used everywhere including numeric score bars. The `ScoreCard` (L856) does `Number(value) || 0` so a missing score shows as `0/100` rather than `N/A`. Inconsistent treatment between components. |

---

### 2.5 Unchecked Missing-Data Behavior

| # | Location | Defect |
|---|----------|--------|
| M-1 | `ReadmeAnalyzerService` L17–18 | `readmeData.get("content").toString()` — **NullPointerException** if the repository has no README. `GitHubService.getReadme()` throws a `GitHubApiException(404)` for repos without a README, which propagates uncaught through `AnalyzeController` and aborts the entire analysis. |
| M-2 | `RepositoryFileFilterService` L14–15 | `repositoryTree.get("tree")` is cast directly to `List` without null check — **ClassCastException** if GitHub returns a tree with `"truncated": true` and a partial `"tree"` or no `"tree"` key. |
| M-3 | `RepositoryHygieneService` L15–16 | Same unchecked cast of `repositoryTree.get("tree")` — identical crash risk. |
| M-4 | `AnalyzeController` L85 | `repositoryData.get("default_branch").toString()` — **NullPointerException** if GitHub omits `default_branch` (rare but possible for empty repos). |
| M-5 | `RepositoryComparisonService` L177–178 | `latest.getOverallScore() - previous.getOverallScore()` — **NullPointerException** if either score is `null` (Integer unboxing). No null guard. |
| M-6 | `DocumentationQualityAnalyzerService` L67–69 | `if (content == null || content.isBlank()) { fetchFailedFileCount++; continue; }` counts failed fetches but the score penalty is only 2 points/file and capped at 10. Systematic fetch failures (e.g., rate-limited raw.githubusercontent.com) inflate the documentation score. |
| M-7 | `CodeQualityAnalyzerService` L93–103 | Same issue — fetch failures add to `fetchFailedFileCount`, penalty is 2 pts/file capped at 10. A repo with 100 files where 50 fail fetches would have a 10-pt cap instead of proportional penalty. |

---

### 2.6 Analyzers That Do Not Inspect Real Repository Content

| # | Analyzer | What it actually checks |
|---|----------|------------------------|
| R-1 | `RepositoryHygieneService` | Only checks **file paths** in the tree (`.gitignore` present, `.vs/`, `__pycache__/`, `.env` exists). Never reads any file content. |
| R-2 | `ProjectStructureAnalyzerService` | Only checks **file paths** in the tree (clutter, misplacement, suspicious naming). Never reads any file content. |
| R-3 | `ReadmeAnalyzerService` | Fetches README via GitHub API but inspects only **heading names by regex**. Does not read body text quality, link validity, or code sample presence. |
| R-4 | `CommitQualityAnalyzerService` | Inspects only the **commit message first line**. Does not check commit size, diff complexity, linked issue references, or co-author attribution. |
| R-5 | `RepositoryHygieneService` | Does not check for `.idea/` committed (only checks `.vs/`), does not check `node_modules/`, does not check committed `*.jar`, `*.exe`, or large binary files. |

---

### 2.7 Duplicate Analysis Logic

| # | Files | Duplicate Logic |
|---|-------|-----------------|
| D-1 | `DocumentationQualityAnalyzerService` & `CodeQualityAnalyzerService` | Both independently count `TODO/FIXME/HACK/XXX` occurrences using an identical regex pattern (`\b(TODO|FIXME|HACK|XXX)\b`). Results are stored in both `documentationAnalysis.todoFixmeCount` and `codeQualityAnalysis.todoFixmeCount`. The frontend would show two different TODO counts, and both feed separate score deductions. |
| D-2 | `RepositoryActionPlanService.extractRecommendations()` & `RecommendationFilterService.extractRecommendations()` | Identical method bodies — both walk the `analysisSnapshot.recommendations` list and rebuild maps entry-by-entry. |
| D-3 | `AnalysisResultService.getInteger()` & `RepositorySummaryService.getInteger()` & `RepositoryRecommendationService.getInteger()` & `AnalyzeController.getInteger()` | Four separate identical private helpers for extracting an integer from a `Map<String,Object>`. |
| D-4 | `AnalysisHistoryService.getRepositoryHistory()` is only a one-line wrapper around the repository query already available in every service that calls it. Three services (`RepositoryActionPlanService`, `RepositoryRiskAssessmentService`, `RepositoryIssueEvolutionService`) could call the repository directly; the wrapper adds a layer without abstraction value. |

---

### 2.8 Inconsistent Score Scales

| # | Analyzer | Score Range / Ceiling issue |
|---|----------|-----------------------------|
| S-1 | `ReadmeAnalyzerService` | Maximum attainable score: 15+20+15+10+10+10+10+5+5 = **100**. However all nine sections must be present. A missing `screenshots` section (10 pts) is penalised as heavily as a missing `installation` section (20 pts) relative to the total. Weighting does not reflect actual impact. |
| S-2 | `CommitQualityAnalyzerService` | Quality ratio multiplied by **85**, conventional ratio by **10**, history bonus **+5**, repeated message penalty up to **−15**. Theoretical max = 85+10+5 = **100**, but theoretical minimum is **−10** (before `Math.max(0, …)` guard). The component weights (85/10/5) are undocumented. |
| S-3 | `OverallRepositoryScoreService` | Weights: Hygiene=0.15, README=0.15, Structure=0.15, CommitQuality=0.15, Documentation=0.15, CodeQuality=0.25. **Total = 1.00.** However `CodeQuality` gets 1.67× the weight of every other dimension with no stated rationale. |
| S-4 | `RepositoryRiskAssessmentService.determineRiskLevel()` | Risk thresholds: CRITICAL < 40, HIGH 40–59, MEDIUM 60–74, LOW ≥75. But `determineRiskLevel` is never cross-validated against `OverallRepositoryScoreService.calculateGrade()` thresholds (A+ ≥90, A ≥80, B ≥70, C ≥60, D ≥50, F < 50). A score of 58 is grade D (moderate) but risk HIGH — possible user confusion. |

---

### 2.9 DTO / API Contract Mismatches

| # | Backend Field | Frontend Expectation | Issue |
|---|---------------|----------------------|-------|
| C-1 | `RepositoryComparisonService` emits `structureScore` | `App.jsx` `ComparisonTable` reads `comparison.projectStructureScore` (line 1032) | **Key name mismatch** — backend: `structureScore`, frontend: `projectStructureScore`. The cell is always blank. |
| C-2 | `AnalyzeResponse.java` DTO (3 fields: owner, repository, status) | Actual response from `AnalyzeController` is a `Map<String,Object>` with 40+ fields — `AnalyzeResponse` is **never used**. The DTO is dead code. |
| C-3 | `RecommendationFilterService` response has key `recommendations` | `App.jsx` line 246: `normalizeList(details?.recommendations)` — reads `details.recommendations` directly. But the wrapper response also contains `analysisId`, `owner`, `repository`, `status`. The frontend unwraps the full `recommendations` endpoint and extracts the `recommendations` array correctly. This works but the frontend silently ignores all wrapper metadata. |
| C-4 | `RepositoryProgressService` response keys: `firstScore`, `latestScore`, `bestScore`, `worstScore`, `averageScore`, `totalChange`, `firstAnalyzedAt`, `latestAnalyzedAt` | `App.jsx` reads `details.progress.latestScore`, `details.progress.bestScore`, `details.progress.averageScore`, `details.progress.totalChange`, `details.progress.firstAnalyzedAt`, `details.progress.latestAnalyzedAt` — **matches**. But also renders `details.progress.analysisCount` — **matches**. No mismatch here. |
| C-5 | `RepositoryIssueEvolutionService` returns `resolvedIssues` as a `LinkedHashSet` serialised to JSON array | `App.jsx` does `normalizeList(evolution?.resolvedIssues)` — **works** because JSON serialisation converts Set → Array. Fragile: if the Set serialisation contract changes, the frontend would receive `{}` or break. |

---

### 2.10 Frontend Fields That Do Not Match Backend Responses

| # | App.jsx Location | Field Read | Backend Reality | Impact |
|---|-----------------|------------|-----------------|--------|
| F-1 | `ComparisonTable` line 1032 | `comparison.projectStructureScore` | Backend key is `structureScore` | **Comparison table "Project Structure" row is always blank/undefined** |
| F-2 | `RecommendationCard` line 958 | `recommendation.description` | Backend key is `recommendation` (not `description`) in the recommendations list built by `RepositoryRecommendationService.addRecommendation()` | **Recommendation body text is never displayed** |
| F-3 | `ActionPlanItem` line 1009 | `action.description` | `RepositoryActionPlanService` maps the backend key `recommendation` to `action` in the action plan item, not `description` | **Action plan item body is never displayed** |
| F-4 | `App.jsx` line 246 | `details?.recommendations` | `/api/analyses/{owner}/{repo}/recommendations` returns `{ recommendations: [...], status, priorityFilter, ... }` — the full object, not just the array | **`normalizeList(details?.recommendations)` → `normalizeList(undefined)` → `[]`** because `details.recommendations` is the full wrapper object which is truthy but not an array. Filter tabs and cards never render. |
| F-5 | Recommendation filter tabs line 588 | Filters: `["ALL","HIGH","MEDIUM","LOW"]` | Backend `RepositoryRecommendationService` also generates `"CRITICAL"` priority | **CRITICAL recommendations are not filterable in the UI** |

---

## 3. Architecture Summary

### 3.1 What Works Well
- The **core analysis pipeline** (hygiene → readme → structure → commits → documentation → code → overall → recommendations → persist) is logically sequential and complete.
- `RepositoryContentService` fetches real file contents from `raw.githubusercontent.com` — documentation and code quality analyzers read actual file bytes.
- The **snapshot persistence** model (storing the full JSON blob in `analysis_snapshot jsonb`) is excellent for issue evolution and historical comparison.
- `RepositoryIssueEvolutionService` correctly diffs issue sets between snapshots.
- The PDF report correctly assembles insights, risk, and action plan from stored data.

### 3.2 Critical Bugs (would produce wrong results on every analysis)

1. **F-4** — Recommendations list is always empty in the UI (wrong key path).
2. **F-2 / F-3** — Recommendation and action plan body text never rendered (wrong field name `description` vs `recommendation`/`action`).
3. **F-1** — Comparison table Project Structure column always blank (wrong key `projectStructureScore` vs `structureScore`).
4. **M-1** — Any repo without a README crashes the entire analysis with NPE.
5. **M-5** — `compareLatestWithPrevious` throws NPE on null scores.

### 3.3 Scoring Integrity Issues (scores are not trustworthy)

1. **H-5, H-6, P-3** — Empty or non-source repos receive perfect documentation and code quality scores.
2. **H-1** — README score ignores content quality; heading presence alone earns full marks.
3. **Z-1** — Silent zero propagation on analyzer failure inflates overall score downward without feedback.
4. **D-1** — TODO/FIXME counted twice in two dimensions, deducted from two scores independently — double-penalising the same issue.

### 3.4 Missing Features vs Claimed Analysis
- Hygiene does not detect committed `node_modules/`, `.idea/`, binary files, or secrets inside config files.
- Code quality does not assess cyclomatic complexity, test coverage, dependency health, or naming conventions.
- Documentation quality does not assess README *content*, only *structure*.
- Commit analysis is limited to 100 commits (GitHub API default).

---

## 4. File Reference Index

| File | Key Issues |
|------|-----------|
| [AnalyzeController.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/controller/AnalyzeController.java) | M-4, Z-1 |
| [ReadmeAnalyzerService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/ReadmeAnalyzerService.java) | H-1, M-1, R-3 |
| [RepositoryHygieneService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RepositoryHygieneService.java) | M-3, R-1, R-5 |
| [RepositoryHygieneScoreService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RepositoryHygieneScoreService.java) | H-4, P-4 |
| [ProjectStructureAnalyzerService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/ProjectStructureAnalyzerService.java) | R-2 |
| [CommitQualityAnalyzerService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/CommitQualityAnalyzerService.java) | H-2, R-4, Z-2, S-2 |
| [DocumentationQualityAnalyzerService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/DocumentationQualityAnalyzerService.java) | H-5, H-6, M-6, P-1, P-2, D-1 |
| [CodeQualityAnalyzerService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/CodeQualityAnalyzerService.java) | H-3, M-7, P-3, D-1 |
| [OverallRepositoryScoreService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/OverallRepositoryScoreService.java) | S-3, S-4 |
| [RepositoryRecommendationService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RepositoryRecommendationService.java) | F-2, F-5 (source) |
| [RepositoryComparisonService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RepositoryComparisonService.java) | C-1, M-5 |
| [RepositoryActionPlanService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RepositoryActionPlanService.java) | D-2, F-3 (source) |
| [RecommendationFilterService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RecommendationFilterService.java) | D-2, F-4 (source) |
| [RepositoryInsightsService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RepositoryInsightsService.java) | Z-3 |
| [RepositoryRiskAssessmentService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RepositoryRiskAssessmentService.java) | Z-4, S-4, N-1 |
| [RepositoryFileFilterService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/RepositoryFileFilterService.java) | M-2 |
| [AnalysisResultService.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/service/AnalysisResultService.java) | D-3 |
| [AnalyzeResponse.java](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor/src/main/java/com/omyadav/repodoctor/dto/AnalyzeResponse.java) | C-2 (dead code) |
| [App.jsx](file:///c:/Users/Asus/Downloads/repodoctor/repodoctor-frontend/src/App.jsx) | F-1, F-2, F-3, F-4, F-5, N-3 |
