package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;
import com.omyadav.repodoctor.dto.OverallResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScoringVerificationTest {

    private final OverallRepositoryScoreService overallScoreService = new OverallRepositoryScoreService();

    @Test
    public void testCase1_EmptyRepository() {
        DimensionResult hygiene = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult structure = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult readme = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult commitQuality = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult documentation = DimensionResult.notAnalyzable("No docs");
        DimensionResult codeQuality = DimensionResult.notAnalyzable("No code");

        OverallResult overall = overallScoreService.calculateOverallScore(hygiene, readme, structure, commitQuality, documentation, codeQuality, "java");
        int score = overall.getScore() != null ? overall.getScore() : 0;
        assertTrue(score >= 90 && score <= 100, "Empty repository should score 90-100 with 100 mocks, but got " + score);
    }

    @Test
    public void testCase2_RepositoryWithOnlyReadme() {
        DimensionResult hygiene = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult structure = DimensionResult.builder(AnalysisStatus.SUCCESS).score(50).confidence(1.0).build();
        DimensionResult readme = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult commitQuality = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult documentation = DimensionResult.notAnalyzable("No docs");
        DimensionResult codeQuality = DimensionResult.notAnalyzable("No code");

        OverallResult overall = overallScoreService.calculateOverallScore(hygiene, readme, structure, commitQuality, documentation, codeQuality, "java");
        int score = overall.getScore() != null ? overall.getScore() : 0;
        assertTrue(score >= 80 && score <= 100, "Repository with only README should score 80-100 with these mocks, but got " + score);
    }

    @Test
    public void testCase3_TemplateReactApp() {
        DimensionResult hygiene = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult structure = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult readme = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult commitQuality = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult documentation = DimensionResult.builder(AnalysisStatus.SUCCESS).score(50).confidence(1.0).build();
        
        DimensionResult codeQuality = DimensionResult.builder(AnalysisStatus.PARTIAL)
            .score(50).confidence(0.8)
            .detail("realSourceFileCount", 10)
            .detail("implementationHeavyFileCount", 2)
            .detail("businessLogicSignalCount", 1)
            .detail("executableMethodCount", 5)
            .detail("totalImplementationLines", 150)
            .detail("frameworkSignalCount", 1) // React
            .detail("apiImplementationCount", 0)
            .detail("databaseImplementationCount", 0)
            .detail("generatedLikeFileCount", 5)
            .build();

        OverallResult overall = overallScoreService.calculateOverallScore(hygiene, readme, structure, commitQuality, documentation, codeQuality, "javascript");
        int score = overall.getScore() != null ? overall.getScore() : 0;
        assertTrue(score >= 70 && score <= 90, "Template React app should score 70-90 based on weighted average, but got " + score);
    }

    @Test
    public void testCase4_RepositoryWithOnlyConfigFiles() {
        DimensionResult hygiene = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult structure = DimensionResult.builder(AnalysisStatus.SUCCESS).score(100).confidence(1.0).build();
        DimensionResult readme = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult commitQuality = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult documentation = DimensionResult.notAnalyzable("No docs");
        
        DimensionResult codeQuality = DimensionResult.builder(AnalysisStatus.PARTIAL)
            .score(20).confidence(0.6)
            .detail("realSourceFileCount", 2)
            .detail("implementationHeavyFileCount", 0)
            .detail("businessLogicSignalCount", 0)
            .detail("executableMethodCount", 0)
            .detail("totalImplementationLines", 25) // Less than 30 -> classified as EMPTY
            .build();

        OverallResult overall = overallScoreService.calculateOverallScore(hygiene, readme, structure, commitQuality, documentation, codeQuality, "yaml");
        int score = overall.getScore() != null ? overall.getScore() : 0;
        assertTrue(score >= 60 && score <= 80, "Repository with only config files should score 60-80 without artificial caps, but got " + score);
    }

    @Test
    public void testCase5_SmallStudentProject() {
        DimensionResult hygiene = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult structure = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult readme = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult commitQuality = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult documentation = DimensionResult.builder(AnalysisStatus.SUCCESS).score(60).confidence(1.0).build();
        
        DimensionResult codeQuality = DimensionResult.builder(AnalysisStatus.SUCCESS)
            .score(60).confidence(0.9)
            .detail("realSourceFileCount", 6)
            .detail("implementationHeavyFileCount", 4)
            .detail("businessLogicSignalCount", 2)
            .detail("executableMethodCount", 10)
            .detail("totalImplementationLines", 300)
            .detail("frameworkSignalCount", 0)
            .build();

        OverallResult overall = overallScoreService.calculateOverallScore(hygiene, readme, structure, commitQuality, documentation, codeQuality, "python");
        int score = overall.getScore() != null ? overall.getScore() : 0;
        assertTrue(score >= 65 && score <= 85, "Small student project should score 65-85 based on weighted averages, but got " + score);
    }

    @Test
    public void testCase6_MediumWorkingProject() {
        DimensionResult hygiene = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult structure = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult readme = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult commitQuality = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        DimensionResult documentation = DimensionResult.builder(AnalysisStatus.SUCCESS).score(80).confidence(1.0).build();
        
        DimensionResult codeQuality = DimensionResult.builder(AnalysisStatus.SUCCESS)
            .score(70).confidence(0.9)
            .detail("realSourceFileCount", 15)
            .detail("implementationHeavyFileCount", 10)
            .detail("businessLogicSignalCount", 10)
            .detail("executableMethodCount", 30)
            .detail("totalImplementationLines", 1000)
            .detail("frameworkSignalCount", 2)
            .detail("apiImplementationCount", 2)
            .detail("databaseImplementationCount", 1)
            .build();

        OverallResult overall = overallScoreService.calculateOverallScore(hygiene, readme, structure, commitQuality, documentation, codeQuality, "java");
        int score = overall.getScore() != null ? overall.getScore() : 0;
        assertTrue(score >= 70 && score <= 90, "Medium working project should score 70-90 based on weighted averages, but got " + score);
    }

    @Test
    public void testCase7_ProductionGradeRepository() {
        DimensionResult hygiene = DimensionResult.builder(AnalysisStatus.SUCCESS).score(95).confidence(1.0).build();
        DimensionResult structure = DimensionResult.builder(AnalysisStatus.SUCCESS).score(95).confidence(1.0).build();
        DimensionResult readme = DimensionResult.builder(AnalysisStatus.SUCCESS).score(95).confidence(1.0).build();
        DimensionResult commitQuality = DimensionResult.builder(AnalysisStatus.SUCCESS).score(95).confidence(1.0).build();
        DimensionResult documentation = DimensionResult.builder(AnalysisStatus.SUCCESS).score(95).confidence(1.0).build();
        
        DimensionResult codeQuality = DimensionResult.builder(AnalysisStatus.SUCCESS)
            .score(95).confidence(0.95)
            .detail("realSourceFileCount", 50)
            .detail("implementationHeavyFileCount", 30)
            .detail("businessLogicSignalCount", 25)
            .detail("executableMethodCount", 100)
            .detail("totalImplementationLines", 5000)
            .detail("frameworkSignalCount", 5)
            .detail("apiImplementationCount", 10)
            .detail("databaseImplementationCount", 5)
            .detail("testFileCount", 20)
            .build();

        OverallResult overall = overallScoreService.calculateOverallScore(hygiene, readme, structure, commitQuality, documentation, codeQuality, "java");
        int score = overall.getScore() != null ? overall.getScore() : 0;
        assertTrue(score >= 80 && score <= 95, "Production-grade repository should score 80-95, but got " + score);
    }
}
