package com.socialmedia.social_media_media_service.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadMediaResponse {

    @Builder.Default
    private List<MediaItem> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaItem {
        private String publicId;
        private String mediaUrl;
        private String mediaType;
        private String format;
        private Integer width;
        private Integer height;
        private Long bytes;
    }
}
