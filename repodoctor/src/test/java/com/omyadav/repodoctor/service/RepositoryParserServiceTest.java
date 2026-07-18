package com.omyadav.repodoctor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryParserServiceTest {

    private RepositoryParserService repositoryParserService;

    @BeforeEach
    void setUp() {

        repositoryParserService =
                new RepositoryParserService();
    }

    @Test
    void shouldParseValidGitHubRepositoryUrl() {

        String[] result =
                repositoryParserService
                        .parseRepositoryUrl(
                                "https://github.com/omyadav3131/QuizCraft"
                        );

        assertArrayEquals(
                new String[]{
                        "omyadav3131",
                        "QuizCraft"
                },
                result
        );
    }

    @Test
    void shouldRemoveGitSuffixFromRepositoryName() {

        String[] result =
                repositoryParserService
                        .parseRepositoryUrl(
                                "https://github.com/omyadav3131/QuizCraft.git"
                        );

        assertArrayEquals(
                new String[]{
                        "omyadav3131",
                        "QuizCraft"
                },
                result
        );
    }

    @Test
    void shouldTrimRepositoryUrl() {

        String[] result =
                repositoryParserService
                        .parseRepositoryUrl(
                                "  https://github.com/omyadav3131/QuizCraft  "
                        );

        assertEquals(
                "omyadav3131",
                result[0]
        );

        assertEquals(
                "QuizCraft",
                result[1]
        );
    }

    @Test
    void shouldRejectNonGitHubRepositoryUrl() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                repositoryParserService
                                        .parseRepositoryUrl(
                                                "https://gitlab.com/omyadav3131/QuizCraft"
                                        )
                );

        assertEquals(
                "Only GitHub repositories are supported",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectGitHubUrlWithoutRepository() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                repositoryParserService
                                        .parseRepositoryUrl(
                                                "https://github.com/omyadav3131"
                                        )
                );

        assertEquals(
                "Invalid GitHub repository URL",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectGitHubHomeUrl() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                repositoryParserService
                                        .parseRepositoryUrl(
                                                "https://github.com"
                                        )
                );

        assertEquals(
                "Invalid GitHub repository URL",
                exception.getMessage()
        );
    }
}