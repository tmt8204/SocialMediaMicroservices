package com.socialmedia.social_media_social_service.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.socialmedia.social_media_social_service.event.SocialNotificationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialNotificationEventProducer {

    private static final String SOURCE_SERVICE = "social-service";

    private final KafkaTemplate<String, SocialNotificationEvent> kafkaTemplate;

    @Value("${social.kafka.topics.notification-events}")
    private String notificationTopic;

    public void publishPostReactionCreated(String actorId, String recipientId, Long postId) {
        SocialNotificationEvent event = SocialNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("SOCIAL_POST_REACTION_CREATED")
                .sourceService(SOURCE_SERVICE)
                .postId(postId)
                .actorId(actorId)
                .actorDisplayName(actorId)
                .recipientId(recipientId)
                .contentPreview("Da tha cam xuc bai viet cua ban")
                .deeplink("/posts/" + postId)
                .createdAt(Instant.now())
                .build();

        send(recipientId, event);
    }

    public void publishCommentCreated(String actorId, String recipientId, Long postId, Long commentId) {
        SocialNotificationEvent event = SocialNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("SOCIAL_COMMENT_CREATED")
                .sourceService(SOURCE_SERVICE)
                .postId(postId)
                .commentId(commentId)
                .actorId(actorId)
                .actorDisplayName(actorId)
                .recipientId(recipientId)
                .contentPreview("Da binh luan vao bai viet cua ban")
                .deeplink("/posts/" + postId)
                .createdAt(Instant.now())
                .build();

        send(recipientId, event);
    }

    public void publishFriendRequestCreated(String actorId, String recipientId) {
        SocialNotificationEvent event = SocialNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("SOCIAL_FRIEND_REQUEST_CREATED")
                .sourceService(SOURCE_SERVICE)
                .actorId(actorId)
                .actorDisplayName(actorId)
                .recipientId(recipientId)
                .contentPreview("Da gui loi moi ket ban cho ban")
                .deeplink("/friends/requests")
                .createdAt(Instant.now())
                .build();

        send(recipientId, event);
    }

    public void publishFriendRequestAccepted(String actorId, String recipientId) {
        SocialNotificationEvent event = SocialNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("SOCIAL_FRIEND_REQUEST_ACCEPTED")
                .sourceService(SOURCE_SERVICE)
                .actorId(actorId)
                .actorDisplayName(actorId)
                .recipientId(recipientId)
                .contentPreview("Da chap nhan loi moi ket ban cua ban")
                .deeplink("/friends")
                .createdAt(Instant.now())
                .build();

        send(recipientId, event);
    }

    public void publishPostCreated(String actorId, Long postId, String contentPreview, List<String> recipientIds) {
        for (String recipientId : recipientIds) {
            SocialNotificationEvent event = SocialNotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("SOCIAL_POST_CREATED")
                    .sourceService(SOURCE_SERVICE)
                    .postId(postId)
                    .actorId(actorId)
                    .actorDisplayName(actorId)
                    .recipientId(recipientId)
                    .contentPreview(contentPreview)
                    .deeplink("/posts/" + postId)
                    .createdAt(Instant.now())
                    .build();

            send(recipientId, event);
        }
    }

    private void send(String key, SocialNotificationEvent event) {
        kafkaTemplate.send(notificationTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for recipientId={}: {}",
                                event.getEventType(), event.getRecipientId(), ex.getMessage());
                    } else {
                        log.debug("Published {} for recipientId={} offset={}",
                                event.getEventType(), event.getRecipientId(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
