package com.socialmedia.social_media_media_service.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMediaRequest {

    @NotEmpty(message = "publicIds must not be empty")
    private List<@NotBlank(message = "publicId must not be blank") String> publicIds;
}
