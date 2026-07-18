package com.omyadav.repodoctor.service;

import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class RepositoryParserService {

    public String[] parseRepositoryUrl(String repositoryUrl) {

        try {
            URI uri = URI.create(repositoryUrl.trim());

            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException("Only GitHub repositories are supported");
            }

            String path = uri.getPath();

            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Invalid GitHub repository URL");
            }

            // Remove trailing slashes
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String[] parts = path.split("/");

            // Need at least 3 parts: ["", "owner", "repo"] (possibly more like /tree/main)
            if (parts.length < 3 ||
                    parts[1].isBlank() ||
                    parts[2].isBlank()) {

                throw new IllegalArgumentException("Invalid GitHub repository URL");
            }

            String owner = parts[1];
            String repository = parts[2];

            if (repository.endsWith(".git")) {
                repository = repository.substring(0, repository.length() - 4);
            }

            return new String[]{owner, repository};

        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid repository URL");
        }
    }
}