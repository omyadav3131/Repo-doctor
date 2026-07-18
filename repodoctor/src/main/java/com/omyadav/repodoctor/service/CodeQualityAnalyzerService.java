package com.omyadav.repodoctor.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;

@Service
public class CodeQualityAnalyzerService {

    private final RepositoryContentService repositoryContentService;

    private static final Pattern TODO_PATTERN = Pattern.compile("(?i)\\b(TODO|FIXME|HACK)\\b");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(api[_-]?key|secret|password)\\s*[=:]\\s*([\"'])([^\"'\\r\\n]{8,})\\2");

    public CodeQualityAnalyzerService(RepositoryContentService repositoryContentService) {
        this.repositoryContentService = repositoryContentService;
    }

    public DimensionResult analyzeCodeQuality(String owner, String repository, String branch, List<String> usefulFiles, String repositoryType) {
        
        List<String> sourceFiles = filterSourceFiles(usefulFiles);

        if (sourceFiles.isEmpty()) {
            return DimensionResult.notAnalyzable("No analyzable source code found (only config/docs/vendor).");
        }

        int score = 100;
        int largeFiles = 0;
        int filesWithTodos = 0;
        int filesWithSecrets = 0;
        int analyzedCount = 0;
        
        int totalImplementationLines = 0;
        int implementationHeavyFileCount = 0;
        int generatedLikeFileCount = 0;
        int debugStatementCount = 0;

        List<String> possibleSecretFiles = new ArrayList<>();
        List<String> largeSourceFiles = new ArrayList<>();
        List<String> debugStatementFiles = new ArrayList<>();
        
        List<String> evidence = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();

        List<java.util.concurrent.CompletableFuture<String>> contentFutures = new ArrayList<>();
        for (String file : sourceFiles) {
            contentFutures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                String content = repositoryContentService.getRawFileContent(owner, repository, branch, file);
                return content != null ? file + "|||---|||" + content : null;
            }));
        }

        List<String> contentsWithFileNames = contentFutures.stream()
                .map(java.util.concurrent.CompletableFuture::join)
                .filter(java.util.Objects::nonNull)
                .toList();

        for (String item : contentsWithFileNames) {
            String[] parts = item.split("\\|\\|\\|---\\|\\|\\|", 2);
            String file = parts[0];
            String content = parts[1];
            
            if (content.isBlank()) continue;

            analyzedCount++;
            String[] lines = content.split("\\R");
            int nonBlankLines = 0;
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    nonBlankLines++;
                }
            }
            totalImplementationLines += nonBlankLines;
            
            if (nonBlankLines > 50) {
                implementationHeavyFileCount++;
            }
            if (nonBlankLines > 1000 || file.contains("generated")) {
                generatedLikeFileCount++;
            }

            if (lines.length > 500) {
                largeFiles++;
                largeSourceFiles.add(file);
            }

            if (TODO_PATTERN.matcher(content).find()) {
                filesWithTodos++;
            }

            if (SECRET_PATTERN.matcher(content).find()) {
                filesWithSecrets++;
                possibleSecretFiles.add(file);
            }
            
            if (content.contains("console.log(") || content.contains("System.out.println(") || content.contains("print(")) {
                debugStatementCount++;
                debugStatementFiles.add(file);
            }
        }

        if (analyzedCount == 0) {
            return DimensionResult.notAnalyzable("Failed to fetch contents of any source files.");
        }

        // Quality Deductions (Simple, practical heuristics)
        int penaltyLarge = Math.min(30, largeFiles * 5); // -5 per large file, max 30
        int penaltyTodos = Math.min(20, filesWithTodos * 2); // -2 per todo file, max 20
        int penaltySecrets = Math.min(40, filesWithSecrets * 20); // -20 per secret, max 40
        int penaltyDebug = Math.min(10, debugStatementCount * 2); // -2 per debug file, max 10

        score = Math.max(0, score - penaltyLarge - penaltyTodos - penaltySecrets - penaltyDebug);

        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("Base Score", 100);
        breakdown.put("Large File Penalty", -penaltyLarge);
        breakdown.put("TODO/FIXME Penalty", -penaltyTodos);
        breakdown.put("Hardcoded Secrets Penalty", -penaltySecrets);
        breakdown.put("Debug Statements Penalty", -penaltyDebug);
        
        details.put("Score Breakdown", breakdown);
        details.put("sourceFilesAnalyzed", analyzedCount);
        details.put("realSourceFileCount", analyzedCount); // keeping old name just in case
        details.put("totalImplementationLines", totalImplementationLines);
        details.put("implementationHeavyFileCount", implementationHeavyFileCount);
        details.put("generatedLikeFileCount", generatedLikeFileCount);
        details.put("largeSourceFileCount", largeFiles);
        details.put("largeSourceFiles", largeSourceFiles);
        details.put("todoFixmeCount", filesWithTodos);
        details.put("possibleSecretCount", filesWithSecrets);
        details.put("possibleSecretFiles", possibleSecretFiles);
        details.put("debugStatementCount", debugStatementCount);
        details.put("debugStatementFiles", debugStatementFiles);
        details.put("longFunctionCount", 0); // Not implemented currently

        if (largeFiles > 0) evidence.add(largeFiles + " files are very large (>500 lines).");
        if (filesWithTodos > 0) evidence.add(filesWithTodos + " files contain TODO/FIXME comments.");
        if (filesWithSecrets > 0) evidence.add(filesWithSecrets + " files appear to contain hardcoded secrets or API keys.");
        if (debugStatementCount > 0) evidence.add(debugStatementCount + " files contain leftover debug statements (e.g., console.log, print).");
        
        if (evidence.isEmpty()) {
            evidence.add("No major code quality issues detected (no huge files, secrets, or excess TODOs).");
        }

        DimensionResult.Builder builder = DimensionResult.builder(AnalysisStatus.SUCCESS)
                .score(score)
                .confidence(1.0)
                .totalCandidateItemCount(sourceFiles.size())
                .analyzedItemCount(analyzedCount)
                .failedItemCount(sourceFiles.size() - analyzedCount)
                .details(details);

        for (String ev : evidence) {
            builder.evidence(ev);
        }

        return builder.build();
    }

    private List<String> filterSourceFiles(List<String> files) {
        List<String> valid = new ArrayList<>();
        if (files == null) return valid;
        
        for (String f : files) {
            String lower = f.toLowerCase(Locale.ROOT).replace("\\", "/");
            
            // Ignore vendors, builds, minified
            if (lower.contains("node_modules/") || lower.contains("vendor/") || lower.contains("venv/") || lower.contains(".venv/")) continue;
            if (lower.contains("dist/") || lower.contains("build/") || lower.contains("target/") || lower.contains("out/")) continue;
            if (lower.contains("coverage/")) continue;
            if (lower.endsWith(".min.js") || lower.endsWith(".min.css") || lower.endsWith("-min.js")) continue;
            if (lower.endsWith(".d.ts")) continue; // TS declarations aren't logic

            if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") ||
                lower.endsWith(".jsx") || lower.endsWith(".tsx") || lower.endsWith(".go") || lower.endsWith(".cs") ||
                lower.endsWith(".cpp") || lower.endsWith(".c") || lower.endsWith(".php") || lower.endsWith(".rb")) {
                valid.add(f);
            }
        }
        
        // Limit to 50 files for speed
        if (valid.size() > 50) {
            return valid.subList(0, 50);
        }
        return valid;
    }
}