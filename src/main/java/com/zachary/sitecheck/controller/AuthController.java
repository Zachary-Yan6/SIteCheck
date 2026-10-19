package com.zachary.sitecheck.controller;

import com.zachary.sitecheck.dto.LoginRequest;
import com.zachary.sitecheck.dto.LoginResponse;
import com.zachary.sitecheck.dto.RegisterRequest;
import com.zachary.sitecheck.dto.UserResponse;
import com.zachary.sitecheck.security.AuthUser;
import com.zachary.sitecheck.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** FR-01: email + password in, JWT out. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Public sign-up, always as an INSPECTOR. Returns the same body as login, so the app is logged in straight away. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Lets the app check a stored token is still accepted after coming back online (FR-03). */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return authService.currentUser(authUser);
    }
}
