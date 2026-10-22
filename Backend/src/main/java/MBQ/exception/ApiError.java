package com.MBQ.exception;

import java.time.Instant;
import java.util.Map;

/**
 * The single JSON shape every error response uses, so the React frontend
 * only has to handle one format.
 *
 * @param timestamp   when the error happened (UTC)
 * @param status      HTTP status code, e.g. 400
 * @param error       short reason, e.g. "Bad Request"
 * @param message     human-readable explanation
 * @param path        the URL that was called
 * @param fieldErrors per-field validation messages (empty when not a validation error)
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
