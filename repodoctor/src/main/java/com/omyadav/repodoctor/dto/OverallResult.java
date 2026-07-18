package com.omyadav.repodoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omyadav.repodoctor.analysis.AnalysisStatus;

import java.util.List;
import java.util.Map;
import com.omyadav.repodoctor.analysis.DimensionResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class OverallResult {
    private final AnalysisStatus status;
    private final Integer score;
    private final Double confidence;
    private final String grade;
    private final String repositoryHealth;
    private final int validDimensionCount;
    private final List<String> excludedDimensions;
    private final List<String> warnings;
    private final List<String> evidence;
    private final List<String> reasoning;
    private final List<String> unsupportedMetrics;
    private final List<String> unknownMetrics;
    private final boolean partialAnalysis;
    private final Integer implementationScore;
    private final String implementationClassification;
    private final Map<String, DimensionResult> weightedDimensions;
    
    private final String repositoryType;
    private final Integer baseScore;
    private final Map<String, String> adjustments;

    public OverallResult(AnalysisStatus status, Integer score, Double confidence, String grade, String repositoryHealth,
            int validDimensionCount, List<String> excludedDimensions, List<String> warnings, List<String> evidence,
            List<String> reasoning, List<String> unsupportedMetrics, List<String> unknownMetrics,
            boolean partialAnalysis, Integer implementationScore, String implementationClassification,
            Map<String, DimensionResult> weightedDimensions, String repositoryType, Integer baseScore,
            Map<String, String> adjustments) {
        this.status = status;
        this.score = score;
        this.confidence = confidence;
        this.grade = grade;
        this.repositoryHealth = repositoryHealth;
        this.validDimensionCount = validDimensionCount;
        this.excludedDimensions = excludedDimensions == null ? List.of() : List.copyOf(excludedDimensions);
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        this.reasoning = reasoning == null ? List.of() : List.copyOf(reasoning);
        this.unsupportedMetrics = unsupportedMetrics == null ? List.of() : List.copyOf(unsupportedMetrics);
        this.unknownMetrics = unknownMetrics == null ? List.of() : List.copyOf(unknownMetrics);
        this.partialAnalysis = partialAnalysis;
        this.implementationScore = implementationScore;
        this.implementationClassification = implementationClassification;
        this.weightedDimensions = weightedDimensions == null ? Map.of() : Map.copyOf(weightedDimensions);
        this.repositoryType = repositoryType;
        this.baseScore = baseScore;
        this.adjustments = adjustments == null ? Map.of() : Map.copyOf(adjustments);
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public Integer getScore() {
        return score;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getGrade() {
        return grade;
    }

    public String getRepositoryHealth() {
        return repositoryHealth;
    }

    public int getValidDimensionCount() {
        return validDimensionCount;
    }

    public List<String> getExcludedDimensions() {
        return excludedDimensions;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    public List<String> getReasoning() {
        return reasoning;
    }

    public List<String> getUnsupportedMetrics() {
        return unsupportedMetrics;
    }

    public List<String> getUnknownMetrics() {
        return unknownMetrics;
    }

    public boolean isPartialAnalysis() {
        return partialAnalysis;
    }

    public Integer getImplementationScore() {
        return implementationScore;
    }

    public String getImplementationClassification() {
        return implementationClassification;
    }

    public Map<String, DimensionResult> getWeightedDimensions() {
        return weightedDimensions;
    }

    public String getRepositoryType() {
        return repositoryType;
    }

    public Integer getBaseScore() {
        return baseScore;
    }

    public Map<String, String> getAdjustments() {
        return adjustments;
    }
}
