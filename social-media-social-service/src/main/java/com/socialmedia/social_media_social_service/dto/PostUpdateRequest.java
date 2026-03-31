package com.socialmedia.social_media_social_service.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostUpdateRequest {
    @NotBlank(message = "Content is required")
    private String content;

    @NotBlank(message = "Visibility is required")
    private String visibility; 

    private List<String> mediaUrls = new ArrayList<>();
}
