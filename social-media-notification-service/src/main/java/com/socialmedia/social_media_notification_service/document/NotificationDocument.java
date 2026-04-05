package com.socialmedia.social_media_notification_service.document;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndexes({
    @CompoundIndex(name = "idx_recipient_created", def = "{'recipientId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "idx_recipient_read_created", def = "{'recipientId': 1, 'read': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "idx_recipient_category_created", def = "{'recipientId': 1, 'category': 1, 'createdAt': -1}")
})
public class NotificationDocument {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String sourceEventId;

    private String eventType;

    private String sourceService;

    private String category;

    @Indexed
    private String recipientId;

    private String actorId;

    private String actorDisplayName;

    private String title;

    private String contentPreview;

    private String resourceType;

    private String resourceId;

    private String secondaryResourceId;

    private String deeplink;

    @Builder.Default
    private boolean read = false;

    private Instant readAt;

    @Builder.Default
    private boolean deliveredRealtime = false;

    private Instant deliveredAt;

    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}