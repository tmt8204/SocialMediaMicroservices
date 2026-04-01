package com.socialmedia.social_media_social_service.dto.ReactionsDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReactionsRequest {
    
    @NotBlank(message = "Type is required")
    private String type;

}
