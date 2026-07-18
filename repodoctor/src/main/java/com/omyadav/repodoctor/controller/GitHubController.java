package com.omyadav.repodoctor.controller;

import com.omyadav.repodoctor.service.GitHubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(
            GitHubService gitHubService) {

        this.gitHubService = gitHubService;
    }

    @GetMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> getRateLimit() {

        Map<String, Object> rateLimit =
                gitHubService.getRateLimit();

        return ResponseEntity.ok(rateLimit);
    }
}