package com.socialmedia.social_media_social_service.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialmedia.social_media_social_service.entities.CommunityMemberEntity;
import com.socialmedia.social_media_social_service.entities.enums.CommunityMemberStatus;

public interface CommunityMemberRepository extends JpaRepository<CommunityMemberEntity, Long> {

    Optional<CommunityMemberEntity> findByCommunityIdAndUserId(Long communityId, String userId);

    boolean existsByCommunityIdAndUserId(Long communityId, String userId);

    Page<CommunityMemberEntity> findByCommunityIdAndStatusOrderByJoinedAtAsc(
            Long communityId,
            CommunityMemberStatus status,
            Pageable pageable);

    Page<CommunityMemberEntity> findByUserIdAndStatusOrderByUpdatedAtDesc(
            String userId,
            CommunityMemberStatus status,
            Pageable pageable);

    Page<CommunityMemberEntity> findByCommunityIdAndStatusOrderByCreatedAtDesc(
            Long communityId,
            CommunityMemberStatus status,
            Pageable pageable);

    @Query("SELECT cm.communityId FROM CommunityMemberEntity cm WHERE cm.userId = :userId AND cm.status IN :statuses")
    List<Long> findCommunityIdsByUserIdAndStatuses(@Param("userId") String userId,
                                                   @Param("statuses") Collection<CommunityMemberStatus> statuses);

    long countByCommunityIdAndStatus(Long communityId, CommunityMemberStatus status);
}