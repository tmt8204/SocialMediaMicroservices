package com.socialmedia.auth.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import com.socialmedia.auth.dto.AuthResponse;
import com.socialmedia.auth.dto.ChangePasswordRequest;
import com.socialmedia.auth.dto.CreateProfileEvent;
import com.socialmedia.auth.dto.ForgotPasswordRequest;
import com.socialmedia.auth.dto.GoogleUserProfile;
import com.socialmedia.auth.dto.IssuedAuthTokens;
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
    private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    private final UserTokenRepository userTokenRepository;
    private final EmailService emailService;
    private final KafkaEventProducer kafkaEventProducer;

    @Value("${app.reset-password-token-expiration-ms}")
    private long resetPasswordTokenExpirationMs;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, GoogleIdTokenVerifierService googleIdTokenVerifierService,
            UserTokenRepository userTokenRepository, EmailService emailService,
            KafkaEventProducer kafkaEventProducer) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleIdTokenVerifierService = googleIdTokenVerifierService;
        this.userTokenRepository = userTokenRepository;
        this.emailService = emailService;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    // -------------------- User Registration ------------------- //
    public IssuedAuthTokens registerUser(RegisterRequest registerRequest) {

        // Validate input
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
    public IssuedAuthTokens loginUser(LoginRequest loginRequest) {
        // Validate input
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        Instant now = Instant.now();

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

        // Re-login invalidates all previously issued tokens for this user.
        userTokenRepository.deleteByUserId(user.getId());

        return issueTokens(user);
    }

    public IssuedAuthTokens loginWithGoogle(String idToken) {
        GoogleUserProfile googleUserProfile = googleIdTokenVerifierService.verify(idToken);
        Instant now = Instant.now();

        User user = userRepository.findByEmail(googleUserProfile.email())
                .orElseGet(() -> createGoogleUser(googleUserProfile));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Account is deactivated. Please contact support.");
        }

        user.setEmailVerified(true);
        user.setLoginFailedCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        if (!StringUtils.hasText(user.getFullName()) && StringUtils.hasText(googleUserProfile.fullName())) {
            user.setFullName(googleUserProfile.fullName());
        }
        userRepository.save(user);

        userTokenRepository.deleteByUserId(user.getId());
        return issueTokens(user);
    }

    

    // -------------------- User Logout ------------------- //
    public void logout(String accessToken, String refreshToken) {
        String userId = extractUserIdForLogout(accessToken, refreshToken);
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadRequestException("User not found"));

        userTokenRepository.deleteByUserId(user.getId());
    }

    // -------------------- Change Password ------------------- //
    //dùng jwt đã decode trong gateway để lấy X-User-Id rồi tìm user trong db, sau đó check password cũ có đúng không, nếu đúng thì update password mới, đồng thời xóa hết token cũ đi để bắt đăng nhập lại
    public String changePassword(String userId, ChangePasswordRequest changePasswordRequest) {
        // Validate input
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }

        User user = userRepository.findById(userId)
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

    // -------------------- JWT ------------------- //
    public IssuedAuthTokens refreshToken(String refreshToken) {
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

    private IssuedAuthTokens issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        saveToken(user.getId(), accessToken, TokenType.ACCESS, jwtService.extractExpiration(accessToken).toInstant());
        saveToken(user.getId(), refreshToken, TokenType.REFRESH, jwtService.extractExpiration(refreshToken).toInstant());

        String roleName = user.getRole() != null ? user.getRole().getRoleName() : DEFAULT_ROLE;
        return new IssuedAuthTokens(
            new AuthResponse(
                accessToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roleName),
            refreshToken);
    }

    private User createGoogleUser(GoogleUserProfile googleUserProfile) {
        User user = new User();
        user.setUsername(generateUniqueGoogleUsername(googleUserProfile.email()));
        user.setEmail(googleUserProfile.email());
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setFullName(googleUserProfile.fullName());
        user.setEmailVerified(true);
        user.setRole(loadDefaultRole());

        userRepository.save(user);

        try {
            publishUserCreatedEvent(user);
        } catch (Exception ex) {
            System.err.println("Failed to publish CreateProfileEvent for Google user: " + user.getUsername() + ". Error: " + ex.getMessage());
        }

        return user;
    }

    private Role loadDefaultRole() {
        return roleRepository.findByRoleName(DEFAULT_ROLE)
                .orElseThrow(() -> new BadRequestException("Role not found: " + DEFAULT_ROLE));
    }

    private String generateUniqueGoogleUsername(String email) {
        String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
        String base = localPart.replaceAll("[^a-z0-9._]", "");
        if (!StringUtils.hasText(base)) {
            base = "googleuser";
        }
        if (base.length() < 6) {
            base = (base + "googleuser").substring(0, 6);
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private String extractUserIdForLogout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()
                && jwtService.isTokenValid(accessToken)
                && jwtService.isAccessToken(accessToken)
                && userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse(accessToken, TokenType.ACCESS).isPresent()) {
            return jwtService.extractUserId(accessToken);
        }

        if (refreshToken != null && !refreshToken.isBlank()
                && jwtService.isTokenValid(refreshToken)
                && jwtService.isRefreshToken(refreshToken)
                && userTokenRepository.findByTokenAndTokenTypeAndRevokedFalse(refreshToken, TokenType.REFRESH).isPresent()) {
            return jwtService.extractUserId(refreshToken);
        }

        return null;
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
