package com.socialmedia.social_media_chat_service.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.socialmedia.social_media_chat_service.document.enums.ConversationType;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(name = "idx_last_message_at", def = "{'lastMessageAt': -1}")
})
public class ConversationDocument {

    @Id
    private String id;

    private ConversationType type;

    /** Unique key for DIRECT conversations: min(userA, userB) + ":" + max(userA, userB) */
    @Indexed(unique = true, sparse = true)
    private String directKey;

    private String createdBy;

    private String groupName;

    private String groupAvatarUrl;

    private String groupDescription;

    @Indexed
    private List<String> participantIds;

    private String lastMessageId;
    private String lastMessagePreview;
    private String lastMessageSenderId;
    private Instant lastMessageAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Builder.Default
    private boolean active = true;
}
