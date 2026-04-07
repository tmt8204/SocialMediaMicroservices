package com.socialmedia.social_media_social_service.dto.StoryDTO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryResponse {

    private Long id;

    private String userId;

    private String username;

    private String fullName;

    private String avatarUrl;

    private String caption;

    private String visibility;

    private List<StoryMediaResponse> media = new ArrayList<>();

    private int viewCount;

    private boolean viewedByCurrentUser;

    private Date createdAt;

    private Date expiresAt;
}