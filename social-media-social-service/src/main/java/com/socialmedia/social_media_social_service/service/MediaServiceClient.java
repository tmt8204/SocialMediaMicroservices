package com.socialmedia.social_media_social_service.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MediaServiceClient {

    private final RestClient restClient;

    public MediaServiceClient(@Value("${services.media-service-url:http://localhost:8084}") String mediaServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(mediaServiceUrl)
                .build();
    }

    public void deleteMediaByPublicIds(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return;
        }

        try {
            restClient.post()
                    .uri("/api/media/delete-batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("publicIds", publicIds))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Requested media cleanup for {} Cloudinary assets.", publicIds.size());
        } catch (Exception ex) {
            log.warn("Failed to clean up media in media-service: {}", ex.getMessage());
        }
    }
}
