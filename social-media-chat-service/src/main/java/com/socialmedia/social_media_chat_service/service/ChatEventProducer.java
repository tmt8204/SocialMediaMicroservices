package com.socialmedia.social_media_chat_service.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.socialmedia.social_media_chat_service.config.ChatKafkaProperties;
import com.socialmedia.social_media_chat_service.dto.response.MessageResponse;
import com.socialmedia.social_media_chat_service.event.ChatMessageEvent;
import com.socialmedia.social_media_chat_service.event.ChatNotificationEvent;
import com.socialmedia.social_media_chat_service.event.ChatReadEvent;

@Service
public class ChatEventProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatEventProducer.class);

    private final ChatKafkaProperties kafkaProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PresenceService presenceService;

    public ChatEventProducer(ChatKafkaProperties kafkaProperties,
            KafkaTemplate<String, Object> kafkaTemplate,
            PresenceService presenceService) {
        this.kafkaProperties = kafkaProperties;
        this.kafkaTemplate = kafkaTemplate;
        this.presenceService = presenceService;
    }

    public void publishMessageCreated(MessageResponse message, List<String> recipients) {
        String eventId = UUID.randomUUID().toString();
        String contentPreview = buildPreview(message);

        LOGGER.info("publish message eventId={} conversationId={} senderId={} recipients={}",
            eventId,
            message.getConversationId(),
            message.getSenderId(),
            recipients);

        ChatMessageEvent messageEvent = new ChatMessageEvent(
                eventId,
                "MESSAGE_CREATED",
                kafkaProperties.getInstanceId(),
                message.getConversationId(),
                message.getId(),
                message.getSenderId(),
                message.getMessageType() == null ? "TEXT" : message.getMessageType().name(),
                contentPreview,
                recipients,
                Instant.now());

        kafkaTemplate.send(kafkaProperties.getTopics().getMessageEvents(), message.getConversationId(), messageEvent);

        for (String recipientId : recipients) {
            if (recipientId.equals(message.getSenderId())) {
                continue;
            }

            ChatNotificationEvent notificationEvent = new ChatNotificationEvent(
                    eventId + "_" + recipientId,
                    "CHAT_MESSAGE_CREATED",
                    message.getConversationId(),
                    message.getId(),
                    message.getSenderId(),
                    message.getSenderId(),
                    recipientId,
                    presenceService.isOnline(recipientId),
                    message.getMessageType() == null ? "TEXT" : message.getMessageType().name(),
                    contentPreview,
                    Instant.now());

            LOGGER.info("publish chat-notification eventId={} recipientId={} conversationId={} recipientOnline={}",
                    notificationEvent.eventId(),
                    recipientId,
                    message.getConversationId(),
                    notificationEvent.recipientOnline());

            kafkaTemplate.send(kafkaProperties.getTopics().getNotificationEvents(), recipientId, notificationEvent);
        }
    }

    public void publishMessageRead(String conversationId, String messageId, String userId, List<String> recipients) {
        ChatReadEvent readEvent = new ChatReadEvent(
                UUID.randomUUID().toString(),
                "MESSAGE_READ",
                kafkaProperties.getInstanceId(),
                conversationId,
                messageId,
                userId,
                recipients,
                Instant.now());

        kafkaTemplate.send(kafkaProperties.getTopics().getReadEvents(), conversationId, readEvent);
    }

    public void publishMessageRecalled(MessageResponse message, List<String> recipients) {
        ChatMessageEvent messageEvent = new ChatMessageEvent(
                UUID.randomUUID().toString(),
                "MESSAGE_RECALLED",
                kafkaProperties.getInstanceId(),
                message.getConversationId(),
                message.getId(),
                message.getSenderId(),
                message.getMessageType() == null ? "SYSTEM" : message.getMessageType().name(),
                "This message was recalled",
                recipients,
                Instant.now());

        kafkaTemplate.send(kafkaProperties.getTopics().getMessageEvents(), message.getConversationId(), messageEvent);
    }

    public void publishConversationCreated(String conversationId, String createdBy, List<String> participants) {
        ChatMessageEvent event = new ChatMessageEvent(
                UUID.randomUUID().toString(),
                "CONVERSATION_CREATED",
                kafkaProperties.getInstanceId(),
                conversationId,
                null,
                createdBy,
                "SYSTEM",
                "Conversation created",
                participants,
                Instant.now());

        kafkaTemplate.send(kafkaProperties.getTopics().getMessageEvents(), conversationId, event);
    }

    private String buildPreview(MessageResponse message) {
        if (message.isDeletedForEveryone()) {
            return "This message was recalled";
        }

        if (message.getContent() != null && !message.getContent().isBlank()) {
            return message.getContent().length() > 120 ? message.getContent().substring(0, 120) : message.getContent();
        }

        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            return "[Attachment]";
        }

        return "";
    }
}
