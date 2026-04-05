package com.socialmedia.social_media_chat_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PresenceService {

    private static final Duration ONLINE_TTL = Duration.ofMinutes(3);

    private final Map<String, Instant> onlineState = new ConcurrentHashMap<>();
    private final Map<String, Instant> typingState = new ConcurrentHashMap<>();

    public void setOnline(String userId, boolean online) {
        if (!StringUtils.hasText(userId)) {
            return;
        }

        if (online) {
            onlineState.put(userId, Instant.now().plus(ONLINE_TTL));
            return;
        }

        onlineState.remove(userId);
    }

    public boolean isOnline(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }

        Instant until = onlineState.get(userId);
        if (until == null) {
            return false;
        }

        boolean stillOnline = until.isAfter(Instant.now());
        if (!stillOnline) {
            onlineState.remove(userId);
        }
        return stillOnline;
    }

    public void setTyping(String conversationId, String userId, boolean typing) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(userId)) {
            return;
        }

        String key = typingKey(conversationId, userId);
        if (typing) {
            typingState.put(key, Instant.now().plus(Duration.ofSeconds(10)));
        } else {
            typingState.remove(key);
        }
    }

    private String typingKey(String conversationId, String userId) {
        return "typing:" + conversationId + ":" + userId;
    }
}
