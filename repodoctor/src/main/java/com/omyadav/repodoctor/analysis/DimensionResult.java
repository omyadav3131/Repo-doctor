package com.omyadav.repodoctor.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DimensionResult {

    private final AnalysisStatus status;
    private final String statusReason;
    private final Integer score;
    private final Double confidence;
    private final String confidenceReason;
    private final Double weight;
    private final Double contribution;
    private final int totalCandidateItemCount;
    private final int analyzedItemCount;
    private final int failedItemCount;
    private final List<String> issues;
    private final List<String> warnings;
    private final List<String> evidence;
    private final Map<String, Object> details;

    private DimensionResult(Builder builder) {
        this.status = builder.status;
        this.statusReason = builder.statusReason;
        this.score = builder.score;
        this.confidence = builder.confidence;
        this.confidenceReason = builder.confidenceReason;
        this.weight = builder.weight;
        this.contribution = builder.contribution;
        this.totalCandidateItemCount = builder.totalCandidateItemCount;
        this.analyzedItemCount = builder.analyzedItemCount;
        this.failedItemCount = builder.failedItemCount;
        this.issues = List.copyOf(builder.issues);
        this.warnings = List.copyOf(builder.warnings);
        this.evidence = List.copyOf(builder.evidence);
        this.details = Map.copyOf(builder.details);
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Integer getScore() {
        return score;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getConfidenceReason() {
        return confidenceReason;
    }

    public Double getWeight() {
        return weight;
    }

    public Double getContribution() {
        return contribution;
    }

    public int getTotalCandidateItemCount() {
        return totalCandidateItemCount;
    }

    public int getAnalyzedItemCount() {
        return analyzedItemCount;
    }

    public int getFailedItemCount() {
        return failedItemCount;
    }

    public List<String> getIssues() {
        return issues;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static DimensionResult notAnalyzable(String reason) {
        return new Builder(AnalysisStatus.NOT_ANALYZABLE)
                .statusReason(reason)
                .build();
    }

    public static DimensionResult fetchFailed(String reason) {
        return new Builder(AnalysisStatus.FETCH_FAILED)
                .statusReason(reason)
                .build();
    }

    public static DimensionResult rateLimited() {
        return new Builder(AnalysisStatus.RATE_LIMITED)
                .statusReason("GitHub API rate limit exceeded")
                .build();
    }

    public static Builder builder(AnalysisStatus status) {
        return new Builder(status);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this.status)
                .statusReason(this.statusReason)
                .score(this.score)
                .confidence(this.confidence)
                .confidenceReason(this.confidenceReason)
                .weight(this.weight)
                .contribution(this.contribution)
                .totalCandidateItemCount(this.totalCandidateItemCount)
                .analyzedItemCount(this.analyzedItemCount)
                .failedItemCount(this.failedItemCount)
                .issues(this.issues)
                .warnings(this.warnings)
                .details(this.details);
        
        for (String evidenceStr : this.evidence) {
            builder.evidence(evidenceStr);
        }
        return builder;
    }

    public static class Builder {
        private final AnalysisStatus status;
        private String statusReason;
        private Integer score;
        private Double confidence;
        private String confidenceReason;
        private Double weight;
        private Double contribution;
        private int totalCandidateItemCount;
        private int analyzedItemCount;
        private int failedItemCount;
        private final List<String> issues = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> evidence = new ArrayList<>();
        private final Map<String, Object> details = new LinkedHashMap<>();

        public Builder(AnalysisStatus status) {
            this.status = status;

            // DEFAULT ONLY: Every analyzer MUST explicitly call both .confidence(...) 
            // and .confidenceReason(...) together. Otherwise it will silently regress 
            // to this "always 100%, no real reason" default.
            if (status == AnalysisStatus.SUCCESS) {
                this.confidence = 1.0;
            }
        }

        public Builder statusReason(String statusReason) {
            this.statusReason = statusReason;
            return this;
        }

        public Builder score(Integer score) {
            this.score = score;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder confidenceReason(String confidenceReason) {
            this.confidenceReason = confidenceReason;
            return this;
        }

        public Builder weight(Double weight) {
            this.weight = weight;
            return this;
        }

        public Builder contribution(Double contribution) {
            this.contribution = contribution;
            return this;
        }

        public Builder totalCandidateItemCount(int totalCandidateItemCount) {
            this.totalCandidateItemCount = totalCandidateItemCount;
            return this;
        }

        public Builder analyzedItemCount(int analyzedItemCount) {
            this.analyzedItemCount = analyzedItemCount;
            return this;
        }

        public Builder failedItemCount(int failedItemCount) {
            this.failedItemCount = failedItemCount;
            return this;
        }

        public Builder issue(String issue) {
            this.issues.add(issue);
            return this;
        }

        public Builder issues(List<String> issues) {
            if (issues != null) {
                this.issues.addAll(issues);
            }
            return this;
        }

        public Builder warning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder warnings(List<String> warnings) {
            if (warnings != null) {
                this.warnings.addAll(warnings);
            }
            return this;
        }

        public Builder evidence(String evidence) {
            this.evidence.add(evidence);
            return this;
        }

        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        public Builder details(Map<String, Object> details) {
            if (details != null) {
                this.details.putAll(details);
            }
            return this;
        }

        public DimensionResult build() {
            return new DimensionResult(this);
        }
    }
}
