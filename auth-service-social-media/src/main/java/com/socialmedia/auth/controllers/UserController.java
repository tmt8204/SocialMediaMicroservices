package com.socialmedia.auth.controllers;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.socialmedia.auth.dto.AuthResponse;
import com.socialmedia.auth.dto.ChangePasswordRequest;
import com.socialmedia.auth.dto.ForgotPasswordRequest;
import com.socialmedia.auth.dto.LoginRequest;
import com.socialmedia.auth.dto.RefreshTokenRequest;
import com.socialmedia.auth.dto.RegisterRequest;
import com.socialmedia.auth.dto.ResetPasswordRequest;
import com.socialmedia.auth.services.UserService;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    @Autowired
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        AuthResponse response = userService.registerUser(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = userService.loginUser(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        AuthResponse response = userService.refreshToken(refreshTokenRequest.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(Authentication authentication) {
        userService.logout(authentication);
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestHeader("X-User-Id") String userId, @Valid @RequestBody ChangePasswordRequest changePasswordRequest) {
        String result = userService.changePassword(userId, changePasswordRequest);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        userService.forgotPassword(forgotPasswordRequest);
        return ResponseEntity.ok("If an account with that email exists, a password reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        userService.resetPassword(resetPasswordRequest);
        return ResponseEntity.ok("Password has been reset successfully.");
    }
}
