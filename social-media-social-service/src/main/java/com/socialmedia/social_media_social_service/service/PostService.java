package com.socialmedia.social_media_social_service.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_social_service.dto.PostDTO.PostCreateRequest;
import com.socialmedia.social_media_social_service.dto.PostDTO.PostMediaRequest;
import com.socialmedia.social_media_social_service.dto.PostDTO.PostResponse;
import com.socialmedia.social_media_social_service.dto.PostDTO.PostUpdateRequest;
import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;
import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.entities.PostMedia;
import com.socialmedia.social_media_social_service.entities.enums.CommunityMemberStatus;
import com.socialmedia.social_media_social_service.entities.enums.PostContextType;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.helpers.PostHelper;
import com.socialmedia.social_media_social_service.repositories.FriendRepository;
import com.socialmedia.social_media_social_service.repositories.PostRepository;
import com.socialmedia.social_media_social_service.repositories.ReactionsRepository;

import lombok.AllArgsConstructor;

@Service
@Transactional
@AllArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final FriendRepository friendRepository;
    private final PostHelper postHelper;
    private final MediaServiceClient mediaServiceClient;
    private final ReactionsRepository reactionsRepository;
    private final UserProfileClient userProfileClient;
    private final SocialNotificationEventProducer notificationEventProducer;
    private final CommunityService communityService;

    //---------------- POST OPERATIONS ----------------
    public PostResponse createPost(String userId, PostCreateRequest request) {
        validatePostPayload(request.getContent(), request.getMedia(), request.getMediaUrls());

        PostEntity post = new PostEntity();
        post.setUserId(userId);
        post.setContent(normalizeContent(request.getContent()));
        post.setVisibility(postHelper.resolveVisibility(request.getVisibility()));
        post.setCommunityId(null);
        post.setPostContext(PostContextType.PROFILE);
        post.setCreatedAt(new Date());
        post.setUpdatedAt(new Date());

        replacePostMedia(post, request.getMedia(), request.getMediaUrls());
        PostEntity savedPost = postRepository.save(post);

        publishPostCreatedNotification(userId, savedPost);

        return enrichPostResponse(postHelper.convertToPostResponse(savedPost, hasUserReacted(userId, savedPost.getId())));
    }

    public PostResponse updatePost(String userId, Long postId, PostUpdateRequest request) {
        validatePostPayload(request.getContent(), request.getMedia(), request.getMediaUrls());

        // Ensure the post exists and belongs to the user
        PostEntity post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

        List<String> removedPublicIds = extractRemovedPublicIds(post.getMedia(), request.getMedia(), request.getMediaUrls());

        post.setContent(normalizeContent(request.getContent()));
        post.setVisibility(postHelper.resolveVisibility(request.getVisibility()));
        post.setUpdatedAt(new Date());

        replacePostMedia(post, request.getMedia(), request.getMediaUrls());

        PostEntity updatedPost = postRepository.save(post);
        mediaServiceClient.deleteMediaByPublicIds(removedPublicIds);

        return enrichPostResponse(postHelper.convertToPostResponse(updatedPost, hasUserReacted(userId, updatedPost.getId())));
    }

    public PostResponse getPostById(String userId, Long postId) {
        PostEntity post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

        validateCanReadPost(userId, post);

        return enrichPostResponse(postHelper.convertToPostResponse(post, hasUserReacted(userId, post.getId())));
    }

    public PostResponse createCommunityPost(String userId, Long communityId, PostCreateRequest request) {
        communityService.requireWritableCommunity(userId, communityId);
        validatePostPayload(request.getContent(), request.getMedia(), request.getMediaUrls());

        PostEntity post = new PostEntity();
        post.setUserId(userId);
        post.setContent(normalizeContent(request.getContent()));
        post.setVisibility(postHelper.resolveVisibility(request.getVisibility()));
        post.setCommunityId(communityId);
        post.setPostContext(PostContextType.COMMUNITY);
        post.setCreatedAt(new Date());
        post.setUpdatedAt(new Date());

        replacePostMedia(post, request.getMedia(), request.getMediaUrls());
        PostEntity savedPost = postRepository.save(post);

        return enrichPostResponse(postHelper.convertToPostResponse(savedPost, hasUserReacted(userId, savedPost.getId())));
    }

    // Hide post (soft delete by marking as deleted)
    public void hidePost(String userId, Long postId) {
        PostEntity post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId + " for user: " + userId));

        post.setDeleted(true);
        postRepository.save(post);
    }

    // Unhide post (restore from soft delete)
    public void unhidePost(String userId, Long postId) {
        PostEntity post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId + " for user: " + userId));

        post.setDeleted(false);
        postRepository.save(post);
    }

    // Delete post permanently from database
    public void deletePostPermanently(String userId, Long postId) {
        PostEntity post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId + " for user: " + userId));

        List<String> publicIdsToDelete = extractPublicIds(post.getMedia());
        postRepository.delete(post);
        mediaServiceClient.deleteMediaByPublicIds(publicIdsToDelete);
    }

    //Get posts of the authenticated user
    public Page<PostResponse> getOwnerPost(String userId, Pageable pageable) {
        Page<PostEntity> userPosts = postRepository.findUserPosts(userId, pageable);
        List<PostResponse> responses = userPosts.getContent().stream()
                .map(post -> postHelper.convertToPostResponse(post, hasUserReacted(userId, post.getId())))
                .toList();
        return enrichPostPage(userPosts, responses);
    }

    //----------------NEWSFEED OPERATIONS----------------
    public Page<PostResponse> getFeed(String userId, Pageable pageable) {
        List<String> friendIds = friendRepository.findAllAcceptedFriendIds(userId);
        List<Long> joinedCommunityIds = communityService == null
                ? List.of()
                : communityService.getJoinedCommunityIds(userId);

        Page<PostEntity> feedPosts;
        if (friendIds.isEmpty() && joinedCommunityIds.isEmpty()) {
            feedPosts = postRepository.findPublicFeedPosts(userId, pageable);
        } else if (joinedCommunityIds.isEmpty()) {
            feedPosts = postRepository.findFeedPostsWithFriends(userId, friendIds, pageable);
        } else if (friendIds.isEmpty()) {
            feedPosts = postRepository.findFeedPostsWithCommunities(userId, joinedCommunityIds, pageable);
        } else {
            feedPosts = postRepository.findFeedPosts(userId, friendIds, joinedCommunityIds, pageable);
        }

        List<PostResponse> responses = feedPosts.getContent().stream()
                .map(post -> postHelper.convertToPostResponse(post, hasUserReacted(userId, post.getId())))
                .toList();
        return enrichPostPage(feedPosts, responses);
    }

    public Page<PostResponse> getCommunityPosts(String userId, Long communityId, Pageable pageable) {
        communityService.requireReadableCommunity(userId, communityId);

        Page<PostEntity> communityPosts = postRepository.findCommunityPosts(communityId, pageable);
        List<PostResponse> responses = communityPosts.getContent().stream()
                .map(post -> postHelper.convertToPostResponse(post, hasUserReacted(userId, post.getId())))
                .toList();
        return enrichPostPage(communityPosts, responses);
    }

    private PostResponse enrichPostResponse(PostResponse response) {
        if (response == null || !StringUtils.hasText(response.getUserId())) {
            return response;
        }

        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(List.of(response.getUserId()));
        applyAuthorProfile(response, profiles.get(response.getUserId()));
        applyCommunityMetadata(response, loadCommunityNames(List.of(response)));
        return response;
    }

    private Page<PostResponse> enrichPostPage(Page<PostEntity> sourcePage, List<PostResponse> responses) {
        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(
                responses.stream().map(PostResponse::getUserId).toList());
        Map<Long, String> communityNames = loadCommunityNames(responses);

        responses.forEach(response -> {
            applyAuthorProfile(response, profiles.get(response.getUserId()));
            applyCommunityMetadata(response, communityNames);
        });

        return new PageImpl<>(responses, sourcePage.getPageable(), sourcePage.getTotalElements());
    }

    private void validateCanReadPost(String userId, PostEntity post) {
        if (post.getPostContext() == PostContextType.COMMUNITY) {
            if (post.getCommunityId() == null) {
                throw new IllegalStateException("Community post is missing communityId");
            }
            communityService.requireReadableCommunity(userId, post.getCommunityId());
            return;
        }

        if (userId.equals(post.getUserId())) {
            return;
        }

        if ("PUBLIC".equalsIgnoreCase(post.getVisibility())) {
            return;
        }

        if ("FRIEND".equalsIgnoreCase(post.getVisibility())) {
            List<String> friendIds = friendRepository.findAllAcceptedFriendIds(userId);
            if (friendIds.contains(post.getUserId())) {
                return;
            }
        }

        throw new ResourceNotFoundException("Post not found with id: " + post.getId());
    }

    private void applyAuthorProfile(PostResponse response, UserProfileSummary profile) {
        if (response == null || profile == null) {
            return;
        }

        response.setUsername(profile.getUsername());
        response.setFullName(profile.getFullName());
        response.setAvatarUrl(profile.getAvatarUrl());
    }

    private Map<Long, String> loadCommunityNames(List<PostResponse> responses) {
        List<Long> communityIds = responses.stream()
                .map(PostResponse::getCommunityId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (communityIds.isEmpty()) {
            return Map.of();
        }

        return communityService.getCommunityNamesByIds(communityIds);
    }

    private void applyCommunityMetadata(PostResponse response, Map<Long, String> communityNames) {
        if (response == null || response.getCommunityId() == null) {
            return;
        }

        response.setCommunityName(communityNames.get(response.getCommunityId()));
    }

    private void validatePostPayload(String content, List<PostMediaRequest> media, List<String> mediaUrls) {
        if (!StringUtils.hasText(content) && !hasAnyMedia(media, mediaUrls)) {
            throw new IllegalArgumentException("Post must contain content or at least one media item");
        }
    }

    private boolean hasAnyMedia(List<PostMediaRequest> media, List<String> mediaUrls) {
        if (media != null) {
            for (PostMediaRequest item : media) {
                if (item != null && StringUtils.hasText(item.getMediaUrl())) {
                    return true;
                }
            }
        }

        if (mediaUrls != null) {
            for (String mediaUrl : mediaUrls) {
                if (StringUtils.hasText(mediaUrl)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        return content.trim();
    }

    //----------------MEDIA OPERATIONS----------------
    private void replacePostMedia(PostEntity post, List<PostMediaRequest> media, List<String> mediaUrls) {
        List<PostMediaRequest> normalizedMedia = normalizeMediaRequests(media, mediaUrls);

        if (post.getMedia() == null) {
            post.setMedia(new ArrayList<>());
        } else {
            post.getMedia().clear();
        }

        for (PostMedia mediaItem : mapToEntities(post, normalizedMedia)) {
            post.getMedia().add(mediaItem);
        }
    }

    private List<PostMedia> mapToEntities(PostEntity post, List<PostMediaRequest> normalizedMedia) {
        List<PostMedia> items = new ArrayList<>();

        for (PostMediaRequest mediaItem : normalizedMedia) {
            if (mediaItem == null || !StringUtils.hasText(mediaItem.getMediaUrl())) {
                continue;
            }

            PostMedia item = new PostMedia();
            item.setPost(post);
            item.setPublicId(normalizeText(mediaItem.getPublicId()));
            item.setMediaUrl(mediaItem.getMediaUrl().trim());
            item.setMediaType(resolveMediaType(mediaItem.getMediaType(), mediaItem.getMediaUrl()));
            item.setProvider(resolveProvider(mediaItem.getProvider()));
            item.setWidth(mediaItem.getWidth());
            item.setHeight(mediaItem.getHeight());
            item.setBytes(mediaItem.getBytes());
            items.add(item);
        }

        return items;
    }

    private List<PostMediaRequest> normalizeMediaRequests(List<PostMediaRequest> media, List<String> mediaUrls) {
        List<PostMediaRequest> normalizedMedia = new ArrayList<>();

        if (media != null) {
            for (PostMediaRequest item : media) {
                if (item != null) {
                    normalizedMedia.add(item);
                }
            }
        }

        if (mediaUrls != null) {
            for (String mediaUrl : mediaUrls) {
                if (!StringUtils.hasText(mediaUrl)) {
                    continue;
                }

                PostMediaRequest legacyItem = new PostMediaRequest();
                legacyItem.setMediaUrl(mediaUrl.trim());
                legacyItem.setMediaType(inferMediaTypeFromUrl(mediaUrl));
                legacyItem.setProvider("CLOUDINARY");
                normalizedMedia.add(legacyItem);
            }
        }

        return normalizedMedia;
    }

    private List<String> extractRemovedPublicIds(List<PostMedia> currentMedia, List<PostMediaRequest> media, List<String> mediaUrls) {
        if (currentMedia == null || currentMedia.isEmpty()) {
            return List.of();
        }

        List<PostMediaRequest> normalizedMedia = normalizeMediaRequests(media, mediaUrls);
        Set<String> incomingPublicIds = new LinkedHashSet<>();
        Set<String> incomingUrls = new LinkedHashSet<>();

        for (PostMediaRequest item : normalizedMedia) {
            if (item == null) {
                continue;
            }
            if (StringUtils.hasText(item.getPublicId())) {
                incomingPublicIds.add(item.getPublicId().trim());
            }
            if (StringUtils.hasText(item.getMediaUrl())) {
                incomingUrls.add(item.getMediaUrl().trim());
            }
        }

        Set<String> removedPublicIds = new LinkedHashSet<>();
        for (PostMedia existingItem : currentMedia) {
            if (existingItem == null || !StringUtils.hasText(existingItem.getPublicId())) {
                continue;
            }

            boolean stillPresent = incomingPublicIds.contains(existingItem.getPublicId())
                    || (StringUtils.hasText(existingItem.getMediaUrl())
                            && incomingUrls.contains(existingItem.getMediaUrl()));

            if (!stillPresent) {
                removedPublicIds.add(existingItem.getPublicId());
            }
        }

        return new ArrayList<>(removedPublicIds);
    }

    private List<String> extractPublicIds(List<PostMedia> currentMedia) {
        if (currentMedia == null || currentMedia.isEmpty()) {
            return List.of();
        }

        Set<String> publicIds = new LinkedHashSet<>();
        for (PostMedia item : currentMedia) {
            if (item != null && StringUtils.hasText(item.getPublicId())) {
                publicIds.add(item.getPublicId().trim());
            }
        }

        return new ArrayList<>(publicIds);
    }

    private String resolveMediaType(String mediaType, String mediaUrl) {
        if (!StringUtils.hasText(mediaType)) {
            return inferMediaTypeFromUrl(mediaUrl);
        }

        String normalized = mediaType.trim().toUpperCase(Locale.ROOT);
        if (!"IMAGE".equals(normalized) && !"VIDEO".equals(normalized)) {
            throw new IllegalArgumentException("Invalid mediaType. Allowed values: IMAGE, VIDEO");
        }

        return normalized;
    }

    private String inferMediaTypeFromUrl(String mediaUrl) {
        if (!StringUtils.hasText(mediaUrl)) {
            return "IMAGE";
        }

        String normalizedUrl = mediaUrl.trim().toLowerCase(Locale.ROOT);
        if (normalizedUrl.contains(".mp4") || normalizedUrl.contains(".mov")) {
            return "VIDEO";
        }

        return "IMAGE";
    }

    private String resolveProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "CLOUDINARY";
        }
        return provider.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasUserReacted(String userId, Long postId) {
        if (!StringUtils.hasText(userId) || postId == null) {
            return false;
        }
        return reactionsRepository.existsByUserIdAndPostId(userId, postId);
    }

    private void publishPostCreatedNotification(String authorId, PostEntity savedPost) {
        String visibility = savedPost.getVisibility();
        if ("PRIVATE".equals(visibility)) {
            return;
        }

        List<String> recipientIds = friendRepository.findAllAcceptedFriendIds(authorId);
        if (recipientIds.isEmpty()) {
            return;
        }

        String content = savedPost.getContent();
        String preview;
        if (content != null && !content.isBlank()) {
            preview = content.length() > 100 ? content.substring(0, 100) : content;
        } else {
            preview = "Da dang bai viet moi";
        }

        notificationEventProducer.publishPostCreated(authorId, savedPost.getId(), preview, recipientIds);
    }

}
