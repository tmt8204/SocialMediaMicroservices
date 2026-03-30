package com.socialmedia.auth.entities;

import java.time.Instant;

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
@Document(collection = "user_tokens")
public class UserToken extends BaseEntity {

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String token;

    private TokenType tokenType;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private boolean revoked = false;
}
