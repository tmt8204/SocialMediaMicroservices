package com.socialmedia.social_media_social_service.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_social_service.dto.FriendDTO.FriendActionResponse;
import com.socialmedia.social_media_social_service.dto.FriendDTO.FriendListItemResponse;
import com.socialmedia.social_media_social_service.dto.FriendDTO.FriendRequestResponse;
import com.socialmedia.social_media_social_service.dto.FriendDTO.FriendSuggestionResponse;
import com.socialmedia.social_media_social_service.dto.FriendDTO.RelationshipStatusResponse;
import com.socialmedia.social_media_social_service.service.FriendService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/social/friends")
@AllArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/requests/{targetUserId}")
    public ResponseEntity<FriendActionResponse> sendFriendRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(friendService.sendFriendRequest(userId, targetUserId));
    }

    @PutMapping("/requests/{requesterId}/accept")
    public ResponseEntity<FriendActionResponse> acceptFriendRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String requesterId) {
        return ResponseEntity.ok(friendService.acceptFriendRequest(userId, requesterId));
    }

    @PutMapping("/requests/{requesterId}/reject")
    public ResponseEntity<FriendActionResponse> rejectFriendRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String requesterId) {
        return ResponseEntity.ok(friendService.rejectFriendRequest(userId, requesterId));
    }

    @DeleteMapping("/requests/{targetUserId}/cancel")
    public ResponseEntity<FriendActionResponse> cancelFriendRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(friendService.cancelFriendRequest(userId, targetUserId));
    }

    @DeleteMapping("/{targetUserId}")
    public ResponseEntity<FriendActionResponse> unfriend(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(friendService.unfriend(userId, targetUserId));
    }

    @GetMapping
    public ResponseEntity<Page<FriendListItemResponse>> getFriends(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(friendService.getFriends(userId, pageable));
    }

    @GetMapping("/requests")
    public ResponseEntity<Page<FriendRequestResponse>> getPendingRequests(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "incoming") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(friendService.getPendingRequests(userId, type, pageable));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<Page<FriendSuggestionResponse>> getFriendSuggestions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(friendService.getFriendSuggestions(userId, pageable));
    }

    @GetMapping("/relationship/{targetUserId}")
    public ResponseEntity<RelationshipStatusResponse> getRelationshipStatus(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(friendService.getRelationshipStatus(userId, targetUserId));
    }
}
