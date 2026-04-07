package com.socialmedia.social_media_social_service.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.socialmedia.social_media_social_service.dto.ReactionDTO.ReactionResponse;
import com.socialmedia.social_media_social_service.dto.ReactionDTO.ReactionsRequest;
import com.socialmedia.social_media_social_service.service.ReactionService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/social")
@AllArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping("/posts/{postId}/react")
    public ResponseEntity<ReactionResponse> reactToPost(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId,
            @RequestBody(required = false) ReactionsRequest request) {
        String type = (request != null && request.getType() != null) ? request.getType() : null;
        return ResponseEntity.ok(reactionService.reactToPost(userId, postId, type));
    }

    @DeleteMapping("/posts/{postId}/react")
    public ResponseEntity<ReactionResponse> removeReaction(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(reactionService.removeReaction(userId, postId));
    }

    @GetMapping("/posts/{postId}/reaction-summary")
    public ResponseEntity<ReactionResponse> getReactionSummary(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(reactionService.getReactionSummary(userId, postId));
    }
}
