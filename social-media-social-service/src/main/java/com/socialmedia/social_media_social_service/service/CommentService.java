package com.socialmedia.social_media_social_service.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentCreateRequest;
import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentResponse;
import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentUpdateRequest;
import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;
import com.socialmedia.social_media_social_service.entities.CommentEntity;
import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.entities.enums.PostContextType;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.helpers.CommentHelper;
import com.socialmedia.social_media_social_service.repositories.CommentRepository;
import com.socialmedia.social_media_social_service.repositories.PostRepository;

import lombok.AllArgsConstructor;

@Service
@Transactional
@AllArgsConstructor
public class CommentService {
    
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CommentHelper commentHelper;
    private final UserProfileClient userProfileClient;
    private final SocialNotificationEventProducer notificationEventProducer;
    private final CommunityService communityService;

    //---------------- COMMENT OPERATIONS ----------------
    public List<CommentResponse> getCommentsByPostId(String userId, Long postId) {
        PostEntity post = getAccessiblePost(userId, postId);

        List<CommentResponse> responses = commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(postId)
                .stream()
                .map(commentHelper::convertToCommentResponse)
                .toList();

        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(
            responses.stream().map(CommentResponse::getUserId).toList());
        responses.forEach(response -> applyAuthorProfile(response, profiles.get(response.getUserId())));
        return responses;
    }

    public CommentResponse createComment(String userId, Long postId, CommentCreateRequest request) {
        // Validate content is not blank
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment content is required and cannot be empty");
        }
        
        // Validate content length
        if (request.getContent().length() > 1000) {
            throw new IllegalArgumentException("Comment content must not exceed 1000 characters");
        }
        
        // Check post exists
        PostEntity post = getAccessiblePost(userId, postId);
        
        // Create new comment entity
        CommentEntity comment = new CommentEntity();
        comment.setUserId(userId);
        comment.setContent(request.getContent().trim());
        comment.setPost(post);
        comment.setDeleted(false);
        comment.setCreatedAt(new Date());
        comment.setUpdatedAt(new Date());
        
        // Save to database
        CommentEntity savedComment = commentRepository.save(comment);

        post.setCommentCount(commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(postId).size());
        postRepository.save(post);

        if (!userId.equals(post.getUserId())) {
            notificationEventProducer.publishCommentCreated(userId, post.getUserId(), postId, savedComment.getId());
        }

        // Convert to response
        CommentResponse response = commentHelper.convertToCommentResponse(savedComment);
        return enrichCommentResponse(response);
    }
    
    public CommentResponse updateComment(String userId, Long commentId, CommentUpdateRequest request) {
        // Find comment by id and userId (ensure ownership)
        CommentEntity comment = commentRepository.findByIdAndUserId(commentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId + " for user: " + userId));
        
        // Check comment is not deleted
        if (comment.isDeleted()) {
            throw new IllegalStateException("Comment was deleted");
        }

        ensureCommentPostAccessible(userId, comment);
        
        // Update content
        comment.setContent(request.getContent().trim());
        comment.setUpdatedAt(new Date());
        
        // Save to database
        CommentEntity updatedComment = commentRepository.save(comment);
        
        // Convert to response
        CommentResponse response = commentHelper.convertToCommentResponse(updatedComment);
        return enrichCommentResponse(response);
    }
    
    public void deleteComment(String userId, Long commentId) {
        // Find comment by id and userId (ensure ownership)
        CommentEntity comment = commentRepository.findByIdAndUserId(commentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId + " for user: " + userId));

        PostEntity post = comment.getPost();
    ensureCommentPostAccessible(userId, comment);
        
        // Delete from database
        commentRepository.delete(comment);

        if (post != null) {
            post.setCommentCount(commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(post.getId()).size());
            postRepository.save(post);
        }
    }

    private CommentResponse enrichCommentResponse(CommentResponse response) {
        if (response == null || !StringUtils.hasText(response.getUserId())) {
            return response;
        }

        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(List.of(response.getUserId()));
        applyAuthorProfile(response, profiles.get(response.getUserId()));
        return response;
    }

    private void applyAuthorProfile(CommentResponse response, UserProfileSummary profile) {
        if (response == null || profile == null) {
            return;
        }

        response.setUsername(profile.getUsername());
        response.setFullName(profile.getFullName());
        response.setAvatarUrl(profile.getAvatarUrl());
    }

    private PostEntity getAccessiblePost(String userId, Long postId) {
        PostEntity post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

        if (post.getPostContext() == PostContextType.COMMUNITY) {
            communityService.requireReadableCommunity(userId, post.getCommunityId());
        }

        return post;
    }

    private void ensureCommentPostAccessible(String userId, CommentEntity comment) {
        PostEntity post = comment.getPost();
        if (post != null && post.getPostContext() == PostContextType.COMMUNITY) {
            communityService.requireReadableCommunity(userId, post.getCommunityId());
        }
    }
    
}
