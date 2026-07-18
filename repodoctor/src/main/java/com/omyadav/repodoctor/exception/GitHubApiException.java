package com.omyadav.repodoctor.exception;

public class GitHubApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public GitHubApiException(
            int statusCode,
            String errorCode,
            String message) {

        super(message);

        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}