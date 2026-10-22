package com.MBQ.exception;

/**
 * Thrown when a requested record (patient, ticket, department, facility) does not exist.
 * The {@link GlobalExceptionHandler} turns it into an HTTP 404 response.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " with id " + id + " was not found");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
