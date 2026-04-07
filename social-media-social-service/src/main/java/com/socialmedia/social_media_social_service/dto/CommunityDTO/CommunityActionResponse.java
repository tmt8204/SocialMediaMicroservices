package com.socialmedia.social_media_social_service.dto.CommunityDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityActionResponse {

    private Long communityId;

    private String userId;

    private String status;

    private String message;
}