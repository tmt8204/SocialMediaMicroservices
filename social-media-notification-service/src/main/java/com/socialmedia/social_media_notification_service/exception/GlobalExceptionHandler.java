package com.socialmedia.social_media_notification_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.socialmedia.social_media_notification_service.dto.ApiErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), "not_found");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "bad_request");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "internal_error");
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, String error) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(status.value(), message, error));
    }
}