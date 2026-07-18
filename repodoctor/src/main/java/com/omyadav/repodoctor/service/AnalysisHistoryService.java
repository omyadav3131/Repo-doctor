package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.entity.AnalysisResult;
import com.omyadav.repodoctor.repository.AnalysisResultRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalysisHistoryService {

    private final AnalysisResultRepository analysisResultRepository;

    public AnalysisHistoryService(
            AnalysisResultRepository analysisResultRepository) {

        this.analysisResultRepository = analysisResultRepository;
    }

    public List<AnalysisResult> getRepositoryHistory(
            String owner,
            String repositoryName) {

        return analysisResultRepository
                .findByOwnerAndRepositoryNameOrderByAnalysisDateDesc(
                        owner,
                        repositoryName
                );
    }
}