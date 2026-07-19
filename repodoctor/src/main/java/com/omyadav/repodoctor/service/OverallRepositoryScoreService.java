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
        double totalConfidenceWeight = 0.0;
        double totalConfidenceScore = 0.0;

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
                    
                    double dimConfidence = result.getConfidence() != null ? result.getConfidence() : 1.0;
                    totalConfidenceScore += dimConfidence * BASE_WEIGHTS.get(name);
                    totalConfidenceWeight += BASE_WEIGHTS.get(name);
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
            
            // Extract dimension reasons if they exist
            if (result.getDetails() != null && result.getDetails().containsKey("reasons")) {
                Object reasonsObj = result.getDetails().get("reasons");
                if (reasonsObj instanceof List<?>) {
                    for (Object reasonObj : (List<?>) reasonsObj) {
                        reasoning.add("[" + name + "] " + reasonObj.toString());
                    }
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

        double overallConfidence = totalConfidenceWeight > 0 ? totalConfidenceScore / totalConfidenceWeight : 0.0;
        if (overallConfidence < 0) overallConfidence = 0.0;
        if (overallConfidence > 1.0) overallConfidence = 1.0;

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
            }
        }

        int finalScore = (int) Math.round(Math.max(0, Math.min(100, finalScoreRaw)));
        
        // No arbitrary scale caps. The final score is purely based on measurable dimension evidence.
        String health = mapRepositoryHealth(finalScore);
        String grade = calculateGrade(finalScore);
        
        AnalysisStatus finalStatus = excludedDimensions.isEmpty() ? AnalysisStatus.SUCCESS : AnalysisStatus.PARTIAL;

        return new OverallResult(
                finalStatus,
                finalScore,
                round(overallConfidence),
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
