package com.omyadav.repodoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Recommendation {
    private final String priority;
    private final String category;
    private final String title;
    private final String description;
    private final List<String> affectedItems;
    
    // New Evidence-based fields
    private final String reason;
    private final String evidence;
    private final String expectedBenefit;
    private final Integer estimatedScoreImprovement;

    // Legacy constructor for backward compatibility (if needed by other systems)
    public Recommendation(String priority, String category, String title, String description,
            List<String> affectedItems) {
        this.priority = priority;
        this.category = category;
        this.title = title;
        this.description = description;
        this.affectedItems = affectedItems == null ? List.of() : List.copyOf(affectedItems);
        this.reason = null;
        this.evidence = null;
        this.expectedBenefit = null;
        this.estimatedScoreImprovement = null;
    }

    // New detailed constructor
    public Recommendation(String priority, String category, String title, String description,
            List<String> affectedItems, String reason, String evidence, 
            String expectedBenefit, Integer estimatedScoreImprovement) {
        this.priority = priority;
        this.category = category;
        this.title = title;
        this.description = description;
        this.affectedItems = affectedItems == null ? List.of() : List.copyOf(affectedItems);
        this.reason = reason;
        this.evidence = evidence;
        this.expectedBenefit = expectedBenefit;
        this.estimatedScoreImprovement = estimatedScoreImprovement;
    }

    public String getPriority() {
        return priority;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAffectedItems() {
        return affectedItems;
    }

    public String getReason() {
        return reason;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getExpectedBenefit() {
        return expectedBenefit;
    }

    public Integer getEstimatedScoreImprovement() {
        return estimatedScoreImprovement;
    }
}
