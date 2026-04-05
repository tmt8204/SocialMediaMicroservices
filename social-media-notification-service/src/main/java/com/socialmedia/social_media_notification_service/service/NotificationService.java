package com.socialmedia.social_media_notification_service.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_notification_service.document.NotificationDocument;
import com.socialmedia.social_media_notification_service.dto.NotificationActorResponse;
import com.socialmedia.social_media_notification_service.dto.NotificationListResponse;
import com.socialmedia.social_media_notification_service.dto.NotificationReadResponse;
import com.socialmedia.social_media_notification_service.dto.NotificationResponse;
import com.socialmedia.social_media_notification_service.dto.UnreadCountResponse;
import com.socialmedia.social_media_notification_service.exception.BadRequestException;
import com.socialmedia.social_media_notification_service.exception.NotFoundException;
import com.socialmedia.social_media_notification_service.repository.NotificationRepository;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final NotificationRepository notificationRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationRealtimeService notificationRealtimeService;

    public NotificationService(NotificationRepository notificationRepository,
            MongoTemplate mongoTemplate,
            NotificationRealtimeService notificationRealtimeService) {
        this.notificationRepository = notificationRepository;
        this.mongoTemplate = mongoTemplate;
        this.notificationRealtimeService = notificationRealtimeService;
    }

    public NotificationListResponse getNotifications(String recipientId, String cursor, Integer limit, Boolean read, String category) {
        int safeLimit = normalizeLimit(limit);
        Query query = new Query();
        query.addCriteria(Criteria.where("recipientId").is(recipientId));

        if (read != null) {
            query.addCriteria(Criteria.where("read").is(read));
        }

        if (StringUtils.hasText(category)) {
            query.addCriteria(Criteria.where("category").is(category.trim()));
        }

        if (StringUtils.hasText(cursor)) {
            CursorToken token = parseCursor(cursor);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("createdAt").lt(token.createdAt()),
                    new Criteria().andOperator(
                            Criteria.where("createdAt").is(token.createdAt()),
                            Criteria.where("id").lt(token.id()))));
        }

        query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        query.limit(safeLimit + 1);

        List<NotificationDocument> results = mongoTemplate.find(query, NotificationDocument.class);
        boolean hasMore = results.size() > safeLimit;
        List<NotificationDocument> pageItems = hasMore ? results.subList(0, safeLimit) : results;
        String nextCursor = hasMore && !pageItems.isEmpty() ? buildCursor(pageItems.get(pageItems.size() - 1)) : null;

        return NotificationListResponse.builder()
                .items(pageItems.stream().map(this::toResponse).toList())
                .nextCursor(nextCursor)
                .build();
    }

    public UnreadCountResponse getUnreadCount(String recipientId) {
        return UnreadCountResponse.builder()
                .unreadCount(notificationRepository.countByRecipientIdAndReadFalse(recipientId))
                .build();
    }

    public NotificationReadResponse markAsRead(String recipientId, String notificationId) {
        NotificationDocument notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        boolean stateChanged = false;
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
            stateChanged = true;
        }

        NotificationReadResponse response = NotificationReadResponse.builder()
                .id(notification.getId())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .build();

        if (stateChanged) {
            notificationRealtimeService.publishRead(recipientId, response);
        }

        return response;
    }

    public void ingestNotification(NotificationDocument notification) {
        if (notification == null || !StringUtils.hasText(notification.getSourceEventId())) {
            throw new BadRequestException("sourceEventId is required");
        }

        if (notificationRepository.existsBySourceEventId(notification.getSourceEventId())) {
            LOGGER.info("skip duplicate notification sourceEventId={}", notification.getSourceEventId());
            return;
        }

        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(Instant.now());
        }

        try {
            LOGGER.info("persist notification sourceEventId={} recipientId={} eventType={}",
                    notification.getSourceEventId(),
                    notification.getRecipientId(),
                    notification.getEventType());
            NotificationDocument savedNotification = notificationRepository.save(notification);
            LOGGER.info("persisted notification id={} sourceEventId={} recipientId={}",
                    savedNotification.getId(),
                    savedNotification.getSourceEventId(),
                    savedNotification.getRecipientId());

            try {
                notificationRealtimeService.publishCreated(savedNotification.getRecipientId(), toResponse(savedNotification));
            } catch (Exception ex) {
                LOGGER.warn("realtime publish failed notificationId={} recipientId={} message={}",
                        savedNotification.getId(),
                        savedNotification.getRecipientId(),
                        ex.getMessage());
            }
        } catch (DuplicateKeyException ex) {
            LOGGER.info("duplicate notification sourceEventId={} ignored", notification.getSourceEventId());
        } catch (Exception ex) {
            LOGGER.error("persist notification failed sourceEventId={} recipientId={} message={}",
                    notification.getSourceEventId(),
                    notification.getRecipientId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    public NotificationResponse toResponse(NotificationDocument notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .eventType(notification.getEventType())
                .category(notification.getCategory())
                .title(notification.getTitle())
                .contentPreview(notification.getContentPreview())
                .actor(NotificationActorResponse.builder()
                        .id(notification.getActorId())
                        .displayName(notification.getActorDisplayName())
                        .build())
                .deeplink(notification.getDeeplink())
                .resourceType(notification.getResourceType())
                .resourceId(notification.getResourceId())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        if (limit < 1) {
            throw new BadRequestException("limit must be greater than 0");
        }

        return Math.min(limit, MAX_LIMIT);
    }

    private String buildCursor(NotificationDocument notification) {
        Instant createdAt = notification.getCreatedAt();
        if (createdAt == null || !StringUtils.hasText(notification.getId())) {
            return null;
        }

        String raw = createdAt.toString() + "|" + notification.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private CursorToken parseCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2 || !StringUtils.hasText(parts[1])) {
                throw new BadRequestException("Invalid cursor");
            }
            return new CursorToken(Instant.parse(parts[0]), parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid cursor");
        }
    }

    private record CursorToken(Instant createdAt, String id) {
    }
}