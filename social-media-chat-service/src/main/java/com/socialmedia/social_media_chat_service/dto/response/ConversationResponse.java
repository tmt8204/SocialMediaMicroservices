package com.socialmedia.social_media_chat_service.dto.response;

import com.socialmedia.social_media_chat_service.document.enums.ConversationType;
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
public class ConversationResponse {
    private String conversationId;
    private ConversationType type;
    private List<String> participantIds;
    private String lastMessageId;
    private String lastMessagePreview;
    private String lastMessageSenderId;
    private Instant lastMessageAt;
    private Instant createdAt;
}
