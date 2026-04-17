package com.socialmedia.social_media_media_service.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_storage")
public class UserStorageDocument {
    @Id
    private String userId;
    
    @Builder.Default
    private long totalBytesUsed = 0;
}
