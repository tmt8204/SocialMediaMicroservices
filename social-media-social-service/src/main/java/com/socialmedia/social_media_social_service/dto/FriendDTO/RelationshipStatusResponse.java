package com.socialmedia.social_media_social_service.dto.FriendDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipStatusResponse {

    private String userId;

    private String targetUserId;

    private String status;
}
