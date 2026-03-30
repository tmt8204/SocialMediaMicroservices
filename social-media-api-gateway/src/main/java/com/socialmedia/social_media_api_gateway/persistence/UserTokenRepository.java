package com.socialmedia.social_media_api_gateway.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Mono;

public interface UserTokenRepository extends ReactiveMongoRepository<UserToken, String> {

    Mono<UserToken> findByTokenAndTokenTypeAndRevokedFalse(String token, TokenType tokenType);
}
