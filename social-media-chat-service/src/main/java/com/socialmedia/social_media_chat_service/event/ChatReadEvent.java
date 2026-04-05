package com.socialmedia.social_media_chat_service.event;

import java.time.Instant;
import java.util.List;

public record ChatReadEvent(
        String eventId,
        String eventType,
        String originInstanceId,
        String conversationId,
        String messageId,
        String userId,
        List<String> recipientIds,
        Instant createdAt) {
}
