package com.socialmedia.social_media_media_service.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "media_files")
public class MediaFileDocument {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String publicId;
    
    @Indexed
    private String userId;
    
    private long bytes;
    
    private String resourceType;
}
