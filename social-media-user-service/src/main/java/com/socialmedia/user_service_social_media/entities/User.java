package com.socialmedia.user_service_social_media.entities;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "users")
public class User {
    
    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String fullName;

    private String avatarUrl;

    private String bio;

    private String coverUrl;

    private String birthDay;

    private String location;

    private String relationship;

    private Integer phone;

    private Instant updateAt;
}
