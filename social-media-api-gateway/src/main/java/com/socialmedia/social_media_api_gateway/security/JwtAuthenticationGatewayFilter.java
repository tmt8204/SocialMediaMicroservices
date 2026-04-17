package com.socialmedia.social_media_api_gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.socialmedia.social_media_api_gateway.config.GatewayAuthProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayAuthProperties authProperties;
    private final JwtTokenService jwtTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationGatewayFilter(GatewayAuthProperties authProperties,
                                          JwtTokenService jwtTokenService,
                                          TokenRevocationService tokenRevocationService) {
        this.authProperties = authProperties;
        this.jwtTokenService = jwtTokenService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        Claims claims;
        try {
            claims = jwtTokenService.validateAndParseAccessToken(token);
        } catch (JwtException ex) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(role) ) {
            return unauthorized(exchange, "Missing required token claims");
        }

        // Role enforcement for admin paths
        if (path.startsWith("/api/admin")) {
            List<String> adminRoles = authProperties.getAdminRoles();
            if (adminRoles == null || !adminRoles.contains(role)) {
                return forbidden(exchange, "Insufficient permissions to access admin resources");
            }
        }

        return tokenRevocationService.isTokenActive(token)
                .flatMap(isActive -> {
                    if (!isActive) {
                        return unauthorized(exchange, "Token revoked or unknown");
                    }

                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .headers(headers -> {
                                headers.set("X-User-Id", userId);
                                headers.set("X-Role", role);
                            })
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublicPath(String requestPath) {
        List<String> publicPaths = authProperties.getPublicPaths();
        for (String pattern : publicPaths) {
            if (pathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"error\":\"unauthorized\",\"message\":\"" + message + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"error\":\"forbidden\",\"message\":\"" + message + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
