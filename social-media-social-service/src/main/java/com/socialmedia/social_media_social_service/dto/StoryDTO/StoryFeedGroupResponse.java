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
public class StoryFeedGroupResponse {

    private String userId;

    private String username;

    private String fullName;

    private String avatarUrl;

    private boolean hasUnseen;

    private Date latestStoryAt;

    private List<StoryResponse> stories = new ArrayList<>();
}