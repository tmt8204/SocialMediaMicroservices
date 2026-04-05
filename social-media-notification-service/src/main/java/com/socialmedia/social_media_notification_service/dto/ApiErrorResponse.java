package com.socialmedia.social_media_notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {
    private int status;
    private String message;
    private String error;
    private long timestamp;

    public static ApiErrorResponse of(int status, String message, String error) {
        return ApiErrorResponse.builder()
                .status(status)
                .message(message)
                .error(error)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}