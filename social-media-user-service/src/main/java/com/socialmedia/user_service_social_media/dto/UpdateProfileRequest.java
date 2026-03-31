package com.socialmedia.user_service_social_media.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String avatarUrl;

    private String bio;

    private String coverUrl;

    private String birthDay;

    private String location;

    private String relationship;

    private Integer phone;

    private Instant updateAt;

}
