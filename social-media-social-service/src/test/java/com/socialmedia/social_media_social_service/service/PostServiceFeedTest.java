package com.socialmedia.social_media_social_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.entities.enums.PostContextType;
import com.socialmedia.social_media_social_service.helpers.PostHelper;
import com.socialmedia.social_media_social_service.repositories.FriendRepository;
import com.socialmedia.social_media_social_service.repositories.PostRepository;
import com.socialmedia.social_media_social_service.repositories.ReactionsRepository;

@ExtendWith(MockitoExtension.class)
class PostServiceFeedTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private MediaServiceClient mediaServiceClient;

    @Mock
    private ReactionsRepository reactionsRepository;

    @Mock
    private UserProfileClient userProfileClient;

    @Mock
    private SocialNotificationEventProducer notificationEventProducer;

    @Mock
    private CommunityService communityService;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(
                postRepository,
                friendRepository,
                new PostHelper(),
                mediaServiceClient,
                reactionsRepository,
                userProfileClient,
                notificationEventProducer,
                communityService);
    }

    @Test
    void getFeed_includesJoinedCommunityPostsWhenNoFriends() {
        Pageable pageable = PageRequest.of(0, 10);
        PostEntity communityPost = communityPost(11L, 5L, "authorA");

        when(friendRepository.findAllAcceptedFriendIds("userA")).thenReturn(List.of());
        when(communityService.getJoinedCommunityIds("userA")).thenReturn(List.of(5L, 7L));
        when(communityService.getCommunityNamesByIds(List.of(5L))).thenReturn(Map.of(5L, "Spring Builders"));
        when(postRepository.findFeedPostsWithCommunities("userA", List.of(5L, 7L), pageable))
                .thenReturn(new PageImpl<>(List.of(communityPost), pageable, 1));
        when(reactionsRepository.existsByUserIdAndPostId("userA", 11L)).thenReturn(false);
        when(userProfileClient.getProfilesByUserIds(List.of("authorA"))).thenReturn(Map.of());

        var result = postService.getFeed("userA", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(11L);
        assertThat(result.getContent().get(0).getPostContext()).isEqualTo("COMMUNITY");
        assertThat(result.getContent().get(0).getCommunityId()).isEqualTo(5L);
        assertThat(result.getContent().get(0).getCommunityName()).isEqualTo("Spring Builders");
        verify(postRepository).findFeedPostsWithCommunities("userA", List.of(5L, 7L), pageable);
        verify(postRepository, never()).findPublicFeedPosts("userA", pageable);
    }

    @Test
    void getFeed_withFriendsAndCommunities_usesCombinedQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        PostEntity profilePost = profilePost(21L, "friendA");

        when(friendRepository.findAllAcceptedFriendIds("userA")).thenReturn(List.of("friendA"));
        when(communityService.getJoinedCommunityIds("userA")).thenReturn(List.of(5L));
        when(postRepository.findFeedPosts("userA", List.of("friendA"), List.of(5L), pageable))
                .thenReturn(new PageImpl<>(List.of(profilePost), pageable, 1));
        when(reactionsRepository.existsByUserIdAndPostId("userA", 21L)).thenReturn(false);
        when(userProfileClient.getProfilesByUserIds(List.of("friendA"))).thenReturn(Map.of());

        var result = postService.getFeed("userA", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPostContext()).isEqualTo("PROFILE");
        assertThat(result.getContent().get(0).getCommunityId()).isNull();
        assertThat(result.getContent().get(0).getCommunityName()).isNull();
        verify(postRepository).findFeedPosts("userA", List.of("friendA"), List.of(5L), pageable);
        verify(postRepository, never()).findFeedPostsWithFriends("userA", List.of("friendA"), pageable);
    }

    private PostEntity communityPost(Long postId, Long communityId, String authorId) {
        PostEntity post = new PostEntity();
        post.setId(postId);
        post.setUserId(authorId);
        post.setContent("community post");
        post.setVisibility("PUBLIC");
        post.setPostContext(PostContextType.COMMUNITY);
        post.setCommunityId(communityId);
        post.setMedia(List.of());
        return post;
    }

    private PostEntity profilePost(Long postId, String authorId) {
        PostEntity post = new PostEntity();
        post.setId(postId);
        post.setUserId(authorId);
        post.setContent("profile post");
        post.setVisibility("PUBLIC");
        post.setPostContext(PostContextType.PROFILE);
        post.setMedia(List.of());
        return post;
    }
}