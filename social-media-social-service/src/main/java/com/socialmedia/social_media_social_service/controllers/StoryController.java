package com.socialmedia.social_media_social_service.controllers;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryCreateRequest;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryFeedGroupResponse;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryResponse;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryViewResponse;
import com.socialmedia.social_media_social_service.service.StoryService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/social/stories")
@AllArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @PostMapping
    public ResponseEntity<StoryResponse> createStory(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody StoryCreateRequest request) {
        StoryResponse response = storyService.createStory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/feed")
    public ResponseEntity<List<StoryFeedGroupResponse>> getFeed(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(storyService.getFeed(userId));
    }

    @GetMapping("/users/{ownerId}")
    public ResponseEntity<List<StoryResponse>> getStoriesByUser(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String ownerId) {
        return ResponseEntity.ok(storyService.getStoriesByUser(userId, ownerId));
    }

    @GetMapping("/{storyId}")
    public ResponseEntity<StoryResponse> getStoryById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long storyId) {
        return ResponseEntity.ok(storyService.getStoryById(userId, storyId));
    }

    @PostMapping("/{storyId}/view")
    public ResponseEntity<StoryViewResponse> markStoryViewed(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long storyId) {
        return ResponseEntity.ok(storyService.markStoryViewed(userId, storyId));
    }

    @GetMapping("/{storyId}/viewers")
    public ResponseEntity<Page<StoryViewResponse>> getStoryViewers(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long storyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(storyService.getStoryViewers(userId, storyId, pageable));
    }

    @DeleteMapping("/{storyId}")
    public ResponseEntity<Void> deleteStory(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long storyId) {
        storyService.deleteStory(userId, storyId);
        return ResponseEntity.noContent().build();
    }
}