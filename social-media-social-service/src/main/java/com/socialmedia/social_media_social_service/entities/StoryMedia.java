package com.socialmedia.social_media_social_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "story_media")
public class StoryMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private StoryEntity story;

    @Column(name = "public_id", columnDefinition = "nvarchar(255)")
    private String publicId;

    @Column(name = "media_url", columnDefinition = "nvarchar(1000)")
    private String mediaUrl;

    @Column(name = "media_type", columnDefinition = "nvarchar(50)")
    private String mediaType = "IMAGE";

    @Column(name = "provider", columnDefinition = "nvarchar(50)")
    private String provider = "CLOUDINARY";

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "bytes")
    private Long bytes;
}