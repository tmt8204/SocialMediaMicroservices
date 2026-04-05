package com.socialmedia.social_media_notification_service.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public void add(String userId, SseEmitter emitter) {
        emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);
    }

    public List<SseEmitter> get(String userId) {
        return emittersByUser.getOrDefault(userId, new CopyOnWriteArrayList<>());
    }

    public void remove(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId, emitters);
        }
    }
}