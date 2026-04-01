package com.socialmedia.social_media_social_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
    name = "reactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_reaction_user_post", columnNames = {"user_id", "post_id"})
    },
    indexes = {
        @Index(name = "idx_reaction_post", columnList = "post_id")
    }
)
@Entity
public class ReactionsEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "type", columnDefinition = "nvarchar(255) default 'LIKE'")
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private PostEntity post;

    @PrePersist
    @PreUpdate
    private void validateTarget() {
        if (post == null) {
            throw new IllegalStateException("Reaction must reference a post.");
        }
    }

}
