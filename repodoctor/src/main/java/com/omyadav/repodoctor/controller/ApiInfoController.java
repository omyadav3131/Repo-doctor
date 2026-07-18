package com.omyadav.repodoctor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ApiInfoController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getApiInformation() {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "application",
                "RepoDoctor"
        );

        response.put(
                "description",
                "GitHub Repository Health and Code Quality Analysis API"
        );

        response.put(
                "version",
                "1.0.0"
        );

        response.put(
                "apiStatus",
                "RUNNING"
        );

        response.put(
                "healthEndpoint",
                "/api/health"
        );

        response.put(
                "analysisEndpoint",
                "/api/analyze"
        );

        response.put(
                "swaggerUi",
                "/swagger-ui/index.html"
        );

        response.put(
                "openApiDocs",
                "/v3/api-docs"
        );

        response.put(
                "status",
                "REPODOCTOR_API_READY"
        );

        return ResponseEntity.ok(response);
    }
}