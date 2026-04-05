package com.socialmedia.social_media_notification_service.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.socialmedia.social_media_notification_service.service.NotificationRealtimeService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationStreamController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final NotificationRealtimeService notificationRealtimeService;

    public NotificationStreamController(NotificationRealtimeService notificationRealtimeService) {
        this.notificationRealtimeService = notificationRealtimeService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(USER_ID_HEADER) String userId) {
        return notificationRealtimeService.subscribe(userId);
    }
}