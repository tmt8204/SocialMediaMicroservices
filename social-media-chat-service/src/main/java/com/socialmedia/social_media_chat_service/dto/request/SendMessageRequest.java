package com.socialmedia.social_media_chat_service.dto.request;

import com.socialmedia.social_media_chat_service.document.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    private String conversationId;
    private String clientMessageId;
    private MessageType messageType;
    
    @Size(max = 200, message = "Message content must not exceed 200 characters")
    private String content;

    private List<AttachmentDto> attachments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDto {
        private String publicId;
        private String mediaUrl;
        private String mediaType;
    }
}
