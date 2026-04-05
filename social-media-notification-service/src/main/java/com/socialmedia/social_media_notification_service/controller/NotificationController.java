package com.socialmedia.social_media_notification_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_notification_service.dto.NotificationListResponse;
import com.socialmedia.social_media_notification_service.dto.NotificationReadResponse;
import com.socialmedia.social_media_notification_service.dto.UnreadCountResponse;
import com.socialmedia.social_media_notification_service.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<NotificationListResponse> getNotifications(
            @RequestHeader(USER_ID_HEADER) String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(notificationService.getNotifications(userId, cursor, limit, read, category));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(@RequestHeader(USER_ID_HEADER) String userId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationReadResponse> markAsRead(
            @RequestHeader(USER_ID_HEADER) String userId,
            @PathVariable String notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(userId, notificationId));
    }
}