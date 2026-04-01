package com.socialmedia.social_media_social_service.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_social_service.dto.PostCreateRequest;
import com.socialmedia.social_media_social_service.dto.PostResponse;
import com.socialmedia.social_media_social_service.dto.PostUpdateRequest;
import com.socialmedia.social_media_social_service.service.PostService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/social")
@AllArgsConstructor
public class PostController {

    private final PostService postService;


    //---------------- POST ENDPOINTS ----------------
    @PostMapping("/posts/create")
    public ResponseEntity<PostResponse> createPost(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody PostCreateRequest request) {
        PostResponse response = postService.createPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/posts/update/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request) {
        PostResponse response = postService.updatePost(userId, postId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> getPostById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long postId) {
        PostResponse response = postService.getPostById(userId, postId);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/feed")
    public ResponseEntity<Page<PostResponse>> getFeed(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PostResponse> feedPosts = postService.getFeed(userId, pageable);
        return ResponseEntity.ok(feedPosts);
    }
    
    @GetMapping("/user/posts")
    public ResponseEntity<Page<PostResponse>> getOwnerPost(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PostResponse> userPosts = postService.getOwnerPost(userId, pageable);
        return ResponseEntity.ok(userPosts);
    }
}