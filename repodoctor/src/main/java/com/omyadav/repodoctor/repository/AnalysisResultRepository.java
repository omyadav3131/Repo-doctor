package com.omyadav.repodoctor.repository;

import com.omyadav.repodoctor.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AnalysisResultRepository
        extends JpaRepository<AnalysisResult, Long> {

    List<AnalysisResult> findByOwnerAndRepositoryNameOrderByAnalysisDateDesc(
            String owner, String repositoryName);

    List<AnalysisResult> findAllByOrderByAnalysisDateDesc();

    @Query("SELECT AVG(a.overallScore) FROM AnalysisResult a WHERE a.overallScore IS NOT NULL")
    Double findAverageOverallScore();

    @Query("SELECT MAX(a.overallScore) FROM AnalysisResult a WHERE a.overallScore IS NOT NULL")
    Integer findHighestOverallScore();
}