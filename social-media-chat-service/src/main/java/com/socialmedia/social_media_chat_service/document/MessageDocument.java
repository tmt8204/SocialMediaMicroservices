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

import com.socialmedia.social_media_chat_service.document.enums.MessageStatus;
import com.socialmedia.social_media_chat_service.document.enums.MessageType;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_conv_seq", def = "{'conversationId': 1, 'seqNo': -1}"),
    @CompoundIndex(name = "idx_conv_created", def = "{'conversationId': 1, 'createdAt': -1}")
})
public class MessageDocument {

    @Id
    private String id;

    private String conversationId;

    @Indexed
    private String senderId;

    private long seqNo;

    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    private String content;

    private List<AttachmentInfo> attachments;

    private String replyToMessageId;

    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    @Builder.Default
    private boolean edited = false;

    @Builder.Default
    private boolean deletedForEveryone = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentInfo {
        private String publicId;
        private String mediaUrl;
        private String mediaType;
    }
}
