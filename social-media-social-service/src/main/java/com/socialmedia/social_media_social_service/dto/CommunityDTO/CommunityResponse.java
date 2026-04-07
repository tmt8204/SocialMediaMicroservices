package com.socialmedia.social_media_social_service.dto.CommunityDTO;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityResponse {

    private Long id;

    private String name;

    private String description;

    private String coverUrl;

    private String privacy;

    private int memberCount;

    private String createdBy;

    private String createdByUsername;

    private String createdByFullName;

    private String createdByAvatarUrl;

    private String myRole;

    private String myStatus;

    private boolean isMember;

    private boolean canManage;

    private Date createdAt;

    private Date updatedAt;
}