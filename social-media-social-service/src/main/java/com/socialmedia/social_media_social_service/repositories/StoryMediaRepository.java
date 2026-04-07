package com.socialmedia.social_media_social_service.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.socialmedia.social_media_social_service.entities.StoryMedia;

public interface StoryMediaRepository extends JpaRepository<StoryMedia, Long> {

    List<StoryMedia> findByStory_IdIn(Collection<Long> storyIds);

    void deleteByStory_IdIn(Collection<Long> storyIds);
}