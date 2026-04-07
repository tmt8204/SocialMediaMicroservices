package com.socialmedia.auth.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.socialmedia.auth.dto.AuthResponse;
import com.socialmedia.auth.dto.ChangePasswordRequest;
import com.socialmedia.auth.dto.ForgotPasswordRequest;
import com.socialmedia.auth.dto.IssuedAuthTokens;
import com.socialmedia.auth.dto.LoginRequest;
import com.socialmedia.auth.dto.RefreshTokenRequest;
import com.socialmedia.auth.dto.RegisterRequest;
import com.socialmedia.auth.dto.ResetPasswordRequest;
import com.socialmedia.auth.security.RefreshTokenCookieService;
import com.socialmedia.auth.services.UserService;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    @Autowired
    private final UserService userService;

    private final RefreshTokenCookieService refreshTokenCookieService;

    public UserController(UserService userService, RefreshTokenCookieService refreshTokenCookieService) {
        this.userService = userService;
        this.refreshTokenCookieService = refreshTokenCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        IssuedAuthTokens tokens = userService.registerUser(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.buildRefreshTokenCookie(tokens.getRefreshToken()).toString())
                .body(tokens.getAuthResponse());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        IssuedAuthTokens tokens = userService.loginUser(loginRequest);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.buildRefreshTokenCookie(tokens.getRefreshToken()).toString())
                .body(tokens.getAuthResponse());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshTokenRequest refreshTokenRequest) {
        String refreshTokenCookie = extractCookieValue(request, refreshTokenCookieService.getCookieName());
        String refreshToken = refreshTokenCookie != null && !refreshTokenCookie.isBlank()
                ? refreshTokenCookie
                : refreshTokenRequest == null ? null : refreshTokenRequest.getRefreshToken();
        IssuedAuthTokens tokens = userService.refreshToken(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.buildRefreshTokenCookie(tokens.getRefreshToken()).toString())
                .body(tokens.getAuthResponse());
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletRequest request) {
        String refreshTokenCookie = extractCookieValue(request, refreshTokenCookieService.getCookieName());
        userService.logout(extractBearerToken(authorizationHeader), refreshTokenCookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clearRefreshTokenCookie().toString())
                .body("Logged out successfully");
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

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring(7).trim();
    }

    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
