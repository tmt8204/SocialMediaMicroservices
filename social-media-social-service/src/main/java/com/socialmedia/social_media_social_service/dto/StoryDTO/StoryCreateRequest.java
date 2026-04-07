package com.socialmedia.social_media_social_service.dto.StoryDTO;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryCreateRequest {

    private String caption;

    @NotBlank(message = "Visibility is required")
    private String visibility;

    @Valid
    private List<StoryMediaRequest> media = new ArrayList<>();
}