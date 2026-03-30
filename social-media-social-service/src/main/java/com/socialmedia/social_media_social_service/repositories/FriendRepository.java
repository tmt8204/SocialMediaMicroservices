package com.socialmedia.social_media_social_service.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialmedia.social_media_social_service.entities.FriendEntity;

public interface FriendRepository extends JpaRepository<FriendEntity, Long> {
    
    @Query("SELECT f.friendTo FROM FriendEntity f WHERE f.userId = :userId AND f.status = 'ACCEPTED'")
    List<String> findAcceptedFriendsIds(@Param("userId") String userId);
    
    @Query("SELECT f.userId FROM FriendEntity f WHERE f.friendTo = :userId AND f.status = 'ACCEPTED'")
    List<String> findUserIdsWhoAddedAsFollowers(@Param("userId") String userId);

}
