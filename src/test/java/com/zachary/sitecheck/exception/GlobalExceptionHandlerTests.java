package com.zachary.sitecheck.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedErrorsGetAGeneric500WithoutInternals() throws Exception {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("SELECT * FROM users failed"));
        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("Something went wrong on the server");
    }

    @Test
    void duplicateKeyIsConflict() {
        ProblemDetail problem = handler.handleDuplicateKey(new DuplicateKeyException("uk_users_email_lower"));
        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).doesNotContain("uk_users_email_lower");
    }

    @Test
    void securityExceptionsAreLeftToSpringSecurity() {
        AccessDeniedException denied = new AccessDeniedException("Access Denied");
        assertThatThrownBy(() -> handler.handleUnexpected(denied)).isSameAs(denied);
        BadCredentialsException unauthenticated = new BadCredentialsException("bad");
        assertThatThrownBy(() -> handler.handleUnexpected(unauthenticated)).isSameAs(unauthenticated);
    }
}
