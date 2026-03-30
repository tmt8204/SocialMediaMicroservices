package com.socialmedia.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import com.socialmedia.auth.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        return buildToken(user, ACCESS_TOKEN_TYPE, accessTokenExpirationMs);
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, REFRESH_TOKEN_TYPE, refreshTokenExpirationMs);
    }

    private String buildToken(User user, String tokenType, long expirationMs) {
        String userId = user.getId();
        String username = user.getUsername();
        String email = user.getEmail();
        String role = user.getRole() != null ? user.getRole().getRoleName() : "USER";

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("user_name", username)
                .claim("email", email)
                .claim("role", role)
                .claim("token_type", tokenType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        Claims claims = extractClaims(token);
        String username = claims.get("user_name", String.class);
        return username != null && !username.isBlank() ? username : claims.getSubject();
    }

    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN_TYPE.equals(extractClaims(token).get("token_type", String.class));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN_TYPE.equals(extractClaims(token).get("token_type", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            return extractClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
