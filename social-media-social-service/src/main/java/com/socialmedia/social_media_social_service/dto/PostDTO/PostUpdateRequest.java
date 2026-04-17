package com.socialmedia.social_media_social_service.dto.PostDTO;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostUpdateRequest {
    private String content;

    @NotBlank(message = "Visibility is required")
    private String visibility;

    @Valid
    private List<PostMediaRequest> media = new ArrayList<>();

    private List<String> mediaUrls = new ArrayList<>();

    @Size(max = 50, message = "Mood must not exceed 50 characters")
    private String mood;

    @Size(max = 50, message = "Location must not exceed 50 characters")
    private String location;
}
