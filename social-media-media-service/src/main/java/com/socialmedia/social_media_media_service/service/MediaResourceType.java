package com.socialmedia.social_media_media_service.service;

import java.util.Locale;

public enum MediaResourceType {
    POST("post", "social-media/posts/"),
    STORY("story", "social-media/stories/");

    private final String tag;
    private final String folderPrefix;

    MediaResourceType(String tag, String folderPrefix) {
        this.tag = tag;
        this.folderPrefix = folderPrefix;
    }

    public String tag() {
        return tag;
    }

    public String folderForUser(String normalizedUserId) {
        return folderPrefix + normalizedUserId;
    }

    public static MediaResourceType from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return POST;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (MediaResourceType value : values()) {
            if (value.tag.equals(normalized)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Invalid resourceType. Allowed values: post, story.");
    }
}