package com.zachary.sitecheck.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 7807 problem+json responses. Extending
 * {@link ResponseEntityExceptionHandler} covers Spring's own errors too,
 * e.g. 400 for failed {@code @Valid} checks.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
    }

    /**
     * An id the phone generated (e.g. a photo UUID) is already taken. The primary key is the final
     * guard; this is the client's mistake, so 409 - the app marks the record failed instead of retrying.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleDuplicateKey(DuplicateKeyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A record with this id already exists");
    }

    /**
     * Anything unexpected (S3 down, a bug...): logged here with the stack trace, answered with the
     * same problem+json shape and no SQL, paths or exception text. 5xx tells the app to retry later.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e; // @PreAuthorize failures: Spring Security turns these into 403 / 401
        }
        log.error("Unhandled error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server");
    }
}
