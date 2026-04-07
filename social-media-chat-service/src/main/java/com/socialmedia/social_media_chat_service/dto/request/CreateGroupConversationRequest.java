package com.socialmedia.social_media_chat_service.dto.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupConversationRequest {
    private String groupName;
    private String groupAvatarUrl;
    private String groupDescription;
    private List<String> memberUserIds;
}