package com.socialmedia.social_media_social_service.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.entities.enums.PostContextType;

public interface PostRepository extends JpaRepository<PostEntity, Long> {

    Optional<PostEntity> findByIdAndUserId(Long id, String userId);

    @Query("SELECT p FROM PostEntity p WHERE p.id = :id AND p.userId = :userId AND p.isDeleted = false")
    Optional<PostEntity> findByIdAndUserIdAndIsDeletedFalse(@Param("id") Long id, @Param("userId") String userId);

    @Query("SELECT p FROM PostEntity p WHERE p.id = :id AND p.isDeleted = false")
    Optional<PostEntity> findByIdAndIsDeletedFalse(@Param("id") Long id);

    @Query("""
    SELECT p FROM PostEntity p
    WHERE p.isDeleted = false
    AND (
        (
            p.postContext = com.socialmedia.social_media_social_service.entities.enums.PostContextType.PROFILE
            AND (
                (p.userId = :userId)
                OR (p.userId IN :friendIds AND (p.visibility = 'FRIEND' OR p.visibility = 'PUBLIC'))
                OR (p.visibility = 'PUBLIC')
            )
        )
        OR (
            p.postContext = com.socialmedia.social_media_social_service.entities.enums.PostContextType.COMMUNITY
            AND p.communityId IN :communityIds
        )
    )
    ORDER BY p.createdAt DESC
    """)
    Page<PostEntity> findFeedPosts(@Param("userId") String userId,
                                   @Param("friendIds") List<String> friendIds,
                                   @Param("communityIds") List<Long> communityIds,
                                   Pageable pageable);

    @Query("""
    SELECT p FROM PostEntity p
    WHERE p.isDeleted = false
    AND (
        (
            p.postContext = com.socialmedia.social_media_social_service.entities.enums.PostContextType.PROFILE
            AND (
                (p.userId = :userId)
                OR (p.userId IN :friendIds AND (p.visibility = 'FRIEND' OR p.visibility = 'PUBLIC'))
                OR (p.visibility = 'PUBLIC')
            )
        )
    )
    ORDER BY p.createdAt DESC
    """)
    Page<PostEntity> findFeedPostsWithFriends(@Param("userId") String userId,
                                              @Param("friendIds") List<String> friendIds,
                                              Pageable pageable);

    @Query("""
    SELECT p FROM PostEntity p
    WHERE p.isDeleted = false
    AND (
        (
            p.postContext = com.socialmedia.social_media_social_service.entities.enums.PostContextType.PROFILE
            AND (
                p.userId = :userId
                OR p.visibility = 'PUBLIC'
            )
        )
        OR (
            p.postContext = com.socialmedia.social_media_social_service.entities.enums.PostContextType.COMMUNITY
            AND p.communityId IN :communityIds
        )
    )
    ORDER BY p.createdAt DESC
    """)
    Page<PostEntity> findFeedPostsWithCommunities(@Param("userId") String userId,
                                                  @Param("communityIds") List<Long> communityIds,
                                                  Pageable pageable);

    @Query("""
    SELECT p FROM PostEntity p
    WHERE p.isDeleted = false
    AND p.postContext = com.socialmedia.social_media_social_service.entities.enums.PostContextType.PROFILE
    AND (
        p.userId = :userId
        OR p.visibility = 'PUBLIC'
    )
    ORDER BY p.createdAt DESC
    """)
    Page<PostEntity> findPublicFeedPosts(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT p FROM PostEntity p WHERE p.userId = :userId AND p.isDeleted = false AND p.postContext = com.socialmedia.social_media_social_service.entities.enums.PostContextType.PROFILE ORDER BY p.createdAt DESC")
    Page<PostEntity> findUserPosts(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT p FROM PostEntity p WHERE p.communityId = :communityId AND p.isDeleted = false AND p.postContext = com.socialmedia.social_media_social_service.entities.enums.PostContextType.COMMUNITY ORDER BY p.createdAt DESC")
    Page<PostEntity> findCommunityPosts(@Param("communityId") Long communityId, Pageable pageable);

    List<PostEntity> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);

    List<PostEntity> findByCommunityIdAndIsDeletedFalseOrderByCreatedAtDesc(Long communityId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE PostEntity p SET p.isDeleted = true WHERE p.communityId = :communityId AND p.postContext = :postContext")
    int softDeleteByCommunityIdAndPostContext(@Param("communityId") Long communityId, @Param("postContext") PostContextType postContext);
}
