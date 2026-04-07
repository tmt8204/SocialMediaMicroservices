package com.socialmedia.social_media_social_service.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityActionResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityJoinRequestResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityMemberResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityMineResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityOverviewResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CreateCommunityRequest;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.UpdateCommunityCoverRequest;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.UpdateCommunityPrivacyRequest;
import com.socialmedia.social_media_social_service.dto.PostDTO.PostCreateRequest;
import com.socialmedia.social_media_social_service.dto.PostDTO.PostResponse;
import com.socialmedia.social_media_social_service.service.CommunityService;
import com.socialmedia.social_media_social_service.service.PostService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/social/communities")
@AllArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final PostService postService;

    @PostMapping
    public ResponseEntity<CommunityResponse> createCommunity(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateCommunityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(communityService.createCommunity(userId, request));
    }

    @GetMapping("/{communityId}")
    public ResponseEntity<CommunityResponse> getCommunityDetail(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId) {
        return ResponseEntity.ok(communityService.getCommunityDetail(userId, communityId));
    }

    @GetMapping("/mine")
    public ResponseEntity<CommunityMineResponse> getMyCommunities(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(communityService.getMyCommunities(userId));
    }

    @GetMapping("/discover")
    public ResponseEntity<Page<CommunityOverviewResponse>> discoverCommunities(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(communityService.discoverCommunities(userId, pageable));
    }

    @PostMapping("/{communityId}/join")
    public ResponseEntity<CommunityResponse> joinCommunity(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId) {
        return ResponseEntity.ok(communityService.joinCommunity(userId, communityId));
    }

    @GetMapping("/{communityId}/members")
    public ResponseEntity<Page<CommunityMemberResponse>> getMembers(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(communityService.getMembers(userId, communityId, pageable));
    }

    @GetMapping("/{communityId}/requests")
    public ResponseEntity<Page<CommunityJoinRequestResponse>> getPendingRequests(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(communityService.getPendingRequests(userId, communityId, pageable));
    }

    @PutMapping("/{communityId}/requests/{targetUserId}/approve")
    public ResponseEntity<CommunityActionResponse> approveJoinRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(communityService.approveJoinRequest(userId, communityId, targetUserId));
    }

    @PutMapping("/{communityId}/requests/{targetUserId}/reject")
    public ResponseEntity<CommunityActionResponse> rejectJoinRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(communityService.rejectJoinRequest(userId, communityId, targetUserId));
    }

    @PostMapping("/{communityId}/leave")
    public ResponseEntity<Void> leaveCommunity(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId) {
        communityService.leaveCommunity(userId, communityId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> deleteCommunity(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId) {
        communityService.deleteCommunity(userId, communityId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{communityId}/cover")
    public ResponseEntity<CommunityResponse> updateCover(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId,
            @Valid @RequestBody UpdateCommunityCoverRequest request) {
        return ResponseEntity.ok(communityService.updateCover(userId, communityId, request.getCoverUrl()));
    }

    @PutMapping("/{communityId}/privacy")
    public ResponseEntity<CommunityResponse> updatePrivacy(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId,
            @Valid @RequestBody UpdateCommunityPrivacyRequest request) {
        return ResponseEntity.ok(communityService.updatePrivacy(userId, communityId, request.getPrivacy()));
    }

    @GetMapping("/{communityId}/posts")
    public ResponseEntity<Page<PostResponse>> getCommunityPosts(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(postService.getCommunityPosts(userId, communityId, pageable));
    }

    @PostMapping("/{communityId}/posts")
    public ResponseEntity<PostResponse> createCommunityPost(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long communityId,
            @Valid @RequestBody PostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createCommunityPost(userId, communityId, request));
    }
}