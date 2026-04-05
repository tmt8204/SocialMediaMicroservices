package com.socialmedia.social_media_chat_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.socialmedia.social_media_chat_service.dto.response.ApiErrorResponse;

import io.jsonwebtoken.JwtException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), "not_found");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "bad_request");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), "forbidden");
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiErrorResponse> handleJwt(JwtException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), "unauthorized");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleOther(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), "internal_error");
    }

    @MessageExceptionHandler({ BadRequestException.class, AccessDeniedException.class, JwtException.class })
    @SendToUser("/queue/errors")
    public ApiErrorResponse handleWsKnownExceptions(Exception ex) {
        HttpStatus status = ex instanceof JwtException ? HttpStatus.UNAUTHORIZED
                : ex instanceof AccessDeniedException ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;

        return ApiErrorResponse.of(status.value(), ex.getMessage(), status.getReasonPhrase());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ApiErrorResponse handleWsGenericException(Exception ex) {
        return ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), "Internal Server Error");
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, String error) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), message, error));
    }
}
