package com.socialmedia.social_media_social_service.dto.ProfileDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileSummary {

    private String userId;

    private String username;

    private String fullName;

    private String avatarUrl;
}