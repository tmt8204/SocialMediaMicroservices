package com.socialmedia.social_media_social_service.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostCreateRequest {

    private String content;

    private String visibility;

    private List<String> mediaUrls = new ArrayList<>();
}
