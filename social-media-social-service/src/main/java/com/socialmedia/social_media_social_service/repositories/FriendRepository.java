package com.socialmedia.social_media_social_service.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialmedia.social_media_social_service.entities.FriendEntity;
import com.socialmedia.social_media_social_service.entities.enums.FriendStatus;

public interface FriendRepository extends JpaRepository<FriendEntity, Long> {

    Optional<FriendEntity> findByPairKey(String pairKey);

    @Query("""
        SELECT f FROM FriendEntity f
        WHERE (f.userId = :userA AND f.friendTo = :userB)
           OR (f.userId = :userB AND f.friendTo = :userA)
    """)
    Optional<FriendEntity> findRelationshipBetween(@Param("userA") String userA, @Param("userB") String userB);

    @Query("SELECT f.friendTo FROM FriendEntity f WHERE f.userId = :userId AND f.status = com.socialmedia.social_media_social_service.entities.enums.FriendStatus.ACCEPTED")
    List<String> findAcceptedFriendsIds(@Param("userId") String userId);

    @Query("""
        SELECT CASE
                 WHEN f.userId = :userId THEN f.friendTo
                 ELSE f.userId
               END
        FROM FriendEntity f
        WHERE (f.userId = :userId OR f.friendTo = :userId)
          AND f.status = com.socialmedia.social_media_social_service.entities.enums.FriendStatus.ACCEPTED
    """)
    List<String> findAllAcceptedFriendIds(@Param("userId") String userId);

    @Query("""
        SELECT f FROM FriendEntity f
        WHERE (f.userId = :userId OR f.friendTo = :userId)
          AND f.status = :status
        ORDER BY COALESCE(f.updatedAt, f.createdAt) DESC
    """)
    Page<FriendEntity> findRelationshipsByUserIdAndStatus(@Param("userId") String userId,
                                                          @Param("status") FriendStatus status,
                                                          Pageable pageable);

    Page<FriendEntity> findByFriendToAndStatusOrderByCreatedAtDesc(String userId, FriendStatus status, Pageable pageable);

    Page<FriendEntity> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, FriendStatus status, Pageable pageable);

    @Query("SELECT f.userId FROM FriendEntity f WHERE f.friendTo = :userId AND f.status = com.socialmedia.social_media_social_service.entities.enums.FriendStatus.ACCEPTED")
    List<String> findUserIdsWhoAddedAsFollowers(@Param("userId") String userId);

    @Query("""
        SELECT CASE
                 WHEN f.userId = :userId THEN f.friendTo
                 ELSE f.userId
               END
        FROM FriendEntity f
        WHERE (f.userId = :userId OR f.friendTo = :userId)
          AND f.status IN :statuses
    """)
    List<String> findRelatedUserIdsByUserIdAndStatuses(@Param("userId") String userId,
                                                       @Param("statuses") List<FriendStatus> statuses);
}
