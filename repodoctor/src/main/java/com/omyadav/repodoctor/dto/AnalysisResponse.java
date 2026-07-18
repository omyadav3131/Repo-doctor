package com.omyadav.repodoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omyadav.repodoctor.analysis.DimensionResult;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AnalysisResponse {
    private final Long analysisId;
    private final RepositoryMetadata repository;
    private final AnalysisScope scope;
    private final LocalDateTime analyzedAt;

    private final DimensionResult hygiene;
    private final DimensionResult readme;
    private final DimensionResult structure;
    private final DimensionResult commitQuality;
    private final DimensionResult documentation;
    private final DimensionResult codeQuality;

    private final OverallResult overall;
    private final List<Recommendation> recommendations;

    private final String status;
    private final List<String> warnings;

    private AnalysisResponse(Builder builder) {
        this.analysisId = builder.analysisId;
        this.repository = builder.repository;
        this.scope = builder.scope;
        this.analyzedAt = builder.analyzedAt;
        this.hygiene = builder.hygiene;
        this.readme = builder.readme;
        this.structure = builder.structure;
        this.commitQuality = builder.commitQuality;
        this.documentation = builder.documentation;
        this.codeQuality = builder.codeQuality;
        this.overall = builder.overall;
        this.recommendations = builder.recommendations == null ? List.of() : List.copyOf(builder.recommendations);
        this.status = builder.status;
        this.warnings = builder.warnings == null ? List.of() : List.copyOf(builder.warnings);
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public RepositoryMetadata getRepository() {
        return repository;
    }

    public AnalysisScope getScope() {
        return scope;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public DimensionResult getHygiene() {
        return hygiene;
    }

    public DimensionResult getReadme() {
        return readme;
    }

    public DimensionResult getStructure() {
        return structure;
    }

    public DimensionResult getCommitQuality() {
        return commitQuality;
    }

    public DimensionResult getDocumentation() {
        return documentation;
    }

    public DimensionResult getCodeQuality() {
        return codeQuality;
    }

    public OverallResult getOverall() {
        return overall;
    }

    public List<Recommendation> getRecommendations() {
        return recommendations;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long analysisId;
        private RepositoryMetadata repository;
        private AnalysisScope scope;
        private LocalDateTime analyzedAt;
        private DimensionResult hygiene;
        private DimensionResult readme;
        private DimensionResult structure;
        private DimensionResult commitQuality;
        private DimensionResult documentation;
        private DimensionResult codeQuality;
        private OverallResult overall;
        private List<Recommendation> recommendations;
        private String status;
        private List<String> warnings;

        public Builder analysisId(Long analysisId) {
            this.analysisId = analysisId;
            return this;
        }

        public Builder repository(RepositoryMetadata repository) {
            this.repository = repository;
            return this;
        }

        public Builder scope(AnalysisScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder analyzedAt(LocalDateTime analyzedAt) {
            this.analyzedAt = analyzedAt;
            return this;
        }

        public Builder hygiene(DimensionResult hygiene) {
            this.hygiene = hygiene;
            return this;
        }

        public Builder readme(DimensionResult readme) {
            this.readme = readme;
            return this;
        }

        public Builder structure(DimensionResult structure) {
            this.structure = structure;
            return this;
        }

        public Builder commitQuality(DimensionResult commitQuality) {
            this.commitQuality = commitQuality;
            return this;
        }

        public Builder documentation(DimensionResult documentation) {
            this.documentation = documentation;
            return this;
        }

        public Builder codeQuality(DimensionResult codeQuality) {
            this.codeQuality = codeQuality;
            return this;
        }

        public Builder overall(OverallResult overall) {
            this.overall = overall;
            return this;
        }

        public Builder recommendations(List<Recommendation> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public AnalysisResponse build() {
            return new AnalysisResponse(this);
        }
    }
}
