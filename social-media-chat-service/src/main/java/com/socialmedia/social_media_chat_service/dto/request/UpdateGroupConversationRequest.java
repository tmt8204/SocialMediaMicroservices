package com.socialmedia.social_media_chat_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupConversationRequest {
    private String groupName;
    private String groupAvatarUrl;
    private String groupDescription;
}