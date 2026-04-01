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
@Table(name = "post_media")
public class PostMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

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
