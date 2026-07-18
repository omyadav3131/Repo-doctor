package com.omyadav.repodoctor.exception;

import com.omyadav.repodoctor.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RepositoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRepositoryNotFound(
            RepositoryNotFoundException exception) {

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "REPOSITORY_NOT_FOUND",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubNotFound(
            HttpClientErrorException.NotFound exception) {

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "REPOSITORY_NOT_FOUND",
                "GitHub repository or requested resource was not found"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationError(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Request validation failed");

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                message
        );

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception) {

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                exception.getMessage()
        );

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            NoResourceFoundException exception) {

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "RESOURCE_NOT_FOUND",
                "Requested API resource was not found"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubApiError(
            GitHubApiException exception) {

        int statusCode = exception.getStatusCode();

        HttpStatus httpStatus;
        try {
            httpStatus = HttpStatus.valueOf(statusCode);
        } catch (IllegalArgumentException ignored) {
            httpStatus = HttpStatus.BAD_GATEWAY;
        }

        ApiErrorResponse error = new ApiErrorResponse(
                httpStatus.value(),
                exception.getErrorCode(),
                exception.getMessage()
        );

        return ResponseEntity.status(httpStatus).body(error);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleTimeout(
            TimeoutException exception) {

        log.error("Request timed out: {}", exception.getMessage());

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.GATEWAY_TIMEOUT.value(),
                "ANALYSIS_TIMEOUT",
                "Repository analysis timed out. The repository may be too large. Please try again."
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error);
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ApiErrorResponse> handleCompletionException(
            CompletionException exception) {

        Throwable cause = exception.getCause();
        if (cause instanceof GitHubApiException gitHubEx) {
            return handleGitHubApiError(gitHubEx);
        }

        log.error("Async task failed: {}", exception.getMessage());

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "ANALYSIS_FAILED",
                "Repository analysis failed. Please try again."
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiErrorResponse> handleRestClientError(
            RestClientException exception) {

        log.error("External service error: {}", exception.getMessage());

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                "EXTERNAL_SERVICE_ERROR",
                "Unable to communicate with GitHub API"
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(
            Exception exception) {

        log.error("Unexpected error: {}", exception.getMessage(), exception);

        String message = "An unexpected server error occurred. Please try again.";
        if (exception instanceof org.springframework.dao.DataAccessException) {
             message = "A database error occurred while processing your request.";
        } else if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
             message = exception.getMessage();
        }

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                message
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}