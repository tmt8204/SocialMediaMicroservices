package com.socialmedia.social_media_api_gateway.security;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.socialmedia.social_media_api_gateway.config.GatewayAuthProperties;
import com.socialmedia.social_media_api_gateway.persistence.TokenType;
import com.socialmedia.social_media_api_gateway.persistence.UserTokenRepository;

import reactor.core.publisher.Mono;

@Service
public class TokenRevocationService {

    private final GatewayAuthProperties properties;
    private final UserTokenRepository userTokenRepository;
    private final WebClient webClient;

    public TokenRevocationService(GatewayAuthProperties properties,
                                  UserTokenRepository userTokenRepository,
                                  WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.userTokenRepository = userTokenRepository;
        this.webClient = webClientBuilder.build();
    }

    public Mono<Boolean> isTokenActive(String token) {
        GatewayAuthProperties.RevocationCheck config = properties.getRevocationCheck();
        if (!config.isEnabled()) {
            return Mono.just(true);
        }

        String mode = config.getMode() == null ? "mongo" : config.getMode().trim().toLowerCase();
        return switch (mode) {
            case "none" -> Mono.just(true);
            case "auth-service" -> isActiveViaAuthService(token, config);
            case "mongo" -> isActiveViaMongo(token, config);
            default -> Mono.just(false);
        };
    }

    private Mono<Boolean> isActiveViaMongo(String token, GatewayAuthProperties.RevocationCheck config) {
        TokenType tokenType;
        try {
            tokenType = TokenType.valueOf(config.getTokenType().toUpperCase());
        } catch (Exception ex) {
            return Mono.just(false);
        }

        return userTokenRepository
                .findByTokenAndTokenTypeAndRevokedFalse(token, tokenType)
                .map(doc -> doc.getExpiresAt() == null || doc.getExpiresAt().isAfter(java.time.Instant.now()))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    private Mono<Boolean> isActiveViaAuthService(String token, GatewayAuthProperties.RevocationCheck config) {
        String introspectionUrl = config.getAuthService().getIntrospectionUrl();
        if (!StringUtils.hasText(introspectionUrl)) {
            return Mono.just(false);
        }

        Duration timeout = Duration.ofMillis(Math.max(config.getAuthService().getReadTimeoutMs(), 500));

        return webClient
                .post()
                .uri(introspectionUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", token, "tokenType", config.getTokenType()))
                .retrieve()
                .bodyToMono(TokenIntrospectionResponse.class)
                .map(response -> response != null && response.active())
                .timeout(timeout)
                .onErrorReturn(false);
    }

    private record TokenIntrospectionResponse(boolean active) {
    }
}
