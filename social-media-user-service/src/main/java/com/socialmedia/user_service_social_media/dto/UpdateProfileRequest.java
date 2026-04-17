package com.socialmedia.user_service_social_media.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 50, message = "Full name must be between 2 and 50 characters")
    private String fullName;

    private String avatarUrl;

    @Size(max = 200, message = "Bio must not exceed 200 characters")
    private String bio;

    private String coverUrl;

    private String birthDay;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;

    @Size(max = 50, message = "Relationship must not exceed 50 characters")
    private String relationship;

    private Integer phone;

    private Instant updateAt;

}
