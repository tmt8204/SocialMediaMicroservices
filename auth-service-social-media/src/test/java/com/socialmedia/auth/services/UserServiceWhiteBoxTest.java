package com.socialmedia.auth.services;

import com.socialmedia.auth.dto.*;
import com.socialmedia.auth.entities.*;
import com.socialmedia.auth.exceptions.*;
import com.socialmedia.auth.repositories.*;
import com.socialmedia.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceWhiteBoxTest {
        @Nested
        @DisplayName("registerUser")
        class RegisterUser {
            @Test
            void usernameExists_throwsConflict() {
                RegisterRequest req = new RegisterRequest();
                req.setUsername("user");
                req.setEmail("user@email.com");
                when(userRepository.existsByUsername("user")).thenReturn(true);
                assertThrows(ConflictException.class, () -> userService.registerUser(req));
            }

            @Test
            void emailExists_throwsConflict() {
                RegisterRequest req = new RegisterRequest();
                req.setUsername("user");
                req.setEmail("user@email.com");
                when(userRepository.existsByUsername("user")).thenReturn(false);
                when(userRepository.existsByEmail("user@email.com")).thenReturn(true);
                assertThrows(ConflictException.class, () -> userService.registerUser(req));
            }

            @Test
            void roleNotFound_throwsBadRequest() {
                RegisterRequest req = new RegisterRequest();
                req.setUsername("user");
                req.setEmail("user@email.com");
                req.setPassword("pass");
                req.setFullName("User");
                when(userRepository.existsByUsername("user")).thenReturn(false);
                when(userRepository.existsByEmail("user@email.com")).thenReturn(false);
                when(roleRepository.findByRoleName(anyString())).thenReturn(Optional.empty());
                assertThrows(BadRequestException.class, () -> userService.registerUser(req));
            }

            @Test
            void registerSuccess_publishEventOk() {
                RegisterRequest req = new RegisterRequest();
                req.setUsername("user");
                req.setEmail("user@email.com");
                req.setPassword("pass");
                req.setFullName("User");
                when(userRepository.existsByUsername("user")).thenReturn(false);
                when(userRepository.existsByEmail("user@email.com")).thenReturn(false);
                Role role = new Role();
                when(roleRepository.findByRoleName(anyString())).thenReturn(Optional.of(role));
                when(jwtService.generateAccessToken(any())).thenReturn("access");
                when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
                when(jwtService.extractExpiration(anyString())).thenReturn(java.util.Date.from(Instant.now().plusSeconds(3600)));
                IssuedAuthTokens tokens = userService.registerUser(req);
                assertNotNull(tokens);
                verify(userRepository).save(any(User.class));
                verify(kafkaEventProducer).publishCreateProfileEvent(any());
            }

            @Test
            void registerSuccess_publishEventFails() {
                RegisterRequest req = new RegisterRequest();
                req.setUsername("user");
                req.setEmail("user@email.com");
                req.setPassword("pass");
                req.setFullName("User");
                when(userRepository.existsByUsername("user")).thenReturn(false);
                when(userRepository.existsByEmail("user@email.com")).thenReturn(false);
                Role role = new Role();
                when(roleRepository.findByRoleName(anyString())).thenReturn(Optional.of(role));
                doThrow(new RuntimeException("Kafka error")).when(kafkaEventProducer).publishCreateProfileEvent(any());
                when(jwtService.generateAccessToken(any())).thenReturn("access");
                when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
                when(jwtService.extractExpiration(anyString())).thenReturn(java.util.Date.from(Instant.now().plusSeconds(3600)));
                IssuedAuthTokens tokens = userService.registerUser(req);
                assertNotNull(tokens);
                verify(userRepository).save(any(User.class));
                verify(kafkaEventProducer).publishCreateProfileEvent(any());
            }
        }
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock GoogleIdTokenVerifierService googleIdTokenVerifierService;
    @Mock UserTokenRepository userTokenRepository;
    @Mock EmailService emailService;
    @Mock KafkaEventProducer kafkaEventProducer;

    @InjectMocks UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("loginUser")
    class LoginUser {
        @Test
        void usernameNotFound_throwsUnauthorized() {
            when(userRepository.findByUsername("notfound")).thenReturn(Optional.empty());
            LoginRequest req = new LoginRequest("notfound", "pwd");
            assertThrows(UnauthorizedException.class, () -> userService.loginUser(req));
        }

        @Test
        void accountLocked_throwsUnauthorized() {
            User user = new User();
            user.setLockedUntil(Instant.now().plusSeconds(3600));
            when(userRepository.findByUsername("locked")).thenReturn(Optional.of(user));
            LoginRequest req = new LoginRequest("locked", "pwd");
            assertThrows(UnauthorizedException.class, () -> userService.loginUser(req));
        }

        @Test
        void lockExpired_wrongPassword_increaseFailedCount() {
            User user = new User();
            user.setLockedUntil(Instant.now().minusSeconds(3600));
            user.setLoginFailedCount(2);
            user.setPasswordHash("hash");
            when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
            LoginRequest req = new LoginRequest("user", "wrong");
            assertThrows(UnauthorizedException.class, () -> userService.loginUser(req));
            verify(userRepository).save(any(User.class));
        }

        @Test
        void wrongPassword_reachThreshold_lockAccount() {
            User user = new User();
            user.setLoginFailedCount(4); // MAX_FAILED_LOGIN_ATTEMPTS = 5
            user.setPasswordHash("hash");
            when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
            LoginRequest req = new LoginRequest("user", "wrong");
            assertThrows(UnauthorizedException.class, () -> userService.loginUser(req));
            verify(userRepository).save(argThat(u -> u.getLockedUntil() != null && u.getLoginFailedCount() == 0));
        }

        @Test
        void correctPassword_butInactive_throwsUnauthorized() {
            User user = new User();
            user.setPasswordHash("hash");
            user.setIsActive(false);
            when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("right", "hash")).thenReturn(true);
            LoginRequest req = new LoginRequest("user", "right");
            assertThrows(UnauthorizedException.class, () -> userService.loginUser(req));
        }

        @Test
        void correctPassword_andActive_success() {
            User user = new User();
            user.setPasswordHash("hash");
            user.setIsActive(true);
            user.setId("uid");
            when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("right", "hash")).thenReturn(true);
            doNothing().when(userTokenRepository).deleteByUserId("uid");
            when(jwtService.generateAccessToken(any())).thenReturn("access");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
            when(jwtService.extractExpiration(anyString())).thenReturn(java.util.Date.from(Instant.now().plusSeconds(3600)));
            IssuedAuthTokens tokens = userService.loginUser(new LoginRequest("user", "right"));
            assertNotNull(tokens);
            verify(userRepository).save(any(User.class));
            verify(userTokenRepository).deleteByUserId("uid");
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {
        @Test
        void userIdNull_throwsUnauthorized() {
            ChangePasswordRequest req = new ChangePasswordRequest("old", "new");
            assertThrows(UnauthorizedException.class, () -> userService.changePassword(null, req));
        }

        @Test
        void userNotFound_throwsBadRequest() {
            when(userRepository.findById("uid")).thenReturn(Optional.empty());
            ChangePasswordRequest req = new ChangePasswordRequest("old", "new");
            assertThrows(BadRequestException.class, () -> userService.changePassword("uid", req));
        }

        @Test
        void currentPasswordWrong_throwsUnauthorized() {
            User user = new User();
            user.setPasswordHash("hash");
            when(userRepository.findById("uid")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("old", "hash")).thenReturn(false);
            ChangePasswordRequest req = new ChangePasswordRequest("old", "new");
            assertThrows(UnauthorizedException.class, () -> userService.changePassword("uid", req));
        }

        @Test
        void newPasswordNull_throwsBadRequest() {
            User user = new User();
            user.setPasswordHash("hash");
            when(userRepository.findById("uid")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("old", "hash")).thenReturn(true);
            ChangePasswordRequest req = new ChangePasswordRequest("old", null);
            assertThrows(BadRequestException.class, () -> userService.changePassword("uid", req));
        }

        @Test
        void newPasswordSameAsOld_throwsBadRequest() {
            User user = new User();
            user.setPasswordHash("hash");
            when(userRepository.findById("uid")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("old", "hash")).thenReturn(true);
            when(passwordEncoder.matches("old", "hash")).thenReturn(true);
            when(passwordEncoder.matches("new", "hash")).thenReturn(true); // new == old
            ChangePasswordRequest req = new ChangePasswordRequest("old", "new");
            assertThrows(BadRequestException.class, () -> userService.changePassword("uid", req));
        }

        @Test
        void validChangePassword_success() {
            User user = new User();
            user.setId("uid");
            user.setPasswordHash("hash");
            when(userRepository.findById("uid")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("old", "hash")).thenReturn(true);
            when(passwordEncoder.matches("new", "hash")).thenReturn(false);
            when(passwordEncoder.encode("new")).thenReturn("newhash");
            doNothing().when(userTokenRepository).deleteByUserId("uid");
            ChangePasswordRequest req = new ChangePasswordRequest("old", "new");
            String result = userService.changePassword("uid", req);
            assertEquals("Password changed successfully", result);
            verify(userRepository).save(any(User.class));
            verify(userTokenRepository).deleteByUserId("uid");
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {
        @Test
        void refreshTokenNull_throwsBadRequest() {
            assertThrows(BadRequestException.class, () -> userService.refreshToken(null));
        }

        @Test
        void invalidJwtOrNotRefreshToken_throwsUnauthorized() {
            when(jwtService.isTokenValid("bad")).thenReturn(false);
            assertThrows(UnauthorizedException.class, () -> userService.refreshToken("bad"));

            when(jwtService.isTokenValid("refresh")).thenReturn(true);
            when(jwtService.isRefreshToken("refresh")).thenReturn(false);
            assertThrows(UnauthorizedException.class, () -> userService.refreshToken("refresh"));
        }

        @Test
        void tokenNotFoundOrRevoked_throwsUnauthorized() {
            when(jwtService.isTokenValid("refresh")).thenReturn(true);
            when(jwtService.isRefreshToken("refresh")).thenReturn(true);
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("refresh", TokenType.REFRESH)).thenReturn(Optional.empty());
            assertThrows(UnauthorizedException.class, () -> userService.refreshToken("refresh"));
        }

        @Test
        void userNotFound_throwsUnauthorized() {
            when(jwtService.isTokenValid("refresh")).thenReturn(true);
            when(jwtService.isRefreshToken("refresh")).thenReturn(true);
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("refresh", TokenType.REFRESH)).thenReturn(Optional.of(new UserToken()));
            when(jwtService.extractUserId("refresh")).thenReturn("uid");
            when(userRepository.findById("uid")).thenReturn(Optional.empty());
            assertThrows(UnauthorizedException.class, () -> userService.refreshToken("refresh"));
        }

        @Test
        void validRefreshToken_success() {
            UserToken token = new UserToken();
            when(jwtService.isTokenValid("refresh")).thenReturn(true);
            when(jwtService.isRefreshToken("refresh")).thenReturn(true);
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("refresh", TokenType.REFRESH)).thenReturn(Optional.of(token));
            when(jwtService.extractUserId("refresh")).thenReturn("uid");
            User user = new User();
            user.setId("uid");
            when(userRepository.findById("uid")).thenReturn(Optional.of(user));
            doNothing().when(userTokenRepository).deleteByToken("refresh");
            when(jwtService.generateAccessToken(any())).thenReturn("access");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh2");
            when(jwtService.extractExpiration(anyString())).thenReturn(java.util.Date.from(Instant.now().plusSeconds(3600)));
            IssuedAuthTokens tokens = userService.refreshToken("refresh");
            assertNotNull(tokens);
            verify(userTokenRepository).deleteByToken("refresh");
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {
        @Test
        void tokenNull_throwsBadRequest() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken(null);
            req.setNewPassword("Newpass1!");
            assertThrows(BadRequestException.class, () -> userService.resetPassword(req));
        }

        @Test
        void tokenNotFoundOrRevoked_throwsBadRequest() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("Newpass1!");
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("token", TokenType.RESET_PASSWORD)).thenReturn(Optional.empty());
            assertThrows(BadRequestException.class, () -> userService.resetPassword(req));
        }

        @Test
        void tokenExpired_throwsBadRequestAndDeletesToken() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("Newpass1!");
            UserToken token = new UserToken();
            token.setExpiresAt(Instant.now().minusSeconds(10));
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("token", TokenType.RESET_PASSWORD)).thenReturn(Optional.of(token));
            assertThrows(BadRequestException.class, () -> userService.resetPassword(req));
            verify(userTokenRepository).delete(token);
        }

        @Test
        void userNotFound_throwsBadRequest() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("Newpass1!");
            UserToken token = new UserToken();
            token.setExpiresAt(Instant.now().plusSeconds(100));
            token.setUserId("uid");
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("token", TokenType.RESET_PASSWORD)).thenReturn(Optional.of(token));
            when(userRepository.findById("uid")).thenReturn(Optional.empty());
            assertThrows(BadRequestException.class, () -> userService.resetPassword(req));
        }

        @Test
        void newPasswordNotStrong_throwsBadRequest() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("weak");
            UserToken token = new UserToken();
            token.setExpiresAt(Instant.now().plusSeconds(100));
            token.setUserId("uid");
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("token", TokenType.RESET_PASSWORD)).thenReturn(Optional.of(token));
            User user = new User();
            when(userRepository.findById("uid")).thenReturn(Optional.of(user));
            // password does not match regex
            assertThrows(BadRequestException.class, () -> userService.resetPassword(req));
        }

        @Test
        void newPasswordSameAsOld_throwsBadRequest() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("Newpass1!");
            UserToken token = new UserToken();
            token.setExpiresAt(Instant.now().plusSeconds(100));
            token.setUserId("uid");
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("token", TokenType.RESET_PASSWORD)).thenReturn(Optional.of(token));
            User user = new User();
            user.setPasswordHash("hash");
            when(userRepository.findById("uid")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Newpass1!", "hash")).thenReturn(true);
            assertThrows(BadRequestException.class, () -> userService.resetPassword(req));
        }

        @Test
        void validResetPassword_success() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("Newpass1!");
            UserToken token = new UserToken();
            token.setExpiresAt(Instant.now().plusSeconds(100));
            token.setUserId("uid");
            when(userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse("token", TokenType.RESET_PASSWORD)).thenReturn(Optional.of(token));
            User user = new User();
            user.setId("uid");
            user.setPasswordHash("hash");
            when(userRepository.findById("uid")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Newpass1!", "hash")).thenReturn(false);
            when(passwordEncoder.encode("Newpass1!")).thenReturn("newhash");
            doNothing().when(userTokenRepository).deleteByUserId("uid");
            userService.resetPassword(req);
            verify(userRepository).save(any(User.class));
            verify(userTokenRepository).deleteByUserId("uid");
        }
    }
}
