package com.socialmedia.auth.entities;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Document(collection = "users")
public class User extends BaseEntity {

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    @DBRef
    private Role role;

    private String fullName;

    private Boolean emailVerified = false;

    private Instant lastLoginAt;

    private int loginFailedCount = 0;

    private Instant lockedUntil;

    private Boolean isActive = true;
}
