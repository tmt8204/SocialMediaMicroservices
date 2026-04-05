package com.socialmedia.social_media_chat_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberInfoResponse {
    private String conversationId;
    private String userId;
    private String role;
    private String lastReadMessageId;
    private Instant lastReadAt;
    private int unreadCount;
    private boolean muted;
    private boolean pinned;
    private boolean hidden;
}
