package com.socialmedia.social_media_social_service.dto.CommentDTO;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {
    
    private Long id;
    private String userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private String content;
    private Long postId;
    private boolean isDeleted;
    private Date createdAt;
    private Date updatedAt;

}
