package com.socialmedia.social_media_chat_service.event;

import java.time.Instant;

public record ChatNotificationEvent(
        String eventId,
        String eventType,
        String conversationId,
        String messageId,
        String senderId,
        String senderDisplayName,
        String recipientId,
        boolean recipientOnline,
        String messageType,
        String contentPreview,
        Instant createdAt) {
}
