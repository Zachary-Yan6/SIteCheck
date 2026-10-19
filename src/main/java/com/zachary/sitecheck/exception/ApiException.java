package com.zachary.sitecheck.exception;

import org.springframework.http.HttpStatus;

/**
 * An error with a fixed HTTP status, turned into problem+json by {@link GlobalExceptionHandler}.
 *
 * <p>The status tells the app's sync queue what to do (FR-23): 4xx means "this request will
 * never succeed as sent" - mark the record failed (or re-login on 401) instead of retrying;
 * 5xx and network errors are temporary and retried with backoff.
 * One exception: 409 from a photo confirm means "S3 doesn't have the file" - upload it again.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    private ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
