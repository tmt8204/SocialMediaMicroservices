package com.socialmedia.social_media_social_service.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_social_service.dto.ReactionDTO.ReactionSummaryResponse;
import com.socialmedia.social_media_social_service.service.ReactionService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/social")
@AllArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping("/posts/{postId}/react")
    public ResponseEntity<ReactionSummaryResponse> reactToPost(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(reactionService.reactToPost(userId, postId));
    }

    @DeleteMapping("/posts/{postId}/react")
    public ResponseEntity<ReactionSummaryResponse> removeReaction(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(reactionService.removeReaction(userId, postId));
    }

    @GetMapping("/posts/{postId}/reaction-summary")
    public ResponseEntity<ReactionSummaryResponse> getReactionSummary(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(reactionService.getReactionSummary(userId, postId));
    }
}
