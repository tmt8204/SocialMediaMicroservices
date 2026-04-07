package com.socialmedia.social_media_social_service.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentCreateRequest;
import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentResponse;
import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentUpdateRequest;
import com.socialmedia.social_media_social_service.service.CommentService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/social")
@AllArgsConstructor
public class CommentController {
    
    private final CommentService commentService;

    //---------------- COMMENT ENDPOINTS ----------------
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getCommentsByPostId(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getCommentsByPostId(postId));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        CommentResponse response = commentService.createComment(userId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PutMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        CommentResponse response = commentService.updateComment(userId, commentId, request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        commentService.deleteComment(userId, commentId);
        return ResponseEntity.noContent().build();
    }
}
