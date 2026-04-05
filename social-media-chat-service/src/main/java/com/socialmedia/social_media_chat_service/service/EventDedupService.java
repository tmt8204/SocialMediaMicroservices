package com.socialmedia.social_media_chat_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EventDedupService {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final Map<String, Instant> inMemoryProcessed = new ConcurrentHashMap<>();

    public boolean markIfNew(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return true;
        }

        cleanupExpired();
        return inMemoryProcessed.putIfAbsent(eventId, Instant.now().plus(DEFAULT_TTL)) == null;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        inMemoryProcessed.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
