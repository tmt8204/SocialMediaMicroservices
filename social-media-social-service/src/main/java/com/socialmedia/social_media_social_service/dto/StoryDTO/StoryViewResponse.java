package com.socialmedia.social_media_social_service.dto.StoryDTO;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryViewResponse {

    private Long storyId;

    private String viewerId;

    private String username;

    private String fullName;

    private String avatarUrl;

    private Date viewedAt;
}