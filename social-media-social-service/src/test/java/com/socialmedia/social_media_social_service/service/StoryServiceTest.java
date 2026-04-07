package com.socialmedia.social_media_social_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;
import com.socialmedia.social_media_social_service.entities.StoryEntity;
import com.socialmedia.social_media_social_service.entities.StoryMedia;
import com.socialmedia.social_media_social_service.entities.StoryViewEntity;
import com.socialmedia.social_media_social_service.helpers.StoryHelper;
import com.socialmedia.social_media_social_service.repositories.FriendRepository;
import com.socialmedia.social_media_social_service.repositories.StoryMediaRepository;
import com.socialmedia.social_media_social_service.repositories.StoryRepository;
import com.socialmedia.social_media_social_service.repositories.StoryViewRepository;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private StoryMediaRepository storyMediaRepository;

    @Mock
    private StoryViewRepository storyViewRepository;

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private UserProfileClient userProfileClient;

    @Mock
    private MediaServiceClient mediaServiceClient;

    private StoryService storyService;

    @BeforeEach
    void setUp() {
        storyService = new StoryService(
                storyRepository,
                storyMediaRepository,
                storyViewRepository,
                friendRepository,
                new StoryHelper(),
                userProfileClient,
                mediaServiceClient);
    }

    @Test
    void markStoryViewed_returnsExistingViewWithoutIncrementWhenAlreadyViewed() {
        StoryEntity story = activeStory(101L, "ownerA", "FRIEND");
        StoryViewEntity existingView = storyView(101L, "viewerA", new Date());

        when(storyRepository.findByIdAndIsDeletedFalse(101L)).thenReturn(Optional.of(story));
        when(friendRepository.findAllAcceptedFriendIds("viewerA")).thenReturn(List.of("ownerA"));
        when(storyViewRepository.findByStoryIdAndViewerId(101L, "viewerA")).thenReturn(Optional.of(existingView));

        var response = storyService.markStoryViewed("viewerA", 101L);

        assertThat(response.getStoryId()).isEqualTo(101L);
        assertThat(response.getViewerId()).isEqualTo("viewerA");
        verify(storyRepository, never()).incrementViewCount(any(), any());
    }

    @Test
    void markStoryViewed_isIdempotentWhenConcurrentInsertHitsUniqueConstraint() {
        StoryEntity story = activeStory(202L, "ownerA", "FRIEND");
        StoryViewEntity existingView = storyView(202L, "viewerA", new Date());

        when(storyRepository.findByIdAndIsDeletedFalse(202L)).thenReturn(Optional.of(story));
        when(friendRepository.findAllAcceptedFriendIds("viewerA")).thenReturn(List.of("ownerA"));
        when(storyViewRepository.findByStoryIdAndViewerId(202L, "viewerA"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingView));
        when(storyViewRepository.save(any(StoryViewEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        var response = storyService.markStoryViewed("viewerA", 202L);

        assertThat(response.getStoryId()).isEqualTo(202L);
        assertThat(response.getViewerId()).isEqualTo("viewerA");
        verify(storyRepository, never()).incrementViewCount(any(), any());
    }

    @Test
    void getStoryViewers_enrichesProfilesForOwner() {
        StoryEntity story = activeStory(303L, "ownerA", "PUBLIC");
        StoryViewEntity viewerA = storyView(303L, "viewerA", new Date());
        StoryViewEntity viewerB = storyView(303L, "viewerB", new Date());
        var pageable = PageRequest.of(0, 20);

        when(storyRepository.findByIdAndUserId(303L, "ownerA")).thenReturn(Optional.of(story));
        when(storyViewRepository.findByStoryIdOrderByCreatedAtDesc(303L, pageable))
                .thenReturn(new PageImpl<>(List.of(viewerA, viewerB), pageable, 2));
        when(userProfileClient.getProfilesByUserIds(List.of("viewerA", "viewerB")))
                .thenReturn(Map.of(
                        "viewerA", new UserProfileSummary("viewerA", "anna", "Anna", "a.png"),
                        "viewerB", new UserProfileSummary("viewerB", "bob", "Bob", "b.png")));

        var result = storyService.getStoryViewers("ownerA", 303L, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("anna");
        assertThat(result.getContent().get(1).getFullName()).isEqualTo("Bob");
    }

    @Test
    void cleanupStories_deletesMediaViewsAndStoriesInOrder() {
        StoryEntity story = activeStory(404L, "ownerA", "PUBLIC");
        StoryMedia media = new StoryMedia();
        media.setId(1L);
        media.setStory(story);
        media.setPublicId("social-media/stories/ownerA/file-1");
        media.setMediaUrl("https://cdn/story.jpg");

        when(storyRepository.findCleanupCandidates(any(Date.class), any(PageRequest.class))).thenReturn(List.of(story));
        when(storyMediaRepository.findByStory_IdIn(List.of(404L))).thenReturn(List.of(media));

        int deletedCount = storyService.cleanupStories(50, 0);

        assertThat(deletedCount).isEqualTo(1);
        var inOrder = inOrder(mediaServiceClient, storyViewRepository, storyMediaRepository, storyRepository);
        inOrder.verify(mediaServiceClient).deleteMediaByPublicIds(List.of("social-media/stories/ownerA/file-1"));
        inOrder.verify(storyViewRepository).deleteByStoryIdIn(List.of(404L));
        inOrder.verify(storyMediaRepository).deleteByStory_IdIn(List.of(404L));
        inOrder.verify(storyRepository).deleteAllByIdInBatch(List.of(404L));
    }

    private StoryEntity activeStory(Long storyId, String ownerId, String visibility) {
        StoryEntity story = new StoryEntity();
        story.setId(storyId);
        story.setUserId(ownerId);
        story.setVisibility(visibility);
        story.setCreatedAt(new Date(System.currentTimeMillis() - 1000));
        story.setUpdatedAt(story.getCreatedAt());
        story.setExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        story.setMedia(new ArrayList<>());
        return story;
    }

    private StoryViewEntity storyView(Long storyId, String viewerId, Date createdAt) {
        StoryViewEntity view = new StoryViewEntity();
        view.setStoryId(storyId);
        view.setViewerId(viewerId);
        view.setCreatedAt(createdAt);
        view.setUpdatedAt(createdAt);
        return view;
    }
}