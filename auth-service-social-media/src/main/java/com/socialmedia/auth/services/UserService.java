package com.socialmedia.auth.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.socialmedia.auth.dto.AuthResponse;
import com.socialmedia.auth.dto.ChangePasswordRequest;
import com.socialmedia.auth.dto.CreateProfileEvent;
import com.socialmedia.auth.dto.ForgotPasswordRequest;
import com.socialmedia.auth.dto.LoginRequest;
import com.socialmedia.auth.dto.RegisterRequest;
import com.socialmedia.auth.dto.ResetPasswordRequest;
import com.socialmedia.auth.entities.Role;
import com.socialmedia.auth.entities.TokenType;
import com.socialmedia.auth.entities.User;
import com.socialmedia.auth.entities.UserToken;
import com.socialmedia.auth.exceptions.BadRequestException;
import com.socialmedia.auth.exceptions.ConflictException;
import com.socialmedia.auth.exceptions.UnauthorizedException;
import com.socialmedia.auth.repositories.RoleRepository;
import com.socialmedia.auth.repositories.UserRepository;
import com.socialmedia.auth.repositories.UserTokenRepository;
import com.socialmedia.auth.security.JwtService;

/**
 * UserService
 * 
 * Handles all user-related operations including:
 * - User registration and validation
 * - User login and authentication
 * - Token management (JWT access and refresh tokens)
 * - Password changes
 * - User logout
 */
@Service
public class UserService {

    // Constants for default role and security policies
    private static final String DEFAULT_ROLE = "USER";
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5; // Max failed login attempts before lockout
    private static final long LOCKOUT_DURATION_HOURS = 24; // Lockout duration in hours

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserTokenRepository userTokenRepository;
    private final EmailService emailService;
    private final KafkaEventProducer kafkaEventProducer;

    @Value("${app.reset-password-token-expiration-ms}")
    private long resetPasswordTokenExpirationMs;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, UserTokenRepository userTokenRepository, EmailService emailService,
            KafkaEventProducer kafkaEventProducer) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userTokenRepository = userTokenRepository;
        this.emailService = emailService;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    // -------------------- User Registration ------------------- //
    public AuthResponse registerUser(RegisterRequest registerRequest) {
        // Validate input
        if (registerRequest.getUsername() == null || registerRequest.getUsername().isBlank()) {
            throw new BadRequestException("Username is required");
        }
        if (registerRequest.getUsername().length() < 6) {
            throw new BadRequestException("Username must have at least 6 characters");
        }
        if (registerRequest.getEmail() == null || registerRequest.getEmail().isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (registerRequest.getPassword() == null || registerRequest.getPassword().isBlank()) {
            throw new BadRequestException("Password is required");
        }

        String strongPasswordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
        if (!registerRequest.getPassword().matches(strongPasswordRegex)) {
            throw new BadRequestException("Password must contain at least 8 characters including uppercase, lowercase, digit and special character (@$!%*?&)");
        }

        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        // Create new user
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFullName(registerRequest.getFullName());

        // Assign default role
        Role role = roleRepository.findByRoleName(DEFAULT_ROLE)
            .orElseThrow(() -> new BadRequestException("Role not found: " + DEFAULT_ROLE));

        user.setRole(role);

        // Save user to database
        userRepository.save(user);

        // Publish CreateProfileEvent to Kafka topic for user-service to consume
        try {
            publishUserCreatedEvent(user);
        } catch (Exception ex) {
            // Log error but don't rollback registration
            // User service can handle this asynchronously or via retry
            System.err.println("Failed to publish CreateProfileEvent for user: " + user.getUsername() + ". Error: " + ex.getMessage());
        }

        return issueTokens(user);
    }

    /**
     * Publish user created event to Kafka topic
     * Notifies user-service to create user profile
     */
    private void publishUserCreatedEvent(User user) {
        CreateProfileEvent event = new CreateProfileEvent(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            System.currentTimeMillis(),
            "USER_CREATED"
        );
        kafkaEventProducer.publishCreateProfileEvent(event);
    }

    // -------------------- User Login ------------------- //
    public AuthResponse loginUser(LoginRequest loginRequest) {
        // Validate input
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        Instant now = Instant.now();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BadRequestException("Username and password must not be empty");
        }

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        // Block login while lock window is still active.
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                throw new UnauthorizedException("Account is locked until " + user.getLockedUntil());
            }

            // Lock window has expired, reset counters for a fresh retry window.
            user.setLoginFailedCount(0);
            user.setLockedUntil(null);
        }

        // Verify password and apply lockout policy on failure.
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setLoginFailedCount(user.getLoginFailedCount() + 1);

            if (user.getLoginFailedCount() >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCKOUT_DURATION_HOURS, ChronoUnit.HOURS));
                user.setLoginFailedCount(0);
            }

            userRepository.save(user);

            throw new UnauthorizedException("Invalid username or password");
        }

        // Check account status (null-safe for old records missing isActive field).
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Account is deactivated. Please contact support.");
        }

        // Successful login resets all lock/rate-limit state.
        user.setLoginFailedCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        return issueTokens(user);
    }

    

    // -------------------- User Logout ------------------- //
    public void logout(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }

        User user = userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new BadRequestException("User not found"));

        userTokenRepository.deleteByUserId(user.getId());
    }

    // -------------------- Change Password ------------------- //
    public String changePassword(Authentication authentication, ChangePasswordRequest changePasswordRequest) {
        // Validate input
        if (authentication == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }

        User user = userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new BadRequestException("User not found"));

        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        // Validate new password
        String newPassword = changePasswordRequest.getNewPassword();
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password must not be empty");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from current password");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Password reset invalidates all sessions and refresh grants.
        userTokenRepository.deleteByUserId(user.getId());

        return "Password changed successfully";
    }

    // -------------------- Helper JWT ------------------- //
    public AuthResponse refreshToken(String refreshToken) {
        // Validate input
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }

        if (!jwtService.isTokenValid(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse(refreshToken, TokenType.REFRESH)
            .orElseThrow(() -> new UnauthorizedException("Refresh token is expired or revoked"));

        String userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Rotation prevents a stolen old refresh token from being reused.
        userTokenRepository.deleteByToken(refreshToken);
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        saveToken(user.getId(), accessToken, TokenType.ACCESS, jwtService.extractExpiration(accessToken).toInstant());
        saveToken(user.getId(), refreshToken, TokenType.REFRESH, jwtService.extractExpiration(refreshToken).toInstant());

        String roleName = user.getRole() != null ? user.getRole().getRoleName() : DEFAULT_ROLE;
        return new AuthResponse(
            accessToken,
            refreshToken,
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            roleName);
    }

    private void saveToken(String userId, String token, TokenType tokenType, Instant expiresAt) {
        UserToken userToken = new UserToken();
        userToken.setUserId(userId);
        userToken.setToken(token);
        userToken.setTokenType(tokenType);
        userToken.setExpiresAt(expiresAt);
        userToken.setRevoked(false);
        userTokenRepository.save(userToken);
    }

    // -------------------- Forgot Password ------------------- //
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always respond with a generic message to prevent email enumeration
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            // Invalidate any existing reset tokens for this user
            userTokenRepository.deleteByUserIdAndTokenType(user.getId(), TokenType.RESET_PASSWORD);

            String resetToken = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plusMillis(resetPasswordTokenExpirationMs);
            saveToken(user.getId(), resetToken, TokenType.RESET_PASSWORD, expiresAt);

            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        });
    }

    // -------------------- Reset Password ------------------- //
    public void resetPassword(ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new BadRequestException("Token is required");
        }

        UserToken userToken = userTokenRepository
                .findByTokenAndTokenTypeAndRevokedFalse(request.getToken(), TokenType.RESET_PASSWORD)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        if (userToken.getExpiresAt().isBefore(Instant.now())) {
            userTokenRepository.delete(userToken);
            throw new BadRequestException("Password reset token has expired");
        }

        User user = userRepository.findById(userToken.getUserId())
                .orElseThrow(() -> new BadRequestException("User not found"));

        String newPassword = request.getNewPassword();
        String strongPasswordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
        if (!newPassword.matches(strongPasswordRegex)) {
            throw new BadRequestException("Password must contain at least 8 characters including uppercase, lowercase, digit and special character (@$!%*?&)");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Invalidate all tokens (access, refresh, reset) after successful password reset
        userTokenRepository.deleteByUserId(user.getId());
    }
}
