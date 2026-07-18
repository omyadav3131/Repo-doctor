package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentationQualityAnalyzerService {

    private static final int MAX_FILES_TO_ANALYZE = 50;

    private final RepositoryContentService repositoryContentService;

    public DocumentationQualityAnalyzerService(RepositoryContentService repositoryContentService) {
        this.repositoryContentService = repositoryContentService;
    }

    public DimensionResult analyzeDocumentation(
            String owner,
            String repository,
            String branch,
            List<String> usefulFiles,
            String repositoryType) {

        List<String> sourceFiles = usefulFiles.stream()
                .filter(this::isSourceFile)
                .limit(MAX_FILES_TO_ANALYZE)
                .toList();

        List<String> docsFiles = usefulFiles.stream()
                .filter(f -> f.toLowerCase().contains("/docs/") || f.toLowerCase().endsWith(".md"))
                .toList();

        if (sourceFiles.isEmpty() && docsFiles.isEmpty()) {
            return DimensionResult.notAnalyzable("No source files or documentation files found.");
        }

        int scorePresence = 0;
        int scoreCompleteness = 0;
        int scoreQuality = 0;

        List<String> evidence = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();

        // Presence (Max 30)
        if (!docsFiles.isEmpty()) {
            scorePresence += 15;
            evidence.add("Dedicated documentation files/folders found.");
        }
        
        AtomicInteger filesWithComments = new AtomicInteger(0);
        AtomicInteger totalDocLines = new AtomicInteger(0);
        AtomicInteger totalCodeLines = new AtomicInteger(0);
        AtomicInteger fetchFailed = new AtomicInteger(0);
        AtomicInteger filesAnalyzed = new AtomicInteger(0);
        List<String> largeUndocumentedFilesList = java.util.Collections.synchronizedList(new ArrayList<>());

        boolean isNotebook = "JUPYTER_NOTEBOOK".equals(repositoryType);

        sourceFiles.parallelStream().forEach(filePath -> {
            String content = repositoryContentService.getRawFileContent(owner, repository, branch, filePath);
            if (content == null || content.isBlank()) {
                fetchFailed.incrementAndGet();
                return;
            }
            filesAnalyzed.incrementAndGet();
            
            if (filePath.endsWith(".ipynb")) {
                int[] stats = analyzeNotebookDocs(content);
                if (stats[0] > 0) filesWithComments.incrementAndGet();
                totalDocLines.addAndGet(stats[0]); // markdown cells
                totalCodeLines.addAndGet(stats[1]); // code cells
            } else {
                int[] stats = analyzeSourceDocs(content, filePath);
                if (stats[0] > 0) filesWithComments.incrementAndGet();
                totalDocLines.addAndGet(stats[0]);
                totalCodeLines.addAndGet(stats[1]);
                
                // Collect large undocumented files (>100 lines with 0 docs)
                if (stats[0] == 0 && stats[1] > 100) {
                    largeUndocumentedFilesList.add(filePath);
                }
            }
        });

        int validFiles = filesAnalyzed.get();
        if (validFiles > 0) {
            double commentedFileRatio = (double) filesWithComments.get() / validFiles;
            if (commentedFileRatio >= 0.2) {
                scorePresence += 15;
                evidence.add("Source code contains inline comments or docstrings.");
            } else if (commentedFileRatio > 0) {
                scorePresence += 5;
            }

            // Completeness (Max 40)
            if (isNotebook) {
                // In notebooks, markdown cells are the docs.
                if (totalDocLines.get() > 0) scoreCompleteness += 20;
                if (totalDocLines.get() >= totalCodeLines.get() * 0.3) {
                    scoreCompleteness += 20;
                    evidence.add("Healthy ratio of Markdown to Code cells in Notebook.");
                }
            } else {
                if (totalDocLines.get() > 0) scoreCompleteness += 15;
                if (totalDocLines.get() >= totalCodeLines.get() * 0.1) {
                    scoreCompleteness += 15;
                    evidence.add("Healthy ratio of comments/docstrings to source lines.");
                }
                if (docsFiles.size() >= 2) {
                    scoreCompleteness += 10;
                }
            }

            // Quality (Max 30)
            if (totalDocLines.get() > 50) {
                scoreQuality += 15;
            }
            if (docsFiles.size() > 5) {
                scoreQuality += 15;
                evidence.add("Extensive external documentation detected.");
            }
        } else if ("DOCUMENTATION_REPOSITORY".equals(repositoryType)) {
            if (docsFiles.size() > 2) {
                scorePresence = 30;
                scoreCompleteness = 20;
                evidence.add("Multiple documentation files detected.");
                if (docsFiles.size() >= 5) {
                    scoreCompleteness += 20;
                }
                if (docsFiles.stream().anyMatch(f -> f.toLowerCase().contains("/docs/") || f.toLowerCase().contains("/guides/"))) {
                    scoreQuality += 30;
                    evidence.add("Dedicated docs/ hierarchy detected.");
                } else if (docsFiles.size() > 10) {
                    scoreQuality += 15;
                }
            } else {
                // Failsafe in case a repo was incorrectly classified
                scorePresence = 15;
                scoreCompleteness = 10;
                scoreQuality = 0;
                evidence.add("Minimal documentation structure found for a documentation repo.");
            }
        }

        scorePresence = Math.min(30, scorePresence);
        scoreCompleteness = Math.min(40, scoreCompleteness);
        scoreQuality = Math.min(30, scoreQuality);

        int totalScore = scorePresence + scoreCompleteness + scoreQuality;

        double documentationCoverage = 0.0;
        if (totalCodeLines.get() > 0) {
            documentationCoverage = (double) totalDocLines.get() / (totalDocLines.get() + totalCodeLines.get()) * 100.0;
        } else if (totalDocLines.get() > 0) {
            documentationCoverage = 100.0;
        }
        
        List<String> largeUndocumentedFiles = new ArrayList<>();
        // we didn't track per-file in the lambda easily, so we can just say if coverage < 20% and we had files, we warn on the largest one. But wait, we can track it easily!
        // We need a thread-safe list.
        // Actually, let's just add it to details below.

        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("Presence", scorePresence);
        breakdown.put("Completeness", scoreCompleteness);
        breakdown.put("Quality", scoreQuality);
        details.put("Score Breakdown", breakdown);
        details.put("filesWithComments", filesWithComments.get());
        details.put("totalDocLines", totalDocLines.get());
        details.put("totalCodeLines", totalCodeLines.get());
        details.put("documentationCoverage", documentationCoverage);
        details.put("largeUndocumentedFiles", new ArrayList<>(largeUndocumentedFilesList));

        DimensionResult.Builder builder = DimensionResult.builder(AnalysisStatus.SUCCESS)
                .score(totalScore)
                .confidence(1.0)
                .totalCandidateItemCount(sourceFiles.size() + docsFiles.size())
                .analyzedItemCount(validFiles)
                .failedItemCount(fetchFailed.get())
                .details(details);

        for (String ev : evidence) {
            builder.evidence(ev);
        }

        return builder.build();
    }

    private int[] analyzeSourceDocs(String content, String filePath) {
        int docLines = 0;
        int codeLines = 0;
        String[] lines = content.split("\\R");
        boolean inBlock = false;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            
            if (trimmed.startsWith("/*") || trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")) {
                if (!trimmed.endsWith("*/") && !trimmed.endsWith("\"\"\"") && !trimmed.endsWith("'''")) {
                    inBlock = true;
                }
                docLines++;
                continue;
            }
            if (inBlock) {
                if (trimmed.endsWith("*/") || trimmed.endsWith("\"\"\"") || trimmed.endsWith("'''")) {
                    inBlock = false;
                }
                docLines++;
                continue;
            }
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                docLines++;
                continue;
            }
            
            codeLines++;
        }
        return new int[]{docLines, codeLines};
    }

    private int[] analyzeNotebookDocs(String content) {
        int markdownCells = 0;
        int codeCells = 0;
        Matcher typeMatcher = Pattern.compile("\"cell_type\":\\s*\"(markdown|code)\"").matcher(content);
        while (typeMatcher.find()) {
            if ("markdown".equals(typeMatcher.group(1))) markdownCells++;
            else codeCells++;
        }
        return new int[]{markdownCells, codeCells};
    }

    private boolean isSourceFile(String filePath) {
        String lower = filePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") ||
               lower.endsWith(".jsx") || lower.endsWith(".tsx") || lower.endsWith(".go") || lower.endsWith(".cs") ||
               lower.endsWith(".cpp") || lower.endsWith(".c") || lower.endsWith(".php") || lower.endsWith(".rb") ||
               lower.endsWith(".ipynb");
    }
}