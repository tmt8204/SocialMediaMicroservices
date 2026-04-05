package com.socialmedia.social_media_chat_service.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtTokenService {

    private static final String ACCESS_TOKEN_TYPE = "ACCESS";

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return ACCESS_TOKEN_TYPE.equals(claims.get("token_type", String.class));
    }

    public boolean isExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null || expiration.before(new Date());
    }

    public Claims validateAndParseAccessToken(String token) {
        try {
            Claims claims = extractClaims(token);
            if (isExpired(claims) || !isAccessToken(claims)) {
                throw new JwtException("Invalid token type or expiration");
            }
            return claims;
        } catch (Exception ex) {
            throw new JwtException("Invalid JWT", ex);
        }
    }
}
