package com.omyadav.repodoctor.exception;

public class RepositoryNotFoundException
        extends RuntimeException {

    public RepositoryNotFoundException(
            String message) {

        super(message);
    }
}