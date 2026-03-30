package com.socialmedia.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    private String userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("email")
    private String email;

    @JsonProperty("fullName")
    private String fullName;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("eventType")
    private String eventType = "USER_CREATED";
}
