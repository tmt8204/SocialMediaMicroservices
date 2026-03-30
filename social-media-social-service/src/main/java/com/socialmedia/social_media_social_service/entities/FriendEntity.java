package com.socialmedia.social_media_social_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "friends")
@Entity
public class FriendEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "friend_to", nullable = false)
    private String friendTo;

    @Column(name = "status", columnDefinition = "nvarchar(255) default 'PENDING'")
    private String status;
}
