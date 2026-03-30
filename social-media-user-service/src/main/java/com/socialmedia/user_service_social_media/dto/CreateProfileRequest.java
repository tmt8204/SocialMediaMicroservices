package com.socialmedia.user_service_social_media.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProfileRequest {

    private String username;

    @Email(message = "Email is invalid")
    private String email;

    private String fullName;

    private String avatarUrl;

    private String bio;

    private String coverUrl;

    private String birthDay;

    private String location;

    private String relationship;

    private Integer phone;
}
