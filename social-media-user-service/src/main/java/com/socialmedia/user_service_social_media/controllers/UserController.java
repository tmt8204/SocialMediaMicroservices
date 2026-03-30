package com.socialmedia.user_service_social_media.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.socialmedia.user_service_social_media.dto.UpdateProfileRequest;
import com.socialmedia.user_service_social_media.dto.UserResponse;
import com.socialmedia.user_service_social_media.services.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/create")
    public ResponseEntity<UserResponse> createProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-Email") String email) {
        UserResponse response = userService.createProfile(userId, username, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@RequestHeader("X-User-Id") String userId) {
        UserResponse response = userService.getMe(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getOtherUserProfile(@PathVariable("username") String username) {
        UserResponse response = userService.getProfileByUsername(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(@RequestParam("q") String keyword) {
        return ResponseEntity.ok(userService.searchUsers(keyword));
    }

    @PutMapping("/update-profile")
    public ResponseEntity<UserResponse> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }
}
