package com.socialmedia.social_media_social_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.socialmedia.social_media_social_service.dto.PostDTO.PostCreateRequest;
import com.socialmedia.social_media_social_service.dto.PostDTO.PostMediaRequest;
import com.socialmedia.social_media_social_service.dto.PostDTO.PostUpdateRequest;
import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;
import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.helpers.PostHelper;
import com.socialmedia.social_media_social_service.repositories.FriendRepository;
import com.socialmedia.social_media_social_service.repositories.PostRepository;
import com.socialmedia.social_media_social_service.repositories.ReactionsRepository;

@ExtendWith(MockitoExtension.class)
class PostServiceValidationTest {

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
    void createPost_rejectsWhenContentAndMediaAreBothEmpty() {
        PostCreateRequest request = new PostCreateRequest();
        request.setContent("   ");
        request.setVisibility("PUBLIC");
        request.setMedia(List.of());
        request.setMediaUrls(List.of("   "));

        assertThatThrownBy(() -> postService.createPost("userA", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Post must contain content or at least one media item");
    }

    @Test
    void createPost_allowsMediaOnlyPostAndNormalizesBlankContentToNull() {
        PostCreateRequest request = new PostCreateRequest();
        request.setContent("   ");
        request.setVisibility("PUBLIC");
        request.setMedia(List.of(new PostMediaRequest("public-1", "https://cdn/image.jpg", "IMAGE", "CLOUDINARY", 100, 100, 200L)));
        request.setMediaUrls(List.of());
        request.setMood("happy");
        request.setLocation("Hanoi");

        PostEntity savedPost = new PostEntity();
        savedPost.setId(1L);
        savedPost.setUserId("userA");
        savedPost.setVisibility("PUBLIC");
        savedPost.setMedia(List.of());

        when(postRepository.save(any(PostEntity.class))).thenReturn(savedPost);
        when(reactionsRepository.existsByUserIdAndPostId("userA", 1L)).thenReturn(false);
        when(userProfileClient.getProfilesByUserIds(List.of("userA")))
                .thenReturn(Map.of("userA", new UserProfileSummary("userA", "userA", "User A", null)));

        postService.createPost("userA", request);

        ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isNull();
        assertThat(captor.getValue().getMood()).isEqualTo("happy");
        assertThat(captor.getValue().getLocation()).isEqualTo("Hanoi");
    }

    @Test
    void updatePost_allowsContentOnlyWithoutMedia() {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setContent("updated text");
        request.setVisibility("PUBLIC");
        request.setMedia(List.of());
        request.setMediaUrls(List.of());
        request.setMood("focused");
        request.setLocation("Da Nang");

        PostEntity existing = new PostEntity();
        existing.setId(10L);
        existing.setUserId("userA");
        existing.setContent("old");
        existing.setVisibility("PUBLIC");
        existing.setMedia(new ArrayList<>());

        when(postRepository.findByIdAndUserId(10L, "userA")).thenReturn(Optional.of(existing));
        when(postRepository.save(any(PostEntity.class))).thenReturn(existing);
        when(reactionsRepository.existsByUserIdAndPostId("userA", 10L)).thenReturn(false);
        when(userProfileClient.getProfilesByUserIds(List.of("userA")))
                .thenReturn(Map.of("userA", new UserProfileSummary("userA", "userA", "User A", null)));

        postService.updatePost("userA", 10L, request);

        assertThat(existing.getContent()).isEqualTo("updated text");
        assertThat(existing.getMood()).isEqualTo("focused");
        assertThat(existing.getLocation()).isEqualTo("Da Nang");
    }
}