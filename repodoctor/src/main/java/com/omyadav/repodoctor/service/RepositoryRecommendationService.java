package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.analysis.DimensionResult;
import com.omyadav.repodoctor.dto.Recommendation;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RepositoryRecommendationService {

    public List<Recommendation> generateRecommendations(
            DimensionResult hygiene,
            DimensionResult readme,
            DimensionResult structure,
            DimensionResult commitQuality,
            DimensionResult documentation,
            DimensionResult codeQuality,
            String repositoryType) {

        List<Recommendation> recommendations = new ArrayList<>();

        /*
         * HYGIENE RECOMMENDATIONS
         */
        if (hygiene != null && hygiene.getScore() != null && hygiene.getScore() < 90) {
            recommendations.add(new Recommendation(
                    "HIGH",
                    "REPOSITORY_HYGIENE",
                    "Clean repository metadata and generated files",
                    "Remove IDE metadata, generated files and unnecessary committed artifacts.",
                    hygiene.getIssues()));
        }

        /*
         * README RECOMMENDATIONS
         */
        if (readme != null && readme.getScore() != null && readme.getScore() < 90) {
            List<String> missingReadmeSections = findMissingReadmeSections(getMap(readme.getDetails(), "readmeChecks"));
            recommendations.add(new Recommendation(
                    readme.getScore() < 60 ? "HIGH" : "MEDIUM",
                    "README",
                    "Improve README documentation",
                    buildReadmeMessage(missingReadmeSections),
                    missingReadmeSections));
        }

        /*
         * PROJECT STRUCTURE
         */
        if ("EMPTY".equals(repositoryType)) {
            recommendations.add(new Recommendation(
                    "HIGH",
                    "PROJECT_STRUCTURE",
                    "Initialize the repository",
                    "This repository appears to be completely empty. Start by adding a README, a LICENSE, and your source code.",
                    Collections.emptyList()));
        } else if ("README_ONLY".equals(repositoryType)) {
            recommendations.add(new Recommendation(
                    "HIGH",
                    "PROJECT_STRUCTURE",
                    "Add meaningful implementation",
                    "This repository only contains a minimal README. Add actual source code, documentation, or project files to make it a complete repository.",
                    Collections.emptyList()));
        } else if (structure != null) {
            int rootClutterCount = getInteger(structure.getDetails(), "rootClutterCount");
            if (rootClutterCount > 0) {
                recommendations.add(new Recommendation(
                        rootClutterCount >= 10 ? "HIGH" : "MEDIUM",
                        "PROJECT_STRUCTURE",
                        "Reduce root directory clutter",
                        "Move database utilities, migration scripts and non-essential artifacts into dedicated directories.",
                        getFormattedStringList(structure.getDetails(), "rootClutter")));
            }

            int duplicateCount = getInteger(structure.getDetails(), "duplicateLookingFileCount");
            if (duplicateCount > 0) {
                recommendations.add(new Recommendation(
                        "MEDIUM",
                        "PROJECT_STRUCTURE",
                        "Review duplicate-looking files",
                        "Review files with identical names and remove or rename genuinely duplicated implementations.",
                        formatAffectedFiles(flattenDuplicateFiles(structure.getDetails().get("duplicateLookingFiles")))));
            }

            int suspiciousFileCount = getInteger(structure.getDetails(), "suspiciousNamedFileCount");
            if (suspiciousFileCount > 0) {
                recommendations.add(new Recommendation(
                        "LOW",
                        "PROJECT_STRUCTURE",
                        "Review suspicious versioned filenames",
                        "Avoid filenames such as v2, final, copy or backup when version control already tracks file history.",
                        getFormattedStringList(structure.getDetails(), "suspiciousNamedFiles")));
            }

            if (structure.getScore() != null && structure.getScore() < 50) {
                recommendations.add(new Recommendation(
                        "HIGH",
                        "PROJECT_STRUCTURE",
                        "Reorganize project structure",
                        "The repository structure score is low. Separate application code, scripts, documentation, tests and generated data.",
                        Collections.emptyList()));
            }
        }

        /*
         * COMMIT QUALITY
         */
        if (commitQuality != null) {
            int genericCommitCount = getInteger(commitQuality.getDetails(), "genericCommitCount");
            int repeatedMessageCount = getInteger(commitQuality.getDetails(), "repeatedMessageCount");

            if (genericCommitCount > 0) {
                recommendations.add(new Recommendation(
                        "LOW",
                        "COMMIT_QUALITY",
                        "Use descriptive commit messages",
                        "Replace generic commit messages with clear action-oriented descriptions of the actual change.",
                        getFormattedStringList(commitQuality.getDetails(), "genericCommitMessages")));
            }

            if (repeatedMessageCount > 0) {
                List<String> repeatedCommitIssues = getFormattedStringList(commitQuality.getDetails(), "commitIssues")
                        .stream()
                        .filter(issue -> issue.startsWith("Repeated commit message:"))
                        .toList();

                recommendations.add(new Recommendation(
                        "LOW",
                        "COMMIT_QUALITY",
                        "Avoid repeated commit messages",
                        "Write commit messages that describe each individual change instead of repeatedly reusing the same message.",
                        formatAffectedFiles(repeatedCommitIssues)));
            }

            if (commitQuality.getScore() != null && commitQuality.getScore() < 50) {
                recommendations.add(new Recommendation(
                        "MEDIUM",
                        "COMMIT_QUALITY",
                        "Improve commit history quality",
                        "Use focused commits and descriptive action-oriented messages. Consider a consistent conventional commit format.",
                        Collections.emptyList()));
            }
        }

        /*
         * DOCUMENTATION QUALITY
         */
        if ("EMPTY".equals(repositoryType) || "README_ONLY".equals(repositoryType)) {
            // Documentation recommendations are handled by the structural recommendations
        } else if (documentation != null) {
            double documentationCoverage = getDouble(documentation.getDetails(), "documentationCoverage");

            if (documentation.getScore() != null && documentation.getScore() < 40) {
                recommendations.add(new Recommendation(
                        "HIGH",
                        "DOCUMENTATION",
                        "Increase source code documentation",
                        "Documentation quality is critically low. Add meaningful docstrings or documentation comments to important classes, functions and routes.",
                        documentation.getIssues()));
            } else if (documentation.getScore() != null && documentation.getScore() < 70) {
                recommendations.add(new Recommendation(
                        "MEDIUM",
                        "DOCUMENTATION",
                        "Improve source code documentation",
                        "Add documentation to important public functions, classes and complex business logic.",
                        documentation.getIssues()));
            }

            if (documentationCoverage < 30.0) {
                recommendations.add(new Recommendation(
                        "HIGH",
                        "DOCUMENTATION",
                        "Improve documentation coverage",
                        "Current source-file documentation coverage is only " + roundTwoDecimals(documentationCoverage)
                                + "%. Prioritize core application and route files.",
                        getFormattedStringList(documentation.getDetails(), "largeUndocumentedFiles")));
            }
        }

        /*
         * CODE QUALITY
         */
        if (codeQuality != null) {
            int sourceFilesAnalyzed = getInteger(codeQuality.getDetails(), "sourceFilesAnalyzed");
            int implementationHeavyFileCount = getInteger(codeQuality.getDetails(), "implementationHeavyFileCount");
            int totalImplementationLines = getInteger(codeQuality.getDetails(), "totalImplementationLines");
            int generatedLikeFileCount = getInteger(codeQuality.getDetails(), "generatedLikeFileCount");

            if (sourceFilesAnalyzed > 0
                    && (implementationHeavyFileCount < 4 || totalImplementationLines < 250 || generatedLikeFileCount > 0)) {

                List<String> implementationEvidence = new ArrayList<>();
                implementationEvidence.add("Analyzed source files: " + sourceFilesAnalyzed);
                implementationEvidence.add("Substantial implementation files: " + implementationHeavyFileCount);
                implementationEvidence.add("Implementation lines: " + totalImplementationLines);
                implementationEvidence.add("Generated/template-like files: " + generatedLikeFileCount);

                recommendations.add(new Recommendation(
                        "CRITICAL",
                        "IMPLEMENTATION_DEPTH",
                        "Increase real implementation depth",
                        "Repository appears to contain limited business implementation. Add production-grade services, domain logic and tests before expecting a high quality score.",
                        implementationEvidence));
            }

            int possibleSecretCount = getInteger(codeQuality.getDetails(), "possibleSecretCount");
            if (possibleSecretCount > 0) {
                recommendations.add(new Recommendation(
                        "CRITICAL",
                        "SECURITY",
                        "Remove possible hardcoded secrets",
                        "Move credentials and secrets to environment variables and rotate any exposed credentials immediately.",
                        getFormattedStringList(codeQuality.getDetails(), "possibleSecretFiles")));
            }

            int largeSourceFileCount = getInteger(codeQuality.getDetails(), "largeSourceFileCount");
            if (largeSourceFileCount > 0) {
                recommendations.add(new Recommendation(
                        "HIGH",
                        "CODE_QUALITY",
                        "Split large source files",
                        "Break large source files into smaller modules or services with focused responsibilities.",
                        getFormattedStringList(codeQuality.getDetails(), "largeSourceFiles")));
            }

            int longFunctionCount = getInteger(codeQuality.getDetails(), "longFunctionCount");
            if (longFunctionCount > 0) {
                recommendations.add(new Recommendation(
                        "HIGH",
                        "CODE_QUALITY",
                        "Refactor long functions",
                        "Extract long functions into smaller reusable functions with single responsibilities.",
                        getFormattedStringList(codeQuality.getDetails(), "longFunctionFiles")));
            }

            int debugStatementCount = getInteger(codeQuality.getDetails(), "debugStatementCount");
            if (debugStatementCount > 0) {
                recommendations.add(new Recommendation(
                        debugStatementCount >= 20 ? "HIGH" : "MEDIUM",
                        "CODE_QUALITY",
                        "Remove production debug statements",
                        "Replace temporary print or console debug statements with structured application logging where necessary.",
                        getFormattedStringList(codeQuality.getDetails(), "debugStatementFiles")));
            }

            if (codeQuality.getScore() != null && codeQuality.getScore() < 50) {
                recommendations.add(new Recommendation(
                        "HIGH",
                        "CODE_QUALITY",
                        "Prioritize code quality refactoring",
                        "The code quality score is low. Prioritize modularization, function size reduction and cleanup before adding major features.",
                        Collections.emptyList()));
            }
        }

        /*
         * DEDUPLICATE AND SORT BY PRIORITY
         */
        Map<String, Recommendation> uniqueRecommendations = new LinkedHashMap<>();
        for (Recommendation rec : recommendations) {
            uniqueRecommendations.putIfAbsent(rec.getTitle(), rec);
        }
        
        List<Recommendation> finalRecommendations = new ArrayList<>(uniqueRecommendations.values());
        finalRecommendations.sort(Comparator.comparingInt(r -> priorityRank(r.getPriority())));

        return finalRecommendations;
    }

    private List<String> findMissingReadmeSections(Map<String, Object> readmeChecks) {
        List<String> missingSections = new ArrayList<>();
        if (readmeChecks == null)
            return missingSections;

        for (Map.Entry<String, Object> entry : readmeChecks.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                missingSections.add(entry.getKey());
            }
        }
        return missingSections;
    }

    private String buildReadmeMessage(List<String> missingSections) {
        if (missingSections.isEmpty()) {
            return "Improve README clarity, formatting and developer onboarding information.";
        }
        return "Add or improve missing README sections: " + String.join(", ", missingSections) + ".";
    }

    private List<String> flattenDuplicateFiles(Object duplicateObject) {
        List<String> files = new ArrayList<>();
        if (!(duplicateObject instanceof Map<?, ?> duplicateMap)) {
            return files;
        }
        for (Object value : duplicateMap.values()) {
            if (!(value instanceof Collection<?> collection)) {
                continue;
            }
            for (Object item : collection) {
                if (item != null) {
                    files.add(item.toString());
                }
            }
        }
        return files;
    }

    private List<String> getFormattedStringList(Map<String, Object> source, String key) {
        List<String> result = new ArrayList<>();
        if (source == null)
            return result;
        Object value = source.get(key);
        if (!(value instanceof Collection<?> collection))
            return result;
        for (Object item : collection) {
            if (item != null)
                result.add(item.toString());
        }
        return formatAffectedFiles(result);
    }
    
    private List<String> formatAffectedFiles(List<String> files) {
        if (files == null || files.isEmpty()) return files;
        if (files.size() <= 10) return files;
        
        List<String> formatted = new ArrayList<>(files.subList(0, 10));
        formatted.add("+ " + (files.size() - 10) + " more files affected.");
        return formatted;
    }

    private int getInteger(Map<String, Object> source, String key) {
        if (source == null)
            return 0;
        Object value = source.get(key);
        if (value instanceof Number number)
            return number.intValue();
        return 0;
    }

    private double getDouble(Map<String, Object> source, String key) {
        if (source == null)
            return 0.0;
        Object value = source.get(key);
        if (value instanceof Number number)
            return number.doubleValue();
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        if (source == null)
            return null;
        Object value = source.get(key);
        if (value instanceof Map<?, ?>)
            return (Map<String, Object>) value;
        return null;
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            case "LOW" -> 3;
            default -> 4;
        };
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}