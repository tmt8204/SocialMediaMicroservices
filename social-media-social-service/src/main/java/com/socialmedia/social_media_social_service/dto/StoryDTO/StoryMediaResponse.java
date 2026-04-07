package com.socialmedia.social_media_social_service.dto.StoryDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoryMediaResponse {

    private String publicId;

    private String mediaUrl;

    private String mediaType;

    private String provider;

    private Integer width;

    private Integer height;

    private Long bytes;
}