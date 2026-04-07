package com.socialmedia.social_media_social_service.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryMediaResponse;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryResponse;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryViewResponse;
import com.socialmedia.social_media_social_service.entities.StoryEntity;
import com.socialmedia.social_media_social_service.entities.StoryMedia;
import com.socialmedia.social_media_social_service.entities.StoryViewEntity;

@Component
public class StoryHelper {

    public StoryResponse convertToStoryResponse(StoryEntity story, boolean viewedByCurrentUser) {
        return convertToStoryResponse(story, viewedByCurrentUser, null);
    }

    public StoryResponse convertToStoryResponse(StoryEntity story,
                                                boolean viewedByCurrentUser,
                                                UserProfileSummary authorProfile) {
        StoryResponse response = new StoryResponse();
        response.setId(story.getId());
        response.setUserId(story.getUserId());
        if (authorProfile != null) {
            response.setUsername(authorProfile.getUsername());
            response.setFullName(authorProfile.getFullName());
            response.setAvatarUrl(authorProfile.getAvatarUrl());
        }
        response.setCaption(story.getCaption());
        response.setVisibility(story.getVisibility());
        response.setViewCount(story.getViewCount());
        response.setViewedByCurrentUser(viewedByCurrentUser);
        response.setCreatedAt(story.getCreatedAt());
        response.setExpiresAt(story.getExpiresAt());

        List<StoryMediaResponse> mediaResponses = new ArrayList<>();
        for (StoryMedia item : story.getMedia()) {
            mediaResponses.add(StoryMediaResponse.builder()
                    .publicId(item.getPublicId())
                    .mediaUrl(item.getMediaUrl())
                    .mediaType(item.getMediaType())
                    .provider(item.getProvider())
                    .width(item.getWidth())
                    .height(item.getHeight())
                    .bytes(item.getBytes())
                    .build());
        }
        response.setMedia(mediaResponses);

        return response;
    }

    public StoryViewResponse convertToStoryViewResponse(StoryViewEntity storyView, UserProfileSummary viewerProfile) {
        StoryViewResponse response = new StoryViewResponse();
        response.setStoryId(storyView.getStoryId());
        response.setViewerId(storyView.getViewerId());
        response.setViewedAt(storyView.getCreatedAt());
        if (viewerProfile != null) {
            response.setUsername(viewerProfile.getUsername());
            response.setFullName(viewerProfile.getFullName());
            response.setAvatarUrl(viewerProfile.getAvatarUrl());
        }
        return response;
    }

    public String resolveVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return "PUBLIC";
        }

        String normalized = visibility.trim().toUpperCase(Locale.ROOT);
        if (!"PUBLIC".equals(normalized) && !"FRIEND".equals(normalized) && !"PRIVATE".equals(normalized)) {
            throw new IllegalArgumentException("Invalid visibility. Allowed values: PUBLIC, FRIEND, PRIVATE");
        }

        return normalized;
    }
}