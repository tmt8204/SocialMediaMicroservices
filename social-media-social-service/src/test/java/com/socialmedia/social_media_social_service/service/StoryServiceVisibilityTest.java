package com.socialmedia.social_media_social_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;
import com.socialmedia.social_media_social_service.entities.StoryEntity;
import com.socialmedia.social_media_social_service.helpers.StoryHelper;
import com.socialmedia.social_media_social_service.repositories.FriendRepository;
import com.socialmedia.social_media_social_service.repositories.StoryMediaRepository;
import com.socialmedia.social_media_social_service.repositories.StoryRepository;
import com.socialmedia.social_media_social_service.repositories.StoryViewRepository;

@ExtendWith(MockitoExtension.class)
class StoryServiceVisibilityTest {

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
    void getFeed_excludesPrivateStoriesFromFriends() {
        StoryEntity ownStory = story(1L, "viewerA", "PRIVATE");
        StoryEntity friendPrivateStory = story(2L, "friendA", "PRIVATE");
        StoryEntity friendPublicStory = story(3L, "friendA", "PUBLIC");

        when(storyRepository.findByUserIdAndIsDeletedFalseAndExpiresAtAfterOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq("viewerA"), any(Date.class)))
                .thenReturn(List.of(ownStory));
        when(friendRepository.findAllAcceptedFriendIds("viewerA")).thenReturn(List.of("friendA"));
        when(storyRepository.findFeedCandidates(org.mockito.ArgumentMatchers.eq("viewerA"), org.mockito.ArgumentMatchers.eq(List.of("friendA")), any(Date.class)))
                .thenReturn(List.of(ownStory, friendPrivateStory, friendPublicStory));
        when(storyViewRepository.findByViewerIdAndStoryIdIn("viewerA", List.of(1L, 3L))).thenReturn(List.of());
        when(userProfileClient.getProfilesByUserIds(List.of("viewerA", "friendA")))
                .thenReturn(Map.of(
                        "viewerA", new UserProfileSummary("viewerA", "viewer", "Viewer", null),
                        "friendA", new UserProfileSummary("friendA", "friend", "Friend", null)));

        var feed = storyService.getFeed("viewerA");

        assertThat(feed).hasSize(2);
        assertThat(feed.stream().flatMap(group -> group.getStories().stream()).map(story -> story.getId()))
                .containsExactlyInAnyOrder(1L, 3L);
    }

    private StoryEntity story(Long id, String userId, String visibility) {
        Date now = new Date();
        StoryEntity story = new StoryEntity();
        story.setId(id);
        story.setUserId(userId);
        story.setVisibility(visibility);
        story.setCreatedAt(now);
        story.setUpdatedAt(now);
        story.setExpiresAt(new Date(now.getTime() + 60_000));
        story.setMedia(List.of());
        return story;
    }
}