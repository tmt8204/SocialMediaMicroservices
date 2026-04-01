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
public class FriendRequestResponse {

    private String requesterId;

    private String targetUserId;

    private String status;

    private Date requestedAt;

    private Date updatedAt;
}
