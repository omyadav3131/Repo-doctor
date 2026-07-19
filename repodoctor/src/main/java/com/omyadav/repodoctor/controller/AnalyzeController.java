package com.omyadav.repodoctor.controller;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;
import com.omyadav.repodoctor.dto.*;
import com.omyadav.repodoctor.entity.AnalysisResult;
import com.omyadav.repodoctor.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeController.class);

    private static final int ANALYSIS_TIMEOUT_SECONDS = 90;

    private final RepositoryParserService repositoryParserService;
    private final GitHubService gitHubService;
    private final RepositoryFileFilterService repositoryFileFilterService;
    private final RepositoryHygieneService repositoryHygieneService;
    private final ReadmeAnalyzerService readmeAnalyzerService;
    private final ProjectStructureAnalyzerService projectStructureAnalyzerService;
    private final CommitQualityAnalyzerService commitQualityAnalyzerService;
    private final DocumentationQualityAnalyzerService documentationQualityAnalyzerService;
    private final CodeQualityAnalyzerService codeQualityAnalyzerService;
    private final OverallRepositoryScoreService overallRepositoryScoreService;
    private final RepositoryRecommendationService repositoryRecommendationService;
    private final AnalysisResultService analysisResultService;
    private final RepositoryTypeDetector repositoryTypeDetector;
    private final Executor analysisExecutor;

    public AnalyzeController(
            RepositoryParserService repositoryParserService,
            GitHubService gitHubService,
            RepositoryFileFilterService repositoryFileFilterService,
            RepositoryHygieneService repositoryHygieneService,
            ReadmeAnalyzerService readmeAnalyzerService,
            ProjectStructureAnalyzerService projectStructureAnalyzerService,
            CommitQualityAnalyzerService commitQualityAnalyzerService,
            DocumentationQualityAnalyzerService documentationQualityAnalyzerService,
            CodeQualityAnalyzerService codeQualityAnalyzerService,
            OverallRepositoryScoreService overallRepositoryScoreService,
            RepositoryRecommendationService repositoryRecommendationService,
            AnalysisResultService analysisResultService,
            RepositoryTypeDetector repositoryTypeDetector,
            @Qualifier("analysisExecutor") Executor analysisExecutor) {

        this.repositoryParserService = repositoryParserService;
        this.gitHubService = gitHubService;
        this.repositoryFileFilterService = repositoryFileFilterService;
        this.repositoryHygieneService = repositoryHygieneService;
        this.readmeAnalyzerService = readmeAnalyzerService;
        this.projectStructureAnalyzerService = projectStructureAnalyzerService;
        this.commitQualityAnalyzerService = commitQualityAnalyzerService;
        this.documentationQualityAnalyzerService = documentationQualityAnalyzerService;
        this.codeQualityAnalyzerService = codeQualityAnalyzerService;
        this.overallRepositoryScoreService = overallRepositoryScoreService;
        this.repositoryRecommendationService = repositoryRecommendationService;
        this.analysisResultService = analysisResultService;
        this.repositoryTypeDetector = repositoryTypeDetector;
        this.analysisExecutor = analysisExecutor;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeRepository(
            @Valid @RequestBody AnalyzeRequest request) {

        // PARSE REPOSITORY URL
        String[] repositoryDetails = repositoryParserService.parseRepositoryUrl(request.getRepositoryUrl());
        String owner = repositoryDetails[0];
        String repository = repositoryDetails[1];
        String repositoryUrl = request.getRepositoryUrl().trim();

        // FETCH REPOSITORY INFORMATION
        Map<String, Object> repositoryData = gitHubService.getRepository(owner, repository);

        Object defaultBranchObj = repositoryData.get("default_branch");
        String defaultBranch = defaultBranchObj != null ? defaultBranchObj.toString() : "main";

        // CHECK IF ARCHIVED
        boolean archived = Boolean.TRUE.equals(repositoryData.get("archived"));

        // EXTRACT LICENSE
        String license = extractLicense(repositoryData);

        // FETCH REPOSITORY TREE
        Map<String, Object> repositoryTree = gitHubService.getRepositoryTree(owner, repository, defaultBranch);

        boolean treeWasTruncated = Boolean.TRUE.equals(repositoryTree.get("truncated"));

        // FILTER USEFUL FILES
        List<String> usefulFiles = repositoryFileFilterService.filterUsefulFiles(repositoryTree);

        RepositoryMetadata metadata = new RepositoryMetadata(
                owner,
                repository,
                repositoryUrl,
                (String) repositoryData.get("description"),
                safeInteger(repositoryData.get("stargazers_count")),
                safeInteger(repositoryData.get("forks_count")),
                safeInteger(repositoryData.get("watchers_count")),
                safeInteger(repositoryData.get("open_issues_count")),
                (String) repositoryData.get("language"),
                license,
                defaultBranch,
                (String) repositoryData.get("updated_at"),
                archived,
                usefulFiles.size());

        AnalysisScope scope = new AnalysisScope(
                100,
                50,
                treeWasTruncated,
                "Analysis covers the most recent 100 commits and up to 50 source code files.");

        // HANDLE EMPTY REPOSITORIES
        if (usefulFiles.isEmpty()) {
            return buildEmptyRepositoryResponse(metadata, scope, repositoryUrl);
        }

        List<String> warnings = new ArrayList<>();

        if (archived) {
            warnings.add("This repository is archived and is no longer actively maintained.");
        }

        String repositoryType = repositoryTypeDetector.detectRepositoryType(metadata.getLanguage(), usefulFiles);

        // PARALLELIZE ANALYZERS WITH TIMEOUT
        CompletableFuture<DimensionResult> hygieneFuture = CompletableFuture.supplyAsync(
                () -> repositoryHygieneService.analyzeHygiene(repositoryTree, repositoryType), analysisExecutor);

        CompletableFuture<DimensionResult> readmeFuture = CompletableFuture.supplyAsync(() -> {
            Map<String, Object> readmeData = gitHubService.getReadme(owner, repository);
            if (readmeData == null) {
                return DimensionResult.notAnalyzable("No README file found in the repository");
            }
            return readmeAnalyzerService.analyzeReadme(readmeData, repositoryType);
        }, analysisExecutor);

        CompletableFuture<DimensionResult> structureFuture = CompletableFuture.supplyAsync(
                () -> projectStructureAnalyzerService.analyzeStructure(owner, repository, defaultBranch, repositoryTree, repositoryType), analysisExecutor);

        CompletableFuture<DimensionResult> commitQualityFuture = CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> commits = gitHubService.getCommits(owner, repository);
            return commitQualityAnalyzerService.analyzeCommits(commits, repositoryType);
        }, analysisExecutor);

        CompletableFuture<DimensionResult> documentationFuture = CompletableFuture.supplyAsync(
                () -> documentationQualityAnalyzerService.analyzeDocumentation(owner, repository, defaultBranch, usefulFiles, repositoryType),
                analysisExecutor);

        CompletableFuture<DimensionResult> codeQualityFuture = CompletableFuture.supplyAsync(
                () -> codeQualityAnalyzerService.analyzeCodeQuality(owner, repository, defaultBranch, usefulFiles, repositoryType),
                analysisExecutor);

        // AWAIT ALL COMPLETIONS WITH TIMEOUT
        DimensionResult hygiene;
        DimensionResult readme;
        DimensionResult structure;
        DimensionResult commitQuality;
        DimensionResult documentation;
        DimensionResult codeQuality;

        try {
            CompletableFuture.allOf(
                    hygieneFuture, readmeFuture, structureFuture,
                    commitQualityFuture, documentationFuture, codeQualityFuture
            ).get(ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            hygiene = hygieneFuture.join();
            readme = readmeFuture.join();
            structure = structureFuture.join();
            commitQuality = commitQualityFuture.join();
            documentation = documentationFuture.join();
            codeQuality = codeQualityFuture.join();

        } catch (TimeoutException e) {
            log.warn("Analysis timed out for {}/{}, collecting partial results", owner, repository);
            warnings.add("Analysis timed out after " + ANALYSIS_TIMEOUT_SECONDS
                    + " seconds. Some dimensions may have incomplete results.");

            hygiene = safeGet(hygieneFuture);
            readme = safeGet(readmeFuture);
            structure = safeGet(structureFuture);
            commitQuality = safeGet(commitQualityFuture);
            documentation = safeGet(documentationFuture);
            codeQuality = safeGet(codeQualityFuture);

        } catch (Exception e) {
            log.error("Analysis failed for {}/{}: {}", owner, repository, e.getMessage());
            warnings.add("Some analysis dimensions failed: " + e.getMessage());

            hygiene = safeGet(hygieneFuture);
            readme = safeGet(readmeFuture);
            structure = safeGet(structureFuture);
            commitQuality = safeGet(commitQualityFuture);
            documentation = safeGet(documentationFuture);
            codeQuality = safeGet(codeQualityFuture);
        }

        // OVERALL SCORE
        OverallResult overall = overallRepositoryScoreService.calculateOverallScore(
                hygiene, readme, structure, commitQuality, documentation, codeQuality,
                repositoryType);

        // RECOMMENDATION ENGINE
        List<Recommendation> recommendations = repositoryRecommendationService.generateRecommendations(
                hygiene, readme, structure, commitQuality, documentation, codeQuality, repositoryType);

        // ASSEMBLE RESPONSE
        String status = overall.getStatus() == AnalysisStatus.SUCCESS ? "REPOSITORY_ANALYSIS_COMPLETED"
                : "REPOSITORY_ANALYSIS_PARTIAL";
        warnings.addAll(overall.getWarnings());

        AnalysisResponse.Builder responseBuilder = AnalysisResponse.builder()
                .repository(metadata)
                .scope(scope)
                .hygiene(overall.getWeightedDimensions().getOrDefault("Hygiene", hygiene))
                .readme(overall.getWeightedDimensions().getOrDefault("README", readme))
                .structure(overall.getWeightedDimensions().getOrDefault("Project Structure", structure))
                .commitQuality(overall.getWeightedDimensions().getOrDefault("Commit Quality", commitQuality))
                .documentation(overall.getWeightedDimensions().getOrDefault("Documentation", documentation))
                .codeQuality(overall.getWeightedDimensions().getOrDefault("Code Quality", codeQuality))
                .overall(overall)
                .recommendations(recommendations)
                .status(status)
                .warnings(warnings);

        // SAVE ANALYSIS RESULT TO POSTGRESQL
        try {
            AnalysisResponse pendingResponse = responseBuilder.build();
            AnalysisResult savedAnalysis = analysisResultService.saveAnalysis(pendingResponse, repositoryUrl);

            responseBuilder.analysisId(savedAnalysis.getId());
            responseBuilder.analyzedAt(savedAnalysis.getAnalysisDate());
        } catch (Exception e) {
            log.error("Failed to save analysis result: {}", e.getMessage());
            warnings.add("Analysis result was not persisted to the database: " + e.getMessage());
            responseBuilder.warnings(warnings);
        }

        return ResponseEntity.ok(responseBuilder.build());
    }

    private ResponseEntity<AnalysisResponse> buildEmptyRepositoryResponse(
            RepositoryMetadata metadata, AnalysisScope scope, String repositoryUrl) {

        DimensionResult emptyDimension = DimensionResult.notAnalyzable("Repository has no analyzable files");
        String repositoryType = repositoryTypeDetector.detectRepositoryType(metadata.getLanguage(), List.of());
        OverallResult overall = overallRepositoryScoreService.calculateOverallScore(
                emptyDimension, emptyDimension, emptyDimension,
                emptyDimension, emptyDimension, emptyDimension,
                repositoryType);

        List<Recommendation> recommendations = List.of();
        List<String> warnings = List.of("Repository appears to be empty or contains no analyzable files.");

        AnalysisResponse response = AnalysisResponse.builder()
                .repository(metadata)
                .scope(scope)
                .hygiene(overall.getWeightedDimensions().getOrDefault("Hygiene", emptyDimension))
                .readme(overall.getWeightedDimensions().getOrDefault("README", emptyDimension))
                .structure(overall.getWeightedDimensions().getOrDefault("Project Structure", emptyDimension))
                .commitQuality(overall.getWeightedDimensions().getOrDefault("Commit Quality", emptyDimension))
                .documentation(overall.getWeightedDimensions().getOrDefault("Documentation", emptyDimension))
                .codeQuality(overall.getWeightedDimensions().getOrDefault("Code Quality", emptyDimension))
                .overall(overall)
                .recommendations(recommendations)
                .status("REPOSITORY_ANALYSIS_COMPLETED")
                .warnings(warnings)
                .build();

        try {
            AnalysisResult saved = analysisResultService.saveAnalysis(response, repositoryUrl);
            response = AnalysisResponse.builder()
                    .repository(metadata)
                    .scope(scope)
                    .hygiene(overall.getWeightedDimensions().getOrDefault("Hygiene", emptyDimension))
                    .readme(overall.getWeightedDimensions().getOrDefault("README", emptyDimension))
                    .structure(overall.getWeightedDimensions().getOrDefault("Project Structure", emptyDimension))
                    .commitQuality(overall.getWeightedDimensions().getOrDefault("Commit Quality", emptyDimension))
                    .documentation(overall.getWeightedDimensions().getOrDefault("Documentation", emptyDimension))
                    .codeQuality(overall.getWeightedDimensions().getOrDefault("Code Quality", emptyDimension))
                    .overall(overall)
                    .recommendations(recommendations)
                    .status("REPOSITORY_ANALYSIS_COMPLETED")
                    .warnings(warnings)
                    .analysisId(saved.getId())
                    .analyzedAt(saved.getAnalysisDate())
                    .build();
        } catch (Exception e) {
            log.error("Failed to save empty repo analysis: {}", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    private String extractLicense(Map<String, Object> repositoryData) {
        Object licenseObj = repositoryData.get("license");
        if (licenseObj instanceof Map<?, ?> licenseMap) {
            Object name = licenseMap.get("spdx_id");
            if (name != null && !"NOASSERTION".equals(name)) {
                return name.toString();
            }
            name = licenseMap.get("name");
            return name != null ? name.toString() : null;
        }
        return null;
    }

    private DimensionResult safeGet(CompletableFuture<DimensionResult> future) {
        try {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                return future.getNow(null);
            }
        } catch (Exception e) {
            log.debug("Dimension future failed: {}", e.getMessage());
        }
        return DimensionResult.fetchFailed("Analysis dimension timed out or failed");
    }

    private static Integer safeInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}