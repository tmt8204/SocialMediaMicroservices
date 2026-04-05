package com.socialmedia.social_media_notification_service.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialmedia.social_media_notification_service.document.NotificationDocument;
import com.socialmedia.social_media_notification_service.exception.BadRequestException;

@Service
public class NotificationMapper {

    public Optional<NotificationDocument> fromChatEvent(JsonNode event) {
        String eventId = requiredText(event, "eventId");
        String eventType = requiredText(event, "eventType");
        String recipientId = requiredText(event, "recipientId");
        String actorId = firstText(event, "senderId", "actorId");

        if (StringUtils.hasText(actorId) && actorId.equals(recipientId)) {
            return Optional.empty();
        }

        String conversationId = firstText(event, "conversationId", "resourceId");
        String deeplink = firstText(event, "deeplink");
        if (!StringUtils.hasText(deeplink) && StringUtils.hasText(conversationId)) {
            deeplink = "/chat/" + conversationId;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "messageType", firstText(event, "messageType"));
        if (event.has("recipientOnline") && !event.get("recipientOnline").isNull()) {
            metadata.put("recipientOnline", event.get("recipientOnline").asBoolean());
        }

        return Optional.of(NotificationDocument.builder()
                .sourceEventId(eventId)
                .eventType(eventType)
                .sourceService(firstText(event, "sourceService", "service", "producer", "originService") == null
                        ? "chat-service"
                        : firstText(event, "sourceService", "service", "producer", "originService"))
                .category("CHAT")
                .recipientId(recipientId)
                .actorId(actorId)
                .actorDisplayName(firstNonBlank(firstText(event, "senderDisplayName", "actorDisplayName"), actorId))
                .title(resolveChatTitle(eventType))
                .contentPreview(firstText(event, "contentPreview"))
                .resourceType("CONVERSATION")
                .resourceId(conversationId)
                .secondaryResourceId(firstText(event, "messageId", "secondaryResourceId"))
                .deeplink(deeplink)
                .metadata(metadata)
                .createdAt(resolveCreatedAt(event))
                .build());
    }

    public Optional<NotificationDocument> fromSocialEvent(JsonNode event) {
        String eventId = requiredText(event, "eventId");
        String eventType = requiredText(event, "eventType");
        String recipientId = requiredText(event, "recipientId");
        String actorId = firstText(event, "actorId", "senderId");

        if (StringUtils.hasText(actorId) && actorId.equals(recipientId)) {
            return Optional.empty();
        }

        SocialMapping mapping = resolveSocialMapping(eventType, event);
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "postId", firstText(event, "postId"));
        putIfPresent(metadata, "commentId", firstText(event, "commentId"));

        return Optional.of(NotificationDocument.builder()
                .sourceEventId(eventId)
                .eventType(eventType)
                .sourceService(firstText(event, "sourceService", "service", "producer", "originService") == null
                        ? "social-service"
                        : firstText(event, "sourceService", "service", "producer", "originService"))
                .category("SOCIAL")
                .recipientId(recipientId)
                .actorId(actorId)
                .actorDisplayName(firstNonBlank(firstText(event, "actorDisplayName", "senderDisplayName"), actorId))
                .title(mapping.title())
                .contentPreview(firstText(event, "contentPreview"))
                .resourceType(mapping.resourceType())
                .resourceId(mapping.resourceId())
                .deeplink(mapping.deeplink())
                .metadata(metadata)
                .createdAt(resolveCreatedAt(event))
                .build());
    }

    private SocialMapping resolveSocialMapping(String eventType, JsonNode event) {
        String postId = firstText(event, "postId");
        String commentId = firstText(event, "commentId");

        return switch (eventType) {
            case "SOCIAL_POST_REACTION_CREATED" -> new SocialMapping(
                    "Co nguoi da tha cam xuc bai viet cua ban",
                    "POST",
                    requiredValue(postId, "postId is required for SOCIAL_POST_REACTION_CREATED"),
                    buildPostsDeeplink(postId, firstText(event, "deeplink")));
            case "SOCIAL_COMMENT_CREATED" -> new SocialMapping(
                    "Co binh luan moi",
                    "COMMENT",
                    requiredValue(commentId, "commentId is required for SOCIAL_COMMENT_CREATED"),
                    buildPostsDeeplink(postId, firstText(event, "deeplink")));
            case "SOCIAL_COMMENT_REACTION_CREATED" -> new SocialMapping(
                    "Co nguoi da tha cam xuc binh luan cua ban",
                    "COMMENT",
                    requiredValue(commentId, "commentId is required for SOCIAL_COMMENT_REACTION_CREATED"),
                    buildPostsDeeplink(postId, firstText(event, "deeplink")));
            default -> throw new BadRequestException("Unsupported social eventType: " + eventType);
        };
    }

    private String resolveChatTitle(String eventType) {
        return switch (eventType) {
            case "CHAT_MESSAGE_CREATED" -> "Tin nhan moi";
            default -> throw new BadRequestException("Unsupported chat eventType: " + eventType);
        };
    }

    private Instant resolveCreatedAt(JsonNode event) {
        JsonNode createdAtNode = event.get("createdAt");
        if (createdAtNode == null || createdAtNode.isNull()) {
            return Instant.now();
        }

        try {
            return parseInstant(createdAtNode);
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            throw new BadRequestException("Invalid createdAt format");
        }
    }

    private Instant parseInstant(JsonNode node) {
        if (node.isTextual()) {
            return Instant.parse(node.asText().trim());
        }

        if (node.isNumber()) {
            long rawValue = node.asLong();
            return String.valueOf(Math.abs(rawValue)).length() > 10
                    ? Instant.ofEpochMilli(rawValue)
                    : Instant.ofEpochSecond(rawValue);
        }

        if (node.isObject()) {
            JsonNode epochSecond = firstNode(node, "epochSecond", "seconds");
            JsonNode nano = firstNode(node, "nano", "nanos");
            if (epochSecond != null && epochSecond.isNumber()) {
                long seconds = epochSecond.asLong();
                int nanos = nano != null && nano.isNumber() ? nano.asInt() : 0;
                return Instant.ofEpochSecond(seconds, nanos);
            }

            JsonNode epochMilli = firstNode(node, "epochMilli", "millis", "timestamp");
            if (epochMilli != null && epochMilli.isNumber()) {
                return Instant.ofEpochMilli(epochMilli.asLong());
            }

            JsonNode isoValue = firstNode(node, "value", "iso", "dateTime");
            if (isoValue != null && isoValue.isTextual()) {
                return Instant.parse(isoValue.asText().trim());
            }
        }

        throw new IllegalArgumentException("Unsupported createdAt payload");
    }

    private JsonNode firstNode(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode child = node.get(fieldName);
            if (child != null && !child.isNull()) {
                return child;
            }
        }

        return null;
    }

    private String requiredText(JsonNode event, String fieldName) {
        String value = firstText(event, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }

    private String firstText(JsonNode event, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = event.get(fieldName);
            if (node == null || node.isNull()) {
                continue;
            }

            String value = node.isTextual() ? node.asText() : node.toString();
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private String buildPostsDeeplink(String postId, String deeplink) {
        if (StringUtils.hasText(deeplink)) {
            return deeplink;
        }

        return StringUtils.hasText(postId) ? "/posts/" + postId : null;
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value);
        }
    }

    private String requiredValue(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private record SocialMapping(String title, String resourceType, String resourceId, String deeplink) {
    }
}