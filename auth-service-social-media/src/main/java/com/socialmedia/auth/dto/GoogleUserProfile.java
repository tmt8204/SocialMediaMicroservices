package com.socialmedia.auth.dto;

public record GoogleUserProfile(
        String subject,
        String email,
        String fullName) {
}