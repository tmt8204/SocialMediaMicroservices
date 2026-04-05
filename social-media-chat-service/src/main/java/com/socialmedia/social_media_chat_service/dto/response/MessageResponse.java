package com.socialmedia.social_media_chat_service.dto.response;

import com.socialmedia.social_media_chat_service.document.enums.MessageStatus;
import com.socialmedia.social_media_chat_service.document.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private String id;
    private String conversationId;
    private String senderId;
    private long seqNo;
    private MessageType messageType;
    private String content;
    private List<AttachmentDto> attachments;
    private String replyToMessageId;
    private MessageStatus status;
    private boolean edited;
    private boolean deletedForEveryone;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDto {
        private String publicId;
        private String mediaUrl;
        private String mediaType;
    }
}
