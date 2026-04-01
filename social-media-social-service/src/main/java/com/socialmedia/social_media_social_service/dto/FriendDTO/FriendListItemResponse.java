package com.socialmedia.social_media_social_service.dto.FriendDTO;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendListItemResponse {

    private String otherUserId;

    private String status;

    private Date requestedAt;

    private Date respondedAt;
}
