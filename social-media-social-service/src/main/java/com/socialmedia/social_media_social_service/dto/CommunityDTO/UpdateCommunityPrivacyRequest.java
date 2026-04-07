package com.socialmedia.social_media_social_service.dto.CommunityDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCommunityPrivacyRequest {

    @NotBlank(message = "Privacy is required")
    private String privacy;
}