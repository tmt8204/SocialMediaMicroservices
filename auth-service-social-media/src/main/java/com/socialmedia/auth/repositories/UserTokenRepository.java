package com.socialmedia.auth.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.socialmedia.auth.entities.TokenType;
import com.socialmedia.auth.entities.UserToken;

public interface UserTokenRepository extends MongoRepository<UserToken, String> {

    Optional<UserToken> findByTokenAndRevokedFalse(String token);

    Optional<UserToken> findByTokenAndTokenTypeAndRevokedFalse(String token, TokenType tokenType);

    void deleteByUserId(String userId);

    void deleteByToken(String token);

    void deleteByUserIdAndTokenType(String userId, TokenType tokenType);
}
