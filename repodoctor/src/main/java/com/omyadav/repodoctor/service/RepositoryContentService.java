package com.omyadav.repodoctor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class RepositoryContentService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryContentService.class);

    private static final int MAX_FILE_SIZE_BYTES = 512_000; // 500KB limit

    private final RestClient restClient;

    public RepositoryContentService(
            @Value("${github.token:}") String githubToken) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "RepoDoctor");

        if (githubToken != null && !githubToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken);
        }

        this.restClient = builder.build();
    }

    public String getRawFileContent(
            String owner,
            String repository,
            String branch,
            String filePath) {

        try {
            String encodedPath = encodePath(filePath);

            String url =
                    "https://raw.githubusercontent.com/"
                            + owner + "/"
                            + repository + "/"
                            + branch + "/"
                            + encodedPath;

            String content = restClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);

            if (content != null && content.length() > MAX_FILE_SIZE_BYTES) {
                log.debug("Skipping large file ({} bytes): {}", content.length(), filePath);
                return null;
            }

            return content;

        } catch (Exception exception) {
            log.debug("Failed to fetch file content: {} - {}", filePath, exception.getMessage());
            return null;
        }
    }

    private String encodePath(String filePath) {
        String[] segments = filePath.split("/");
        StringBuilder encodedPath = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encodedPath.append("/");
            }
            encodedPath.append(encodeSegment(segments[i]));
        }

        return encodedPath.toString();
    }

    private String encodeSegment(String segment) {
        StringBuilder result = new StringBuilder();

        for (byte value : segment.getBytes(StandardCharsets.UTF_8)) {
            int character = value & 0xFF;

            if (isUnreserved(character)) {
                result.append((char) character);
            } else {
                result.append(String.format("%%%02X", character));
            }
        }

        return result.toString();
    }

    private boolean isUnreserved(int character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '-'
                || character == '.'
                || character == '_'
                || character == '~';
    }
}