package com.socialmedia.social_media_social_service.dto.CommunityDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityOverviewResponse {

    private Long id;

    private String name;

    private String coverUrl;

    private String privacy;

    private int memberCount;

    private String myRole;

    private String myStatus;
}