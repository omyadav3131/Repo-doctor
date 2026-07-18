package com.omyadav.repodoctor.controller;

import com.omyadav.repodoctor.entity.AnalysisResult;
import com.omyadav.repodoctor.service.AnalysisHistoryService;
import com.omyadav.repodoctor.service.AnalysisResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisResultService analysisResultService;
    private final AnalysisHistoryService analysisHistoryService;

    public AnalysisController(
            AnalysisResultService analysisResultService,
            AnalysisHistoryService analysisHistoryService) {

        this.analysisResultService = analysisResultService;
        this.analysisHistoryService = analysisHistoryService;
    }

    @GetMapping
    public ResponseEntity<List<AnalysisResult>> getAllAnalyses() {
        List<AnalysisResult> analyses = analysisResultService.getAllAnalyses();
        return ResponseEntity.ok(analyses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAnalysisById(@PathVariable Long id) {
        return analysisResultService
                .getAnalysisById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{owner}/{repositoryName}")
    public ResponseEntity<List<AnalysisResult>> getRepositoryAnalysisHistory(
            @PathVariable String owner,
            @PathVariable String repositoryName) {

        List<AnalysisResult> history = analysisHistoryService.getRepositoryHistory(
                owner, repositoryName);

        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAnalysis(@PathVariable Long id) {

        boolean deleted = analysisResultService.deleteAnalysis(id);

        Map<String, Object> response = new LinkedHashMap<>();

        if (!deleted) {
            response.put("status", "ANALYSIS_NOT_FOUND");
            response.put("message", "Analysis result not found");
            return ResponseEntity.status(404).body(response);
        }

        response.put("deletedAnalysisId", id);
        response.put("status", "ANALYSIS_DELETED");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {

        Map<String, Object> dashboard = new LinkedHashMap<>();

        long totalAnalyses = analysisResultService.getTotalAnalysisCount();
        Double averageScore = analysisResultService.getAverageScore();
        Integer highestScore = analysisResultService.getHighestScore();

        List<AnalysisResult> allAnalyses = analysisResultService.getAllAnalyses();
        AnalysisResult recentAnalysis = allAnalyses.isEmpty() ? null : allAnalyses.get(0);

        dashboard.put("totalAnalyses", totalAnalyses);
        dashboard.put("averageScore", averageScore != null ? Math.round(averageScore * 10.0) / 10.0 : 0);
        dashboard.put("highestScore", highestScore != null ? highestScore : 0);

        if (recentAnalysis != null) {
            Map<String, Object> recent = new LinkedHashMap<>();
            recent.put("id", recentAnalysis.getId());
            recent.put("repositoryName", recentAnalysis.getRepositoryName());
            recent.put("owner", recentAnalysis.getOwner());
            recent.put("overallScore", recentAnalysis.getOverallScore());
            recent.put("grade", recentAnalysis.getGrade());
            recent.put("analysisDate", recentAnalysis.getAnalysisDate());
            dashboard.put("recentAnalysis", recent);
        }

        return ResponseEntity.ok(dashboard);
    }
}