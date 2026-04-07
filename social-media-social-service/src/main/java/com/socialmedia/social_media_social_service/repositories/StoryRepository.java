package com.socialmedia.social_media_social_service.repositories;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialmedia.social_media_social_service.entities.StoryEntity;

public interface StoryRepository extends JpaRepository<StoryEntity, Long> {

    @EntityGraph(attributePaths = "media")
    List<StoryEntity> findByUserIdAndIsDeletedFalseAndExpiresAtAfterOrderByCreatedAtDesc(String userId, Date now);

    @EntityGraph(attributePaths = "media")
    Optional<StoryEntity> findByIdAndIsDeletedFalse(Long storyId);

    @EntityGraph(attributePaths = "media")
    Optional<StoryEntity> findByIdAndUserId(Long storyId, String userId);

    @EntityGraph(attributePaths = "media")
    @Query("""
        SELECT s FROM StoryEntity s
        WHERE s.isDeleted = false
          AND s.expiresAt > :now
          AND (
                s.userId = :viewerId
                         OR (
                                        s.userId IN :friendIds
                                AND (s.visibility = 'FRIEND' OR s.visibility = 'PUBLIC')
                         )
          )
        ORDER BY s.createdAt DESC
    """)
    List<StoryEntity> findFeedCandidates(@Param("viewerId") String viewerId,
                                         @Param("friendIds") List<String> friendIds,
                                         @Param("now") Date now);

    @Query("""
        SELECT s FROM StoryEntity s
        WHERE s.expiresAt <= :cutoff
           OR (s.isDeleted = true AND COALESCE(s.updatedAt, s.createdAt) <= :cutoff)
        ORDER BY COALESCE(s.updatedAt, s.expiresAt, s.createdAt) ASC
    """)
    List<StoryEntity> findCleanupCandidates(@Param("cutoff") Date cutoff, Pageable pageable);

    @Modifying
    @Query("UPDATE StoryEntity s SET s.viewCount = s.viewCount + 1, s.updatedAt = :updatedAt WHERE s.id = :storyId")
    int incrementViewCount(@Param("storyId") Long storyId, @Param("updatedAt") Date updatedAt);
}