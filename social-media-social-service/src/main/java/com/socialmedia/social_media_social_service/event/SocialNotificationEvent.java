package com.socialmedia.social_media_social_service.event;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialNotificationEvent {

    private String eventId;
    private String eventType;
    private String sourceService;

    private Long postId;
    private Long commentId;

    private String actorId;
    private String actorDisplayName;
    private String recipientId;

    private String contentPreview;
    private String deeplink;

    private Instant createdAt;
}
