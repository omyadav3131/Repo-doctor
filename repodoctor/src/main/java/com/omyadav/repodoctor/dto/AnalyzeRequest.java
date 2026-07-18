package com.omyadav.repodoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AnalyzeRequest {

    @NotBlank(message = "Repository URL is required")
    @Pattern(regexp = "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?$", message = "Must be a valid GitHub repository URL (e.g., https://github.com/owner/repo)")
    private String repositoryUrl;

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
}