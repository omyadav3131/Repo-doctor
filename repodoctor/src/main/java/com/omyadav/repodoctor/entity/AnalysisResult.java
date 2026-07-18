package com.omyadav.repodoctor.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "analysis_results")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    @Column(name = "repository_url")
    private String repositoryUrl;

    private Integer hygieneScore;
    private Integer readmeScore;
    private Integer structureScore;
    private Integer commitQualityScore;
    private Integer documentationScore;
    private Integer codeQualityScore;

    private Integer overallScore;
    private String grade;
    private String repositoryHealth;
    private Integer recommendationCount;

    @Column(name = "analysis_date", nullable = false)
    private LocalDateTime analysisDate;

    @Column(name = "overall_confidence")
    private Double overallConfidence;

    @Column(name = "valid_dimension_count")
    private Integer validDimensionCount;

    @JsonIgnore
    @Convert(converter = com.omyadav.repodoctor.config.MapToJsonConverter.class)
    @Column(name = "analysis_snapshot", columnDefinition = "TEXT")
    private Map<String, Object> analysisSnapshot;

    public AnalysisResult() {
    }

    @PrePersist
    public void prePersist() {
        analysisDate = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public Integer getHygieneScore() {
        return hygieneScore;
    }

    public void setHygieneScore(Integer hygieneScore) {
        this.hygieneScore = hygieneScore;
    }

    public Integer getReadmeScore() {
        return readmeScore;
    }

    public void setReadmeScore(Integer readmeScore) {
        this.readmeScore = readmeScore;
    }

    public Integer getStructureScore() {
        return structureScore;
    }

    public void setStructureScore(Integer structureScore) {
        this.structureScore = structureScore;
    }

    public Integer getCommitQualityScore() {
        return commitQualityScore;
    }

    public void setCommitQualityScore(Integer commitQualityScore) {
        this.commitQualityScore = commitQualityScore;
    }

    public Integer getDocumentationScore() {
        return documentationScore;
    }

    public void setDocumentationScore(Integer documentationScore) {
        this.documentationScore = documentationScore;
    }

    public Integer getCodeQualityScore() {
        return codeQualityScore;
    }

    public void setCodeQualityScore(Integer codeQualityScore) {
        this.codeQualityScore = codeQualityScore;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getRepositoryHealth() {
        return repositoryHealth;
    }

    public void setRepositoryHealth(String repositoryHealth) {
        this.repositoryHealth = repositoryHealth;
    }

    public Integer getRecommendationCount() {
        return recommendationCount;
    }

    public void setRecommendationCount(Integer recommendationCount) {
        this.recommendationCount = recommendationCount;
    }

    public LocalDateTime getAnalysisDate() {
        return analysisDate;
    }

    public void setAnalysisDate(LocalDateTime analysisDate) {
        this.analysisDate = analysisDate;
    }

    public Double getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(Double overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public Integer getValidDimensionCount() {
        return validDimensionCount;
    }

    public void setValidDimensionCount(Integer validDimensionCount) {
        this.validDimensionCount = validDimensionCount;
    }

    public Map<String, Object> getAnalysisSnapshot() {
        return analysisSnapshot;
    }

    public void setAnalysisSnapshot(Map<String, Object> analysisSnapshot) {
        this.analysisSnapshot = analysisSnapshot;
    }
}