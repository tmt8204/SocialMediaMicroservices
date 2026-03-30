package com.socialmedia.user_service_social_media.services;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import com.socialmedia.user_service_social_media.dto.UpdateProfileRequest;
import com.socialmedia.user_service_social_media.dto.UserResponse;
import com.socialmedia.user_service_social_media.entities.User;
import com.socialmedia.user_service_social_media.repositories.UserRepository;

@Service
public class UserService {
    
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getMe(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        return toUserResponse(user);
    }

    public UserResponse getProfileByUsername(String username) {
        String normalizedUsername = username == null ? "" : username.trim();

        User user = userRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        return toUserResponse(user);
    }

    public List<UserResponse> searchUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        return userRepository.searchByUsernameOrFullName(keyword.trim())
                .stream()
                .map(this::toUserResponse)
                .toList();
    }
        
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        //Validate username and email
        if(userRepository.existsByUsernameIgnoreCase(request.getUsername()) && !user.getUsername().equalsIgnoreCase(request.getUsername())) {
            throw new IllegalStateException("Username already exists");
        }

        if(userRepository.existsByEmailIgnoreCase(request.getEmail()) && !user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new IllegalStateException("Email already exists");
        }

        Instant now = Instant.now();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setBio(request.getBio());
        user.setCoverUrl(request.getCoverUrl());
        user.setBirthDay(request.getBirthDay());
        user.setLocation(request.getLocation());
        user.setRelationship(request.getRelationship());
        user.setPhone(request.getPhone());
        user.setUpdateAt(now);

        userRepository.save(user);
        return toUserResponse(user);
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setBio(user.getBio());
        response.setCoverUrl(user.getCoverUrl());
        response.setBirthDay(user.getBirthDay());
        response.setLocation(user.getLocation());
        response.setRelationship(user.getRelationship());
        response.setPhone(user.getPhone());
        response.setUpdateAt(user.getUpdateAt());

        return response;
    }

}
