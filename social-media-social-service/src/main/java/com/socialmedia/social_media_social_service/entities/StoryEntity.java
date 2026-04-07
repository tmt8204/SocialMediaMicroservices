package com.socialmedia.social_media_social_service.entities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
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
@Table(name = "stories")
public class StoryEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "visibility", nullable = false, length = 50)
    private String visibility;

    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

    @Column(name = "is_deleted", columnDefinition = "bit default 0")
    private boolean isDeleted = false;

    @Column(name = "view_count", columnDefinition = "int default 0")
    private int viewCount = 0;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoryMedia> media = new ArrayList<>();
}