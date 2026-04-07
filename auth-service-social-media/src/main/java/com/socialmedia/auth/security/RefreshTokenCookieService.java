package com.socialmedia.auth.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenCookieService {

    @Value("${app.auth.refresh-cookie.name:refresh_token}")
    private String cookieName;

    @Value("${app.auth.refresh-cookie.path:/api/auth}")
    private String cookiePath;

    @Value("${app.auth.refresh-cookie.secure:false}")
    private boolean secure;

    @Value("${app.auth.refresh-cookie.same-site:Lax}")
    private String sameSite;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    public String getCookieName() {
        return cookieName;
    }

    public ResponseCookie buildRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(Duration.ZERO)
                .build();
    }
}