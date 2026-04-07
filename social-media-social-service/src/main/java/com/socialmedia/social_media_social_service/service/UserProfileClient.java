package com.socialmedia.social_media_social_service.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummaryPage;
import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserProfileClient {

    private final RestClient restClient;

    public UserProfileClient(@Value("${services.user-service-url:http://localhost:8082}") String userServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    public Map<String, UserProfileSummary> getProfilesByUserIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<String> normalizedUserIds = userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        if (normalizedUserIds.isEmpty()) {
            return Map.of();
        }

        try {
            UserProfileSummary[] response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/users/internal/profiles")
                            .queryParam("userIds", normalizedUserIds.toArray())
                            .build())
                    .retrieve()
                    .body(UserProfileSummary[].class);

            if (response == null || response.length == 0) {
                return Map.of();
            }

            return java.util.Arrays.stream(response)
                    .filter(profile -> profile != null && profile.getUserId() != null && !profile.getUserId().isBlank())
                    .collect(Collectors.toMap(UserProfileSummary::getUserId, Function.identity(), (left, right) -> left));
        } catch (Exception ex) {
            log.warn("Failed to fetch user profiles from user-service: {}", ex.getMessage());
            return Map.of();
        }
    }

    public Page<UserProfileSummary> getDiscoverProfiles(Collection<String> excludeUserIds, Pageable pageable) {
        List<String> normalizedExcludedUserIds = excludeUserIds == null
                ? List.of()
                : excludeUserIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        .stream()
                        .toList();

        try {
            UserProfileSummaryPage response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/users/internal/discover")
                                .queryParam("page", pageable.getPageNumber())
                                .queryParam("size", pageable.getPageSize());

                        if (!normalizedExcludedUserIds.isEmpty()) {
                            uriBuilder.queryParam("excludeUserIds", normalizedExcludedUserIds.toArray());
                        }

                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(UserProfileSummaryPage.class);

            if (response == null || response.getContent() == null) {
                return new PageImpl<>(List.of(), pageable, 0);
            }

            return new PageImpl<>(response.getContent(), pageable, response.getTotalElements());
        } catch (Exception ex) {
            log.warn("Failed to fetch discover profiles from user-service: {}", ex.getMessage());
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }
}