package com.socialmedia.social_media_social_service.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.socialmedia.social_media_social_service.entities.CommentEntity;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    Optional<List<CommentEntity>> findByPostId(Long postId);
}
