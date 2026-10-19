package com.zachary.sitecheck.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zachary.sitecheck.dto.LoginRequest;
import com.zachary.sitecheck.dto.LoginResponse;
import com.zachary.sitecheck.dto.RegisterRequest;
import com.zachary.sitecheck.dto.UserResponse;
import com.zachary.sitecheck.entity.Role;
import com.zachary.sitecheck.entity.User;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.exception.InvalidCredentialsException;
import com.zachary.sitecheck.mapper.UserMapper;
import com.zachary.sitecheck.security.AuthUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Checked against when the email is unknown, so both failure paths take about the same
    // time and response timing does not reveal which emails exist.
    private final String dummyHash;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    public LoginResponse login(LoginRequest request) {
        User user = findByEmail(request.email());
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), UserResponse.from(user));
    }

    /**
     * Public sign-up: creates an INSPECTOR and logs them straight in. They see no sites until
     * they are added to {@code site_inspectors}.
     */
    public LoginResponse register(RegisterRequest request) {
        String email = request.email().strip();
        if (findByEmail(email) != null) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().strip());
        user.setRole(Role.INSPECTOR);
        try {
            userMapper.insert(user);  // fills user.id; created_at / updated_at use the column defaults
        } catch (DuplicateKeyException e) {
            // Two sign-ups with the same email at the same moment: uk_users_email_lower catches the second.
            throw ApiException.conflict("An account with this email already exists");
        }
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), UserResponse.from(user));
    }

    public UserResponse currentUser(AuthUser authUser) {
        User user = userMapper.selectById(authUser.id());
        if (user == null) {
            // Token is still valid but the account has been deleted.
            throw new InvalidCredentialsException();
        }
        return UserResponse.from(user);
    }

    private User findByEmail(String email) {
        // lower() on both sides matches the uk_users_email_lower index.
        return userMapper.selectOne(new QueryWrapper<User>().apply("lower(email) = lower({0})", email));
    }
}
