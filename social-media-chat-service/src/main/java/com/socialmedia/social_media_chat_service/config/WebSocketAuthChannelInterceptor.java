package com.socialmedia.social_media_chat_service.config;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_chat_service.security.JwtTokenService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public WebSocketAuthChannelInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String token = resolveToken(accessor);
        if (!StringUtils.hasText(token)) {
            throw new JwtException("Missing Authorization token in CONNECT frame");
        }

        Claims claims = jwtTokenService.validateAndParseAccessToken(token);
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        if (!StringUtils.hasText(userId)) {
            throw new JwtException("Invalid token claims");
        }

        accessor.setUser(new ChatPrincipal(userId, role));
        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authHeaders == null || authHeaders.isEmpty()) {
            authHeaders = accessor.getNativeHeader("authorization");
        }
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
                return header.substring(BEARER_PREFIX.length()).trim();
            }
        }

        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        Object sessionAttributesObject = accessor.getSessionAttributes();
        if (sessionAttributesObject instanceof Map<?, ?> sessionAttributes) {
            Object value = sessionAttributes.get("token");
            if (value instanceof String token && StringUtils.hasText(token)) {
                return token;
            }
        }

        return null;
    }

    private static final class ChatPrincipal implements Principal {

        private final String userId;
        @SuppressWarnings("unused")
        private final String role;

        private ChatPrincipal(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }

        @Override
        public String getName() {
            return userId;
        }
    }
}
