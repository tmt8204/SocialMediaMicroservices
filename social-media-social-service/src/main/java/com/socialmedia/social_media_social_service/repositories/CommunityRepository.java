package com.socialmedia.social_media_social_service.repositories;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialmedia.social_media_social_service.entities.CommunityEntity;
import com.socialmedia.social_media_social_service.entities.enums.CommunityPrivacy;

public interface CommunityRepository extends JpaRepository<CommunityEntity, Long> {

    Optional<CommunityEntity> findByIdAndIsDeletedFalse(Long communityId);

    Page<CommunityEntity> findByCreatedByAndIsDeletedFalseOrderByUpdatedAtDesc(String createdBy, Pageable pageable);

    Page<CommunityEntity> findByPrivacyAndIsDeletedFalseOrderByMemberCountDesc(CommunityPrivacy privacy, Pageable pageable);

    Page<CommunityEntity> findByIsDeletedFalseOrderByMemberCountDesc(Pageable pageable);

        @Query("""
                SELECT c FROM CommunityEntity c
                WHERE c.isDeleted = false
                    AND c.id NOT IN :excludedIds
                ORDER BY c.memberCount DESC, c.updatedAt DESC
        """)
        Page<CommunityEntity> findDiscoverCommunities(@Param("excludedIds") Collection<Long> excludedIds, Pageable pageable);
}