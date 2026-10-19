package com.zachary.sitecheck.exception;

/** Wrong email or password. Deliberately does not say which one. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
