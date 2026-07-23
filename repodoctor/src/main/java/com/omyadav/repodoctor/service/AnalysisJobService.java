package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.dto.AnalysisResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnalysisJobService {

    private final Map<String, AnalysisResponse> completedJobs = new ConcurrentHashMap<>();
    private final Map<String, String> jobStatuses = new ConcurrentHashMap<>();

    public void startJob(String jobId) {
        jobStatuses.put(jobId, "PENDING");
    }

    public void completeJob(String jobId, AnalysisResponse response) {
        completedJobs.put(jobId, response);
        jobStatuses.put(jobId, "COMPLETED");
    }

    public void failJob(String jobId, String reason) {
        jobStatuses.put(jobId, "FAILED: " + reason);
    }

    public String getJobStatus(String jobId) {
        return jobStatuses.getOrDefault(jobId, "NOT_FOUND");
    }

    public AnalysisResponse getJobResult(String jobId) {
        return completedJobs.get(jobId);
    }
}
