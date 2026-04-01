package com.socialmedia.social_media_social_service.helpers;

import org.springframework.stereotype.Component;

import com.socialmedia.social_media_social_service.dto.CommentDTO.CommentResponse;
import com.socialmedia.social_media_social_service.entities.CommentEntity;

@Component
public class CommentHelper {
    
    public CommentResponse convertToCommentResponse(CommentEntity comment) {
        return new CommentResponse(
            comment.getId(),
            comment.getUserId(),
            comment.getContent(),
            comment.getPost().getId(),
            comment.isDeleted(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
