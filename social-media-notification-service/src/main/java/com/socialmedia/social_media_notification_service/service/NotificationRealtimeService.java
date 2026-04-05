package com.socialmedia.social_media_notification_service.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.socialmedia.social_media_notification_service.config.SseEmitterRegistry;
import com.socialmedia.social_media_notification_service.dto.NotificationReadResponse;
import com.socialmedia.social_media_notification_service.dto.NotificationResponse;

@Service
public class NotificationRealtimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationRealtimeService.class);
    private static final long SSE_TIMEOUT_MS = 0L;

    private final SseEmitterRegistry emitterRegistry;

    public NotificationRealtimeService(SseEmitterRegistry emitterRegistry) {
        this.emitterRegistry = emitterRegistry;
    }

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitterRegistry.add(userId, emitter);
        LOGGER.info("sse subscribe userId={} emitterCount={}", userId, emitterRegistry.get(userId).size());
        emitter.onCompletion(() -> emitterRegistry.remove(userId, emitter));
        emitter.onTimeout(() -> emitterRegistry.remove(userId, emitter));
        emitter.onError(ex -> emitterRegistry.remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("stream.ready")
                    .data("connected"));
        } catch (IOException ex) {
            emitterRegistry.remove(userId, emitter);
            emitter.completeWithError(ex);
        }

        return emitter;
    }

    public void publishCreated(String userId, NotificationResponse payload) {
        broadcast(userId, "notification.created", payload);
    }

    public void publishRead(String userId, NotificationReadResponse payload) {
        broadcast(userId, "notification.read", payload);
    }

    private void broadcast(String userId, String eventName, Object payload) {
        int emitterCount = emitterRegistry.get(userId).size();
        LOGGER.info("sse broadcast userId={} eventName={} emitterCount={}", userId, eventName, emitterCount);
        for (SseEmitter emitter : emitterRegistry.get(userId)) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                LOGGER.warn("sse broadcast failed userId={} eventName={} message={}",
                        userId,
                        eventName,
                        ex.getMessage());
                emitterRegistry.remove(userId, emitter);
                emitter.completeWithError(ex);
            }
        }
    }
}