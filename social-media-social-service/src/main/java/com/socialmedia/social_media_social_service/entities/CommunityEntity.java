package com.socialmedia.social_media_social_service.entities;

import com.socialmedia.social_media_social_service.entities.enums.CommunityPrivacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "communities",
    indexes = {
        @Index(name = "idx_communities_created_by", columnList = "created_by"),
        @Index(name = "idx_communities_privacy", columnList = "privacy"),
        @Index(name = "idx_communities_member_count", columnList = "member_count")
    }
)
public class CommunityEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "cover_url")
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy", nullable = false, length = 50)
    private CommunityPrivacy privacy = CommunityPrivacy.PUBLIC;

    @Column(name = "member_count", nullable = false)
    private int memberCount = 1;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
}