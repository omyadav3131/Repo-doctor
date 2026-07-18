package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.exception.GitHubApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    private final RestClient restClient;

    public GitHubService(
            @Value("${github.token:}") String githubToken) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(30_000);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory(requestFactory)
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        "application/vnd.github+json")
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "RepoDoctor")
                .defaultHeader(
                        "X-GitHub-Api-Version",
                        "2022-11-28");

        if (githubToken != null
                && !githubToken.isBlank()) {

            builder.defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + githubToken);
        }

        this.restClient = builder.build();
    }

    @Cacheable("githubRepo")
    @Retryable(retryFor = {GitHubApiException.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public Map<String, Object> getRepository(
            String owner,
            String repository) {

        Map<String, Object> repo = executeMapRequest(
                "/repos/{owner}/{repository}",
                owner,
                repository);

        if (repo == null) {
            throw new GitHubApiException(
                    404,
                    "REPOSITORY_NOT_FOUND",
                    "Repository not found: " + owner + "/" + repository);
        }

        if (repo.get("default_branch") == null) {
            log.warn("Repository has no default branch (possibly empty): {}/{}", owner, repository);
        }

        return repo;
    }

    @Cacheable("githubTree")
    @Retryable(retryFor = {GitHubApiException.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public Map<String, Object> getRepositoryTree(
            String owner,
            String repository,
            String branch) {

        try {
            return executeMapRequest(
                    "/repos/{owner}/{repository}/git/trees/{branch}?recursive=1",
                    owner,
                    repository,
                    branch);
        } catch (GitHubApiException e) {
            if (e.getStatusCode() == 404 || e.getStatusCode() == 409) {
                log.warn("Repository tree not available for {}/{} (status {})",
                        owner, repository, e.getStatusCode());
                return Map.of("tree", List.of(), "truncated", false);
            }
            throw e;
        }
    }

    @Cacheable("githubReadme")
    @Retryable(retryFor = {GitHubApiException.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public Map<String, Object> getReadme(
            String owner,
            String repository) {

        try {
            return executeMapRequest(
                    "/repos/{owner}/{repository}/readme",
                    owner,
                    repository);
        } catch (GitHubApiException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    @Cacheable("githubCommits")
    @Retryable(retryFor = {HttpClientErrorException.TooManyRequests.class, GitHubApiException.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCommits(
            String owner,
            String repository) {

        try {
            return restClient.get()
                    .uri(
                            "/repos/{owner}/{repository}/commits?per_page=100",
                            owner,
                            repository)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(List.class);

        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 409) {
                log.warn("No commits found for {}/{} (empty repo)", owner, repository);
                return List.of();
            }
            throw createGitHubException(exception);

        } catch (ResourceAccessException exception) {
            throw new GitHubApiException(
                    503,
                    "GITHUB_CONNECTION_ERROR",
                    "Unable to connect to GitHub API");
        }
    }

    public Map<String, Object> getRateLimit() {
        return executeMapRequest("/rate_limit");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeMapRequest(
            String uri,
            Object... uriVariables) {

        try {
            return restClient.get()
                    .uri(uri, uriVariables)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

        } catch (HttpClientErrorException exception) {
            throw createGitHubException(exception);

        } catch (ResourceAccessException exception) {
            log.error("GitHub API connection error: {}", exception.getMessage());
            throw new GitHubApiException(
                    503,
                    "GITHUB_CONNECTION_ERROR",
                    "Unable to connect to GitHub API. Please try again.");
        }
    }

    private GitHubApiException createGitHubException(
            HttpClientErrorException exception) {

        int statusCode = exception.getStatusCode().value();

        if (statusCode == 401) {
            return new GitHubApiException(
                    401,
                    "GITHUB_AUTHENTICATION_FAILED",
                    "GitHub authentication failed. Check your GitHub token.");
        }

        if (statusCode == 403) {
            String remaining = exception.getResponseHeaders() == null
                    ? null
                    : exception.getResponseHeaders()
                            .getFirst("X-RateLimit-Remaining");

            if ("0".equals(remaining)) {
                String resetHeader = exception.getResponseHeaders()
                        .getFirst("X-RateLimit-Reset");
                String message = "GitHub API rate limit exceeded.";
                if (resetHeader != null) {
                    try {
                        long resetEpoch = Long.parseLong(resetHeader);
                        long minutesUntilReset = Math.max(1,
                                (resetEpoch - System.currentTimeMillis() / 1000) / 60);
                        message += " Resets in approximately " + minutesUntilReset + " minutes.";
                    } catch (NumberFormatException ignored) {
                        // Use default message
                    }
                }
                return new GitHubApiException(429, "GITHUB_RATE_LIMIT_EXCEEDED", message);
            }

            return new GitHubApiException(
                    403,
                    "GITHUB_ACCESS_DENIED",
                    "GitHub API access was denied for this repository.");
        }

        if (statusCode == 404) {
            return new GitHubApiException(
                    404,
                    "REPOSITORY_NOT_FOUND",
                    "GitHub repository or requested resource was not found.");
        }

        if (statusCode == 409) {
            return new GitHubApiException(
                    409,
                    "REPOSITORY_EMPTY",
                    "Repository is empty or has no content.");
        }

        return new GitHubApiException(
                502,
                "GITHUB_API_ERROR",
                "GitHub API request failed with status " + statusCode);
    }
}