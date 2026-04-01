package com.socialmedia.social_media_social_service.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.socialmedia.social_media_social_service.entities.ReactionsEntity;

public interface ReactionsRepository extends JpaRepository<ReactionsEntity, Long> {

    Optional<ReactionsEntity> findByUserIdAndPostId(String userId, Long postId);

    boolean existsByUserIdAndPostId(String userId, Long postId);

    void deleteByUserIdAndPostId(String userId, Long postId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);
}
