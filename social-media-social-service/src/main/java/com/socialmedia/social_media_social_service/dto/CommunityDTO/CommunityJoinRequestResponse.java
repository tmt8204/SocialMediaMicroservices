package com.socialmedia.social_media_social_service.dto.CommunityDTO;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityJoinRequestResponse {

    private Long communityId;

    private String userId;

    private String status;

    private Date requestedAt;

    private Date updatedAt;

    private String username;

    private String fullName;

    private String avatarUrl;
}