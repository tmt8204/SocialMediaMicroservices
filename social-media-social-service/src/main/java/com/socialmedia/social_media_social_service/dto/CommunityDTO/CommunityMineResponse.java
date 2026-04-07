package com.socialmedia.social_media_social_service.dto.CommunityDTO;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMineResponse {

    private List<CommunityOverviewResponse> owned;

    private List<CommunityOverviewResponse> joined;
}