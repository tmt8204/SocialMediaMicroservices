package com.socialmedia.user_service_social_media.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileSummaryResponse {

    private String userId;

    private String username;

    private String fullName;

    private String avatarUrl;
}