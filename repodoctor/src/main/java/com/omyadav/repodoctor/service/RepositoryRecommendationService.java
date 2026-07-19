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
        if (hygiene != null && hygiene.getDetails() != null) {
            boolean hasDirtyFiles = Boolean.TRUE.equals(hygiene.getDetails().get("dirtyFilesFound"));
            if (hasDirtyFiles) {
                List<String> explicitDirtyFiles = getFormattedStringList(hygiene.getDetails(), "dirtyFiles");
                recommendations.add(new Recommendation(
                        "HIGH",
                        "REPOSITORY_HYGIENE",
                        "Clean repository metadata and generated files",
                        "Remove IDE metadata, generated files and unnecessary committed artifacts.",
                        explicitDirtyFiles,
                        "Detected " + explicitDirtyFiles.size() + " generated/dirty files committed to the repository.",
                        String.join(", ", explicitDirtyFiles.subList(0, Math.min(explicitDirtyFiles.size(), 3))) + (explicitDirtyFiles.size() > 3 ? "..." : ""),
                        "Prevents merge conflicts, reduces repository size, and improves security.",
                        15));
            }
        }

        /*
         * README RECOMMENDATIONS
         */
        if (readme != null && readme.getDetails() != null) {
            List<String> missingReadmeSections = findMissingReadmeSections(getMap(readme.getDetails(), "readmeChecks"));
            if (!missingReadmeSections.isEmpty()) {
                recommendations.add(new Recommendation(
                        missingReadmeSections.size() > 3 ? "HIGH" : "MEDIUM",
                        "README",
                        "Improve README documentation",
                        buildReadmeMessage(missingReadmeSections),
                        missingReadmeSections,
                        "Important README sections are missing.",
                        "Missing: " + String.join(", ", missingReadmeSections),
                        "Improves developer onboarding and repository professionalism.",
                        10));
            }
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
                    Collections.emptyList(),
                    "No files found in the repository.",
                    "File count is 0.",
                    "Establishes a base repository layout.",
                    50));
        } else if ("README_ONLY".equals(repositoryType)) {
            recommendations.add(new Recommendation(
                    "HIGH",
                    "PROJECT_STRUCTURE",
                    "Add meaningful implementation",
                    "This repository only contains a minimal README. Add actual source code, documentation, or project files to make it a complete repository.",
                    Collections.emptyList(),
                    "Only README found.",
                    "Source file count is 0.",
                    "Provides actual implementation value.",
                    50));
        } else if (structure != null && structure.getDetails() != null) {
            int rootClutterCount = getInteger(structure.getDetails(), "rootClutterCount");
            if (rootClutterCount > 0) {
                List<String> rootClutter = getFormattedStringList(structure.getDetails(), "rootClutter");
                recommendations.add(new Recommendation(
                        rootClutterCount >= 10 ? "HIGH" : "MEDIUM",
                        "PROJECT_STRUCTURE",
                        "Reduce root directory clutter",
                        "Move database utilities, migration scripts and non-essential artifacts into dedicated directories.",
                        rootClutter,
                        "Root directory is cluttered with " + rootClutterCount + " unstructured files.",
                        String.join(", ", rootClutter.subList(0, Math.min(rootClutter.size(), 3))) + (rootClutter.size() > 3 ? "..." : ""),
                        "Makes repository navigation significantly easier.",
                        10));
            }

            int duplicateCount = getInteger(structure.getDetails(), "duplicateLookingFileCount");
            if (duplicateCount > 0) {
                List<String> duplicates = formatAffectedFiles(flattenDuplicateFiles(structure.getDetails().get("duplicateLookingFiles")));
                recommendations.add(new Recommendation(
                        "MEDIUM",
                        "PROJECT_STRUCTURE",
                        "Review duplicate-looking files",
                        "Review files with identical names and remove or rename genuinely duplicated implementations.",
                        duplicates,
                        "Found " + duplicateCount + " potentially duplicated files.",
                        "Duplicates found for: " + String.join(", ", duplicates.subList(0, Math.min(duplicates.size(), 3))),
                        "Reduces confusion and maintenance overhead.",
                        5));
            }

            int suspiciousFileCount = getInteger(structure.getDetails(), "suspiciousNamedFileCount");
            if (suspiciousFileCount > 0) {
                List<String> suspiciousFiles = getFormattedStringList(structure.getDetails(), "suspiciousNamedFiles");
                recommendations.add(new Recommendation(
                        "LOW",
                        "PROJECT_STRUCTURE",
                        "Review suspicious versioned filenames",
                        "Avoid filenames such as v2, final, copy or backup when version control already tracks file history.",
                        suspiciousFiles,
                        "Found " + suspiciousFileCount + " files with versioning suffixes.",
                        String.join(", ", suspiciousFiles.subList(0, Math.min(suspiciousFiles.size(), 3))),
                        "Adheres to Git best practices.",
                        2));
            }
        }

        /*
         * COMMIT QUALITY
         */
        if (commitQuality != null && commitQuality.getDetails() != null) {
            int genericCommitCount = getInteger(commitQuality.getDetails(), "genericCommitCount");
            int repeatedMessageCount = getInteger(commitQuality.getDetails(), "repeatedMessageCount");

            if (genericCommitCount > 0) {
                List<String> genericCommits = getFormattedStringList(commitQuality.getDetails(), "genericCommitMessages");
                recommendations.add(new Recommendation(
                        "LOW",
                        "COMMIT_QUALITY",
                        "Use descriptive commit messages",
                        "Replace generic commit messages with clear action-oriented descriptions of the actual change.",
                        genericCommits,
                        "Found " + genericCommitCount + " generic/vague commits.",
                        "Examples: " + String.join(", ", genericCommits.subList(0, Math.min(genericCommits.size(), 3))),
                        "Improves project history traceability.",
                        5));
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
                        formatAffectedFiles(repeatedCommitIssues),
                        "Found " + repeatedMessageCount + " instances of repeated commit messages.",
                        "Repeated instances found in commit history.",
                        "Provides accurate context for code changes.",
                        5));
            }
        }

        /*
         * DOCUMENTATION QUALITY
         */
        if ("EMPTY".equals(repositoryType) || "README_ONLY".equals(repositoryType)) {
            // Documentation recommendations are handled by the structural recommendations
        } else if (documentation != null && documentation.getDetails() != null) {
            boolean needsDocImprovement = Boolean.TRUE.equals(documentation.getDetails().get("needsDocumentationImprovement"));
            
            if (needsDocImprovement) {
                List<String> undocumentedFiles = getFormattedStringList(documentation.getDetails(), "largeUndocumentedFiles");
                double documentationCoverage = getDouble(documentation.getDetails(), "documentationCoverage");
                
                recommendations.add(new Recommendation(
                        documentationCoverage < 10.0 ? "HIGH" : "MEDIUM",
                        "DOCUMENTATION",
                        "Improve source code documentation",
                        "Add meaningful docstrings or documentation comments to important classes and functions.",
                        undocumentedFiles,
                        "Documentation coverage is low (" + roundTwoDecimals(documentationCoverage) + "%).",
                        "Undocumented large files: " + (undocumentedFiles.isEmpty() ? "None specifically identified" : String.join(", ", undocumentedFiles.subList(0, Math.min(undocumentedFiles.size(), 3)))),
                        "Makes codebase significantly easier to understand and maintain.",
                        15));
            }
        }

        /*
         * CODE QUALITY
         */
        if (codeQuality != null && codeQuality.getDetails() != null) {
            int sourceFilesAnalyzed = getInteger(codeQuality.getDetails(), "sourceFilesAnalyzed");
            int implementationHeavyFileCount = getInteger(codeQuality.getDetails(), "implementationHeavyFileCount");
            int totalImplementationLines = getInteger(codeQuality.getDetails(), "totalImplementationLines");
            int generatedLikeFileCount = getInteger(codeQuality.getDetails(), "generatedLikeFileCount");

            if (sourceFilesAnalyzed > 0
                    && (implementationHeavyFileCount < 4 || totalImplementationLines < 250 || generatedLikeFileCount > 0)) {

                boolean isSoftware = !List.of("JUPYTER_NOTEBOOK", "DATA_SCIENCE", "MACHINE_LEARNING", "PORTFOLIO", "HTML_CSS", "DATASET_REPOSITORY", "POWER_BI", "README_ONLY", "EMPTY").contains(repositoryType);
                if (isSoftware) {
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
                            implementationEvidence,
                            "Very low ratio of core implementation code.",
                            "Total implementation lines: " + totalImplementationLines,
                            "Transforms the project from a toy/template into a real application.",
                            20));
                }
            }

            int possibleSecretCount = getInteger(codeQuality.getDetails(), "possibleSecretCount");
            if (possibleSecretCount > 0) {
                List<String> secretFiles = getFormattedStringList(codeQuality.getDetails(), "possibleSecretFiles");
                recommendations.add(new Recommendation(
                        "CRITICAL",
                        "SECURITY",
                        "Remove possible hardcoded secrets",
                        "Move credentials and secrets to environment variables and rotate any exposed credentials immediately.",
                        secretFiles,
                        "Detected " + possibleSecretCount + " possible hardcoded secrets/keys.",
                        "Found in: " + String.join(", ", secretFiles.subList(0, Math.min(secretFiles.size(), 3))),
                        "Prevents severe security breaches.",
                        25));
            }

            int largeSourceFileCount = getInteger(codeQuality.getDetails(), "largeSourceFileCount");
            if (largeSourceFileCount > 0) {
                List<String> largeFiles = getFormattedStringList(codeQuality.getDetails(), "largeSourceFiles");
                recommendations.add(new Recommendation(
                        "HIGH",
                        "CODE_QUALITY",
                        "Split large source files",
                        "Break large source files into smaller modules or services with focused responsibilities.",
                        largeFiles,
                        "Detected " + largeSourceFileCount + " files over 500 lines.",
                        "Files: " + String.join(", ", largeFiles.subList(0, Math.min(largeFiles.size(), 3))),
                        "Improves readability and enforces Single Responsibility Principle.",
                        10));
            }

            int debugStatementCount = getInteger(codeQuality.getDetails(), "debugStatementCount");
            if (debugStatementCount > 0) {
                List<String> debugFiles = getFormattedStringList(codeQuality.getDetails(), "debugStatementFiles");
                recommendations.add(new Recommendation(
                        debugStatementCount >= 20 ? "HIGH" : "MEDIUM",
                        "CODE_QUALITY",
                        "Remove production debug statements",
                        "Replace temporary print or console debug statements with structured application logging where necessary.",
                        debugFiles,
                        "Detected leftover print/console.log statements in " + debugStatementCount + " files.",
                        "Files: " + String.join(", ", debugFiles.subList(0, Math.min(debugFiles.size(), 3))),
                        "Cleans up production logs and standardizes observability.",
                        5));
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