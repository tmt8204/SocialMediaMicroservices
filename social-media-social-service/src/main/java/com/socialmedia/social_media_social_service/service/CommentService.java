package com.socialmedia.social_media_social_service.service;

import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentCreateRequest;
import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentResponse;
import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentUpdateRequest;
import com.socialmedia.social_media_social_service.entities.CommentEntity;
import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.helpers.CommentHelper;
import com.socialmedia.social_media_social_service.repositories.CommentRepository;
import com.socialmedia.social_media_social_service.repositories.PostRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CommentService {
    
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CommentHelper commentHelper;

    //---------------- COMMENT OPERATIONS ----------------
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
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));
        
        // Check post is not deleted
        if (post.isDeleted()) {
            throw new IllegalStateException("Post was banned");
        }
        
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
        
        // Convert to response
        return commentHelper.convertToCommentResponse(savedComment);
    }
    
    public CommentResponse updateComment(String userId, Long commentId, CommentUpdateRequest request) {
        // Find comment by id and userId (ensure ownership)
        CommentEntity comment = commentRepository.findByIdAndUserId(commentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId + " for user: " + userId));
        
        // Check comment is not deleted
        if (comment.isDeleted()) {
            throw new IllegalStateException("Comment was deleted");
        }
        
        // Update content
        comment.setContent(request.getContent().trim());
        comment.setUpdatedAt(new Date());
        
        // Save to database
        CommentEntity updatedComment = commentRepository.save(comment);
        
        // Convert to response
        return commentHelper.convertToCommentResponse(updatedComment);
    }
    
    public void deleteComment(String userId, Long commentId) {
        // Find comment by id and userId (ensure ownership)
        CommentEntity comment = commentRepository.findByIdAndUserId(commentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId + " for user: " + userId));
        
        // Delete from database
        commentRepository.delete(comment);
    }
    
}
