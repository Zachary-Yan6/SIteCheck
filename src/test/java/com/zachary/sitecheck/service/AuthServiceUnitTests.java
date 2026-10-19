package com.zachary.sitecheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zachary.sitecheck.dto.LoginRequest;
import com.zachary.sitecheck.dto.LoginResponse;
import com.zachary.sitecheck.dto.RegisterRequest;
import com.zachary.sitecheck.entity.Role;
import com.zachary.sitecheck.entity.User;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.exception.InvalidCredentialsException;
import com.zachary.sitecheck.mapper.UserMapper;
import com.zachary.sitecheck.security.AuthUser;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Login and sign-up rules with a mocked user table; BCrypt strength 4 keeps the tests fast. */
@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTests {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userMapper, passwordEncoder, jwtService);
    }

    @Test
    void loginWithTheRightPasswordIssuesAToken() {
        User aoife = user(2L, "aoife@sitecheck.demo", "Password123!");
        when(userMapper.selectOne(any())).thenReturn(aoife);
        when(jwtService.issue(aoife)).thenReturn(new JwtService.IssuedToken("jwt", Instant.EPOCH));

        LoginResponse response = service.login(new LoginRequest("aoife@sitecheck.demo", "Password123!"));

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.user().role()).isEqualTo(Role.INSPECTOR);
    }

    @Test
    void unknownEmailAndWrongPasswordFailTheSameWay() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@sitecheck.demo", "x")))
                .isInstanceOf(InvalidCredentialsException.class);

        when(userMapper.selectOne(any())).thenReturn(user(2L, "aoife@sitecheck.demo", "Password123!"));
        assertThatThrownBy(() -> service.login(new LoginRequest("aoife@sitecheck.demo", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).issue(any());
    }

    @Test
    void registerTrimsInputAndCreatesAnInspector() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(jwtService.issue(any())).thenReturn(new JwtService.IssuedToken("jwt", Instant.EPOCH));

        LoginResponse response = service.register(
                new RegisterRequest("  new@sitecheck.demo ", "Password123!", " New Person "));

        assertThat(response.user().email()).isEqualTo("new@sitecheck.demo");
        assertThat(response.user().fullName()).isEqualTo("New Person");
        assertThat(response.user().role()).isEqualTo(Role.INSPECTOR);
    }

    @Test
    void registerWithAnExistingEmailIsConflict() {
        when(userMapper.selectOne(any())).thenReturn(user(2L, "aoife@sitecheck.demo", "x"));
        assertThatThrownBy(() -> service.register(new RegisterRequest("aoife@sitecheck.demo", "Password123!", "Aoife")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void simultaneousSignUpCaughtByTheUniqueIndexIsConflict() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("uk_users_email_lower"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("race@sitecheck.demo", "Password123!", "Race")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void currentUserOfADeletedAccountIsRejected() {
        when(userMapper.selectById(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.currentUser(new AuthUser(7L, "gone@sitecheck.demo", Role.INSPECTOR)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void currentUserReadsTheAccount() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "aoife@sitecheck.demo", "x"));
        assertThat(service.currentUser(new AuthUser(2L, "aoife@sitecheck.demo", Role.INSPECTOR)).email())
                .isEqualTo("aoife@sitecheck.demo");
    }

    private User user(long id, String email, String password) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName("Aoife Murphy");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.INSPECTOR);
        return user;
    }
}
