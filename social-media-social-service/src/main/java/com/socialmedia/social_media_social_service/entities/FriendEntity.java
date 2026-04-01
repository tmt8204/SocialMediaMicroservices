package com.socialmedia.social_media_social_service.entities;

import com.socialmedia.social_media_social_service.entities.enums.FriendStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(
    name = "friends",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_friends_pair_key", columnNames = "pair_key")
    }
)
@Entity
public class FriendEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "friend_to", nullable = false)
    private String friendTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "nvarchar(255) default 'PENDING'")
    private FriendStatus status = FriendStatus.PENDING;

    @Column(name = "pair_key", nullable = false, length = 255)
    private String pairKey;

    @Column(name = "action_by")
    private String actionBy;
}
