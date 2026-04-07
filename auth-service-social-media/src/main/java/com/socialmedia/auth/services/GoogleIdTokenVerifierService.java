package com.socialmedia.auth.services;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialmedia.auth.dto.GoogleUserProfile;
import com.socialmedia.auth.exceptions.BadRequestException;
import com.socialmedia.auth.exceptions.UnauthorizedException;

@Service
public class GoogleIdTokenVerifierService {

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> GOOGLE_ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

    private final String googleClientId;
    private final JwtDecoder jwtDecoder;

    @Autowired
    public GoogleIdTokenVerifierService(@Value("${app.auth.google.client-id:}") String googleClientId) {
        this(googleClientId, NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build());
    }

    GoogleIdTokenVerifierService(String googleClientId, JwtDecoder jwtDecoder) {
        this.googleClientId = googleClientId;
        this.jwtDecoder = jwtDecoder;
    }

    public GoogleUserProfile verify(String idToken) {
        if (!StringUtils.hasText(googleClientId)) {
            throw new BadRequestException("Google login is not configured");
        }
        if (!StringUtils.hasText(idToken)) {
            throw new BadRequestException("Google ID token is required");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException ex) {
            throw new UnauthorizedException("Invalid Google ID token");
        }

        validateIssuer(jwt);
        validateAudience(jwt);
        validateEmailVerified(jwt);

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(email)) {
            throw new UnauthorizedException("Google account is missing required claims");
        }

        String fullName = jwt.getClaimAsString("name");
        if (!StringUtils.hasText(fullName)) {
            fullName = email.substring(0, email.indexOf('@'));
        }

        return new GoogleUserProfile(subject, email, fullName);
    }

    private void validateIssuer(Jwt jwt) {
        String issuerValue = Objects.toString(jwt.getClaims().get("iss"), null);
        if (!StringUtils.hasText(issuerValue) || !GOOGLE_ISSUERS.contains(issuerValue)) {
            throw new UnauthorizedException("Invalid Google token issuer");
        }
    }

    private void validateAudience(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (audience == null || audience.stream().noneMatch(googleClientId::equals)) {
            throw new UnauthorizedException("Google token audience is not allowed");
        }
    }

    private void validateEmailVerified(Jwt jwt) {
        Object emailVerified = jwt.getClaims().get("email_verified");
        boolean verified = Boolean.TRUE.equals(emailVerified)
                || (emailVerified instanceof String value && Boolean.parseBoolean(value));
        if (!verified) {
            throw new UnauthorizedException("Google account email is not verified");
        }
    }
}