package com.socialmedia.social_media_chat_service.dto.request;

import com.socialmedia.social_media_chat_service.document.enums.MemberRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConversationMemberRoleRequest {
    private MemberRole role;
}