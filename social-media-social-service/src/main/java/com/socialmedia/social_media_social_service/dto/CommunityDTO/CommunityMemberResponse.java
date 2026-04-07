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
public class CommunityMemberResponse {

    private Long communityId;

    private String userId;

    private String role;

    private String status;

    private Date joinedAt;

    private String username;

    private String fullName;

    private String avatarUrl;
}