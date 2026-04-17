package com.socialmedia.social_media_social_service.dto.CommentDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentCreateRequest {
    
    @NotBlank(message = "Content is required")
    @Size(max = 200, message = "Content must not exceed 200 characters")
    private String content;

}
