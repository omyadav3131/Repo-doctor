package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;
import com.omyadav.repodoctor.dto.OverallResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OverallRepositoryScoreService {

    private static final Map<String, Double> BASE_WEIGHTS = Map.of(
            "Hygiene", 0.15,
            "README", 0.15,
            "Project Structure", 0.15,
            "Commit Quality", 0.15,
            "Documentation", 0.15,
            "Code Quality", 0.25);

    public OverallResult calculateOverallScore(
            DimensionResult hygiene,
            DimensionResult readme,
            DimensionResult structure,
            DimensionResult commitQuality,
            DimensionResult documentation,
            DimensionResult codeQuality,
            String repositoryType) {

        Map<String, DimensionResult> dimensions = new LinkedHashMap<>();
        dimensions.put("Hygiene", hygiene);
        dimensions.put("README", readme);
        dimensions.put("Project Structure", structure);
        dimensions.put("Commit Quality", commitQuality);
        dimensions.put("Documentation", documentation);
        dimensions.put("Code Quality", codeQuality);

        List<String> excludedDimensions = new ArrayList<>();
        List<String> unsupportedMetrics = new ArrayList<>();
        List<String> unknownMetrics = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        List<String> reasoning = new ArrayList<>();

        double validWeightSum = 0.0;
        int validDimensionCount = 0;

        // Step 1: Determine which dimensions are analyzable and their total base weight
        for (Map.Entry<String, DimensionResult> entry : dimensions.entrySet()) {
            String name = entry.getKey();
            DimensionResult result = entry.getValue();

            if (result == null) {
                excludedDimensions.add(name);
                continue;
            }

            if (result.getStatus() == AnalysisStatus.SUCCESS || result.getStatus() == AnalysisStatus.PARTIAL) {
                if (result.getScore() != null) {
                    validWeightSum += BASE_WEIGHTS.get(name);
                    validDimensionCount++;
                } else {
                    excludedDimensions.add(name);
                }
            } else {
                excludedDimensions.add(name);
                if (result.getStatusReason() != null) {
                    warnings.add(name + " excluded: " + result.getStatusReason());
                }
                
                if (result.getStatus() == AnalysisStatus.NOT_ANALYZABLE || 
                    result.getStatus() == AnalysisStatus.UNSUPPORTED ||
                    result.getStatus() == AnalysisStatus.RATE_LIMITED ||
                    result.getStatus() == AnalysisStatus.FETCH_FAILED) {
                    unsupportedMetrics.add(name);
                } else {
                    unknownMetrics.add(name);
                }
            }
        }

        if (validWeightSum <= 0 || validDimensionCount == 0) {
            return new OverallResult(
                    AnalysisStatus.NOT_ANALYZABLE,
                    0,
                    0.0,
                    "F",
                    "CRITICAL",
                    0,
                    excludedDimensions,
                    warnings,
                    evidence,
                    reasoning,
                    unsupportedMetrics,
                    unknownMetrics,
                    true,
                    0,
                    "EMPTY",
                    Map.of(),
                    repositoryType,
                    0,
                    Map.of());
        }

        // Step 2: Calculate the weighted score and dynamic weight redistribution
        double finalScoreRaw = 0.0;
        Map<String, DimensionResult> weightedDimensionsMap = new LinkedHashMap<>();

        for (Map.Entry<String, DimensionResult> entry : dimensions.entrySet()) {
            String name = entry.getKey();
            DimensionResult result = entry.getValue();

            if (result != null && (result.getStatus() == AnalysisStatus.SUCCESS || result.getStatus() == AnalysisStatus.PARTIAL) && result.getScore() != null) {
                // Redistribute weight
                double baseWeight = BASE_WEIGHTS.get(name);
                double redistributedWeight = baseWeight / validWeightSum;
                
                double contribution = result.getScore() * redistributedWeight;
                finalScoreRaw += contribution;

                weightedDimensionsMap.put(name, result.toBuilder()
                        .weight(redistributedWeight)
                        .contribution(contribution)
                        .build());
                        
                evidence.add(name + " contributed " + round(contribution) + " to the final score (Weight: " + round(redistributedWeight*100) + "%).");
            } else {
                // Do not put null values in map to avoid NullPointerException in Map.copyOf
                // weightedDimensionsMap.put(name, null);
            }
        }

        int finalScore = (int) Math.round(Math.max(0, Math.min(100, finalScoreRaw)));
        
        // Apply Repository Scale Penalties
        boolean isSoftware = repositoryType != null && !repositoryType.equals("DOCUMENTATION_REPOSITORY") && !repositoryType.equals("README_ONLY") && !repositoryType.equals("EMPTY") && !repositoryType.equals("UNKNOWN");
        
        if (isSoftware) {
            DimensionResult cq = dimensions.get("Code Quality");
            if (cq == null || cq.getStatus() == AnalysisStatus.NOT_ANALYZABLE) {
                finalScore = Math.min(finalScore, 10);
                warnings.add("Critical Penalty: Classified as software but no analyzable code found. Score capped at 10.");
                evidence.add("Score capped at 10 due to missing source code.");
            } else if (cq.getScore() != null && cq.getDetails() != null) {
                Object linesObj = cq.getDetails().get("totalImplementationLines");
                if (linesObj instanceof Number) {
                    int lines = ((Number) linesObj).intValue();
                    if (lines == 0) {
                        finalScore = Math.min(finalScore, 10);
                        warnings.add("Critical Penalty: No implementation lines found. Score capped at 10.");
                        evidence.add("Score capped at 10 due to 0 implementation lines.");
                    } else if (lines < 50) {
                        finalScore = Math.min(finalScore, 15);
                        warnings.add("Penalty: Very small scale (" + lines + " lines). Score capped at 15.");
                        evidence.add("Score capped at 15 due to very small code size.");
                    } else if (lines < 250) {
                        finalScore = Math.min(finalScore, 25);
                        warnings.add("Penalty: Tiny project scale (" + lines + " lines). Score capped at 25.");
                        evidence.add("Score capped at 25 due to tiny project scale.");
                    } else if (lines < 500) {
                        finalScore = Math.min(finalScore, 45);
                        warnings.add("Penalty: Small student/toy project scale (" + lines + " lines). Score capped at 45.");
                        evidence.add("Score capped at 45 due to small project scale.");
                    } else if (lines < 2000) {
                        finalScore = Math.min(finalScore, 70);
                        warnings.add("Penalty: Medium scale project (" + lines + " lines). Score capped at 70.");
                        evidence.add("Score capped at 70 due to medium project scale.");
                    }
                }
            }
        } else if ("EMPTY".equals(repositoryType)) {
            finalScore = Math.min(finalScore, 10);
            warnings.add("Repository is empty. Score capped at 10.");
            evidence.add("Score capped at 10 because repository is empty.");
        } else if ("README_ONLY".equals(repositoryType)) {
            finalScore = Math.min(finalScore, 15);
            warnings.add("Repository is README-only. Score capped at 15.");
            evidence.add("Score capped at 15 because repository is README-only.");
        }
        
        String health = mapRepositoryHealth(finalScore);
        String grade = calculateGrade(finalScore);
        
        AnalysisStatus finalStatus = excludedDimensions.isEmpty() ? AnalysisStatus.SUCCESS : AnalysisStatus.PARTIAL;

        return new OverallResult(
                finalStatus,
                finalScore,
                1.0, // Confidence simplified to 1.0
                grade,
                health,
                validDimensionCount,
                excludedDimensions,
                warnings,
                evidence,
                reasoning,
                unsupportedMetrics,
                unknownMetrics,
                finalStatus == AnalysisStatus.PARTIAL,
                finalScore, // For backward compatibility
                "STANDARD", // Legacy classification removed
                weightedDimensionsMap,
                repositoryType,
                finalScore,
                Map.of()
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String mapRepositoryHealth(Integer score) {
        int safeScore = score == null ? 0 : score;
        if (safeScore >= 90) return "EXCELLENT";
        if (safeScore >= 75) return "GOOD";
        if (safeScore >= 50) return "NEEDS_IMPROVEMENT";
        if (safeScore >= 25) return "POOR";
        return "CRITICAL";
    }

    private String calculateGrade(int score) {
        if (score >= 90) return "A+";
        if (score >= 80) return "A";
        if (score >= 70) return "B";
        if (score >= 60) return "C";
        if (score >= 50) return "D";
        return "F";
    }
}
