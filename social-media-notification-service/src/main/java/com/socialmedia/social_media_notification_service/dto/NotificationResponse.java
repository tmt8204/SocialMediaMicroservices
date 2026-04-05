package com.socialmedia.social_media_notification_service.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private String id;
    private String eventType;
    private String category;
    private String title;
    private String contentPreview;
    private NotificationActorResponse actor;
    private String deeplink;
    private String resourceType;
    private String resourceId;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}