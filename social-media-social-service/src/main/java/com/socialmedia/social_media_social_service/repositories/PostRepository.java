package com.socialmedia.social_media_social_service.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialmedia.social_media_social_service.entities.PostEntity;

public interface PostRepository extends JpaRepository<PostEntity, Long> {

    Optional<PostEntity> findByIdAndUserId(Long id, String userId);
    
    @Query("""
    SELECT p FROM PostEntity p
    WHERE p.isDeleted = false
    AND p.userId != :userId
    AND (
        (p.userId IN :friendIds AND (p.visibility = 'FRIEND' OR p.visibility = 'PUBLIC'))
        OR (p.visibility = 'PUBLIC')
    )
    ORDER BY p.createdAt DESC
    """)
    Page<PostEntity> findFeedPosts(@Param("userId") String userId, @Param("friendIds") List<String> friendIds, Pageable pageable);
    
    @Query("SELECT p FROM PostEntity p WHERE p.userId = :userId AND p.isDeleted = false ORDER BY p.createdAt DESC")
    Page<PostEntity> findUserPosts(@Param("userId") String userId, Pageable pageable);
    
    List<PostEntity> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);
}
