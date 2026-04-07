package com.socialmedia.social_media_social_service.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialmedia.social_media_social_service.entities.StoryViewEntity;

public interface StoryViewRepository extends JpaRepository<StoryViewEntity, Long> {

    boolean existsByStoryIdAndViewerId(Long storyId, String viewerId);

    Optional<StoryViewEntity> findByStoryIdAndViewerId(Long storyId, String viewerId);

    Page<StoryViewEntity> findByStoryIdOrderByCreatedAtDesc(Long storyId, Pageable pageable);

    long countByStoryId(Long storyId);

    List<StoryViewEntity> findByViewerIdAndStoryIdIn(String viewerId, Collection<Long> storyIds);

    void deleteByStoryIdIn(Collection<Long> storyIds);
}