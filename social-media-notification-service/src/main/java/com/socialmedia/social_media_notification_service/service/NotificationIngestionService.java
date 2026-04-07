package com.socialmedia.social_media_notification_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmedia.social_media_notification_service.exception.BadRequestException;

@Service
public class NotificationIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationIngestionService.class);

    private final ObjectMapper objectMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;

    public NotificationIngestionService(ObjectMapper objectMapper,
            NotificationMapper notificationMapper,
            NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
    }

    public void processChatEvent(String payload) {
        JsonNode event = parse(payload);
        try {
            LOGGER.info("consume chat notification eventId={} recipientId={} eventType={}",
                textOrUnknown(event, "eventId"),
                textOrUnknown(event, "recipientId"),
                textOrUnknown(event, "eventType"));
            notificationMapper.fromChatEvent(event)
                .ifPresentOrElse(notificationService::ingestNotification,
                    () -> logSkipped(event, "chat"));
        } catch (Exception ex) {
            LOGGER.error("process chat notification failed eventId={} recipientId={} message={}",
                textOrUnknown(event, "eventId"),
                textOrUnknown(event, "recipientId"),
                ex.getMessage(),
                ex);
            throw ex;
        }
    }

    public void processSocialEvent(String payload) {
        JsonNode event = parse(payload);
        try {
            LOGGER.info("consume social notification eventId={} recipientId={} eventType={}",
                textOrUnknown(event, "eventId"),
                textOrUnknown(event, "recipientId"),
                textOrUnknown(event, "eventType"));
            notificationMapper.fromSocialEvent(event)
                .ifPresentOrElse(notificationService::ingestNotification,
                    () -> logSkipped(event, "social"));
        } catch (Exception ex) {
            LOGGER.error("process social notification failed eventId={} recipientId={} message={}",
                textOrUnknown(event, "eventId"),
                textOrUnknown(event, "recipientId"),
                ex.getMessage(),
                ex);
            throw ex;
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid notification event payload");
        }
    }

    private void logSkipped(JsonNode event, String source) {
        String eventId = event.hasNonNull("eventId") ? event.get("eventId").asText() : "unknown";
        LOGGER.info("Skipped {} notification eventId={} due to business rules", source, eventId);
    }

    private String textOrUnknown(JsonNode event, String fieldName) {
        return event.hasNonNull(fieldName) ? event.get(fieldName).asText() : "unknown";
    }
}