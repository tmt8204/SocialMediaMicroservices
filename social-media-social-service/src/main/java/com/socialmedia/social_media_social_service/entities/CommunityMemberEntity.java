package com.socialmedia.social_media_social_service.entities;

import java.util.Date;

import com.socialmedia.social_media_social_service.entities.enums.CommunityMemberStatus;
import com.socialmedia.social_media_social_service.entities.enums.CommunityRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
    name = "community_members",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_community_members_community_user", columnNames = { "community_id", "user_id" })
    },
    indexes = {
        @Index(name = "idx_community_members_community_id", columnList = "community_id"),
        @Index(name = "idx_community_members_user_id", columnList = "user_id"),
        @Index(name = "idx_community_members_status", columnList = "status"),
        @Index(name = "idx_community_members_community_status", columnList = "community_id,status"),
        @Index(name = "idx_community_members_user_status", columnList = "user_id,status")
    }
)
public class CommunityMemberEntity extends BaseEntity {

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private CommunityRole role = CommunityRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CommunityMemberStatus status = CommunityMemberStatus.APPROVED;

    @Column(name = "joined_at")
    private Date joinedAt;
}