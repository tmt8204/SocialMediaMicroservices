package com.socialmedia.social_media_social_service.dto.ReactionDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionResponse {

    private Long postId;

    private int totalReacts;

    private boolean reactedByCurrentUser;
}
