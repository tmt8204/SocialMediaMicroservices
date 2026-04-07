package com.socialmedia.social_media_social_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "story_views",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_story_view_story_viewer", columnNames = { "story_id", "viewer_id" })
        })
public class StoryViewEntity extends BaseEntity {

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "viewer_id", nullable = false)
    private String viewerId;
}