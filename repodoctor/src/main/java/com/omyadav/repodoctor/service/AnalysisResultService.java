package com.omyadav.repodoctor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omyadav.repodoctor.dto.AnalysisResponse;
import com.omyadav.repodoctor.entity.AnalysisResult;
import com.omyadav.repodoctor.repository.AnalysisResultRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalysisResultService {

    private final AnalysisResultRepository analysisResultRepository;
    private final ObjectMapper objectMapper;

    public AnalysisResultService(
            AnalysisResultRepository analysisResultRepository,
            ObjectMapper objectMapper) {

        this.analysisResultRepository = analysisResultRepository;
        this.objectMapper = objectMapper;
    }

    public AnalysisResult saveAnalysis(AnalysisResponse response, String repositoryUrl) {

        AnalysisResult analysisResult = new AnalysisResult();

        analysisResult.setOwner(response.getRepository().getOwner());
        analysisResult.setRepositoryName(response.getRepository().getRepository());
        analysisResult.setRepositoryUrl(repositoryUrl);

        analysisResult.setHygieneScore(response.getHygiene() != null ? response.getHygiene().getScore() : null);
        analysisResult.setReadmeScore(response.getReadme() != null ? response.getReadme().getScore() : null);
        analysisResult.setStructureScore(response.getStructure() != null ? response.getStructure().getScore() : null);
        analysisResult.setCommitQualityScore(
                response.getCommitQuality() != null ? response.getCommitQuality().getScore() : null);
        analysisResult.setDocumentationScore(
                response.getDocumentation() != null ? response.getDocumentation().getScore() : null);
        analysisResult.setCodeQualityScore(
                response.getCodeQuality() != null ? response.getCodeQuality().getScore() : null);

        if (response.getOverall() != null) {
            analysisResult.setOverallScore(response.getOverall().getScore());
            analysisResult.setGrade(response.getOverall().getGrade());
            analysisResult.setRepositoryHealth(response.getOverall().getRepositoryHealth());
            analysisResult.setOverallConfidence(response.getOverall().getConfidence());
            analysisResult.setValidDimensionCount(response.getOverall().getValidDimensionCount());
        }

        analysisResult.setRecommendationCount(
                response.getRecommendations() != null ? response.getRecommendations().size() : 0);

        // Save complete analysis snapshot for PDF generation
        Map<String, Object> snapshot = objectMapper.convertValue(response, new TypeReference<>() {});
        analysisResult.setAnalysisSnapshot(new LinkedHashMap<>(snapshot));

        return analysisResultRepository.save(analysisResult);
    }

    public List<AnalysisResult> getAllAnalyses() {
        return analysisResultRepository.findAllByOrderByAnalysisDateDesc();
    }

    public Optional<AnalysisResult> getAnalysisById(Long id) {
        return analysisResultRepository.findById(id);
    }

    public boolean deleteAnalysis(Long id) {
        if (!analysisResultRepository.existsById(id)) {
            return false;
        }
        analysisResultRepository.deleteById(id);
        return true;
    }

    public long getTotalAnalysisCount() {
        return analysisResultRepository.count();
    }

    public Double getAverageScore() {
        return analysisResultRepository.findAverageOverallScore();
    }

    public Integer getHighestScore() {
        return analysisResultRepository.findHighestOverallScore();
    }
}