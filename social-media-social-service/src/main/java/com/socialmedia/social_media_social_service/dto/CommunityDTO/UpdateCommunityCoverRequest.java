package com.socialmedia.social_media_social_service.dto.CommunityDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCommunityCoverRequest {

    @NotBlank(message = "Cover URL is required")
    private String coverUrl;
}