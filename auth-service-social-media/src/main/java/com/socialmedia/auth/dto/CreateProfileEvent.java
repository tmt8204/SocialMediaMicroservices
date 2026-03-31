package com.socialmedia.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * CreateProfileEvent
 * 
 * Event published when a new user is registered in auth service.
 * This event is sent to Kafka topic and consumed by user-service to create user profile.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProfileEvent {

    @JsonProperty("userId")
    @NotBlank(message = "User ID is required")
    private String userId;

    @JsonProperty("username")
    @NotBlank(message = "Username is required")
    private String username;

    @JsonProperty("email")
    @NotBlank(message = "Email is required")
    private String email;

    @JsonProperty("fullName")
    @NotBlank(message = "Full name is required")
    private String fullName;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("eventType")
    @NotBlank(message = "Event type is required")
    private String eventType = "USER_CREATED";
}
