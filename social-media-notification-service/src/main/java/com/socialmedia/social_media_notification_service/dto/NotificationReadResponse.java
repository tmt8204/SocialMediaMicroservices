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
public class NotificationReadResponse {
    private String id;
    private boolean read;
    private Instant readAt;
}