package com.socialmedia.social_media_social_service.helpers;

import org.springframework.stereotype.Component;

import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentResponse;
import com.socialmedia.social_media_social_service.entities.CommentEntity;

@Component
public class CommentHelper {
    
    public CommentResponse convertToCommentResponse(CommentEntity comment) {
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setUserId(comment.getUserId());
        response.setContent(comment.getContent());
        response.setPostId(comment.getPost().getId());
        response.setDeleted(comment.isDeleted());
        response.setCreatedAt(comment.getCreatedAt());
        response.setUpdatedAt(comment.getUpdatedAt());
        return response;
    }
}
