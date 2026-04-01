package com.socialmedia.social_media_social_service.dto.PostDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostMediaRequest {

    private String publicId;

    @NotBlank(message = "mediaUrl is required")
    private String mediaUrl;

    private String mediaType;

    private String provider;

    private Integer width;

    private Integer height;

    private Long bytes;
}
