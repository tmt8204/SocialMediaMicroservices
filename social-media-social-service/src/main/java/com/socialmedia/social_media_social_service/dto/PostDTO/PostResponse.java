package com.socialmedia.social_media_social_service.dto.PostDTO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostResponse {

    private Long id;

    private String userId;

    private String content;

    private String visibility;

    private List<String> mediaUrls = new ArrayList<>();
    
    private Date createdAt;
    
    private Date updatedAt;

}
