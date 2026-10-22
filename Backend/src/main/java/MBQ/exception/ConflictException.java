package com.MBQ.exception;

/**
 * Thrown when a request is valid on its own but breaks a queue rule,
 * e.g. completing a ticket that was never started, or giving a patient two active tickets.
 * The {@link GlobalExceptionHandler} turns it into an HTTP 409 response.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
