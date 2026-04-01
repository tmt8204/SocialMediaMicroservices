package com.socialmedia.social_media_social_service.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.socialmedia.social_media_social_service.dto.PostDTO.PostMediaResponse;
import com.socialmedia.social_media_social_service.dto.PostDTO.PostResponse;
import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.entities.PostMedia;

@Component
public class PostHelper {

    /**
     * Convert PostEntity to PostResponse DTO
     * 
     * @param post The PostEntity to convert
     * @return PostResponse with all fields populated
     */
    public PostResponse convertToPostResponse(PostEntity post) {
        return convertToPostResponse(post, false);
    }

    public PostResponse convertToPostResponse(PostEntity post, boolean reactedByCurrentUser) {
        PostResponse response = new PostResponse();
        response.setId(post.getId());
        response.setUserId(post.getUserId());
        response.setContent(post.getContent());
        response.setVisibility(post.getVisibility());
        response.setTotalReacts(post.getReactionsCount());
        response.setReactedByCurrentUser(reactedByCurrentUser);
        response.setCreatedAt(post.getCreatedAt());
        response.setUpdatedAt(post.getUpdatedAt());

        List<PostMediaResponse> media = new ArrayList<>();
        List<String> mediaUrls = new ArrayList<>();
        for (PostMedia item : post.getMedia()) {
            media.add(PostMediaResponse.builder()
                    .publicId(item.getPublicId())
                    .mediaUrl(item.getMediaUrl())
                    .mediaType(item.getMediaType())
                    .provider(item.getProvider())
                    .width(item.getWidth())
                    .height(item.getHeight())
                    .bytes(item.getBytes())
                    .build());
            mediaUrls.add(item.getMediaUrl());
        }

        response.setMedia(media);
        response.setMediaUrls(mediaUrls);

        return response;
    }

    /**
     * Resolve visibility value - defaults to PUBLIC if null or blank
     * 
     * @param visibility The visibility value to resolve
     * @return Resolved visibility (PUBLIC, FRIEND, or other predefined values)
     */
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
