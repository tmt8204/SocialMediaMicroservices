package com.socialmedia.social_media_social_service.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryCreateRequest;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryFeedGroupResponse;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryMediaRequest;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryResponse;
import com.socialmedia.social_media_social_service.dto.StoryDTO.StoryViewResponse;
import com.socialmedia.social_media_social_service.entities.StoryEntity;
import com.socialmedia.social_media_social_service.entities.StoryMedia;
import com.socialmedia.social_media_social_service.entities.StoryViewEntity;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.helpers.StoryHelper;
import com.socialmedia.social_media_social_service.repositories.FriendRepository;
import com.socialmedia.social_media_social_service.repositories.StoryMediaRepository;
import com.socialmedia.social_media_social_service.repositories.StoryRepository;
import com.socialmedia.social_media_social_service.repositories.StoryViewRepository;

import lombok.AllArgsConstructor;

@Service
@Transactional
@AllArgsConstructor
public class StoryService {

    private static final long STORY_TTL_MILLIS = 24L * 60L * 60L * 1000L;

    private final StoryRepository storyRepository;
    private final StoryMediaRepository storyMediaRepository;
    private final StoryViewRepository storyViewRepository;
    private final FriendRepository friendRepository;
    private final StoryHelper storyHelper;
    private final UserProfileClient userProfileClient;
    private final MediaServiceClient mediaServiceClient;

    public StoryResponse createStory(String userId, StoryCreateRequest request) {
        validateCreateRequest(request);

        Date now = new Date();
        StoryEntity story = new StoryEntity();
        story.setUserId(userId);
        story.setCaption(normalizeOptionalText(request.getCaption(), 500));
        story.setVisibility(storyHelper.resolveVisibility(request.getVisibility()));
        story.setCreatedAt(now);
        story.setUpdatedAt(now);
        story.setExpiresAt(new Date(now.getTime() + STORY_TTL_MILLIS));

        story.getMedia().clear();
        story.getMedia().addAll(mapToMediaEntities(story, request.getMedia()));

        StoryEntity savedStory = storyRepository.save(story);
        return enrichStoryResponse(userId, savedStory, true);
    }

    @Transactional(readOnly = true)
    public List<StoryFeedGroupResponse> getFeed(String userId) {
        Date now = new Date();
        List<StoryEntity> candidates = loadFeedCandidates(userId, now);
        if (candidates.isEmpty()) {
            return List.of();
        }

        return buildFeedGroups(userId, candidates);
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> getStoriesByUser(String viewerId, String ownerId) {
        Date now = new Date();
        List<StoryEntity> stories = storyRepository.findByUserIdAndIsDeletedFalseAndExpiresAtAfterOrderByCreatedAtDesc(ownerId, now);
        if (stories.isEmpty()) {
            return List.of();
        }

        List<String> acceptedFriendIds = viewerId.equals(ownerId)
                ? List.of()
                : friendRepository.findAllAcceptedFriendIds(viewerId);
        Set<Long> viewedStoryIds = loadViewedStoryIds(viewerId, extractStoryIds(stories));
        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(List.of(ownerId));
        UserProfileSummary ownerProfile = profiles.get(ownerId);

        List<StoryResponse> responses = new ArrayList<>();
        for (StoryEntity story : stories) {
            if (!canViewStoryDirectly(viewerId, story, acceptedFriendIds)) {
                continue;
            }
            responses.add(storyHelper.convertToStoryResponse(
                    story,
                    isViewedByCurrentUser(viewerId, story.getId(), story.getUserId(), viewedStoryIds),
                    ownerProfile));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public StoryResponse getStoryById(String viewerId, Long storyId) {
        StoryEntity story = getActiveStory(storyId);
        validateCanViewStory(viewerId, story);

        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(List.of(story.getUserId()));
        return storyHelper.convertToStoryResponse(
                story,
                isViewedByCurrentUser(viewerId, story),
                profiles.get(story.getUserId()));
    }

    public StoryViewResponse markStoryViewed(String viewerId, Long storyId) {
        StoryEntity story = getActiveStory(storyId);
        validateCanViewStory(viewerId, story);

        Date now = new Date();
        if (viewerId.equals(story.getUserId())) {
            return new StoryViewResponse(storyId, viewerId, null, null, null, now);
        }

        StoryViewEntity storyView = storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId).orElse(null);
        if (storyView != null) {
            return storyHelper.convertToStoryViewResponse(storyView, null);
        }

        StoryViewEntity newStoryView = new StoryViewEntity();
        newStoryView.setStoryId(storyId);
        newStoryView.setViewerId(viewerId);
        newStoryView.setCreatedAt(now);
        newStoryView.setUpdatedAt(now);

        try {
            StoryViewEntity savedView = storyViewRepository.save(newStoryView);
            storyRepository.incrementViewCount(storyId, now);
            return storyHelper.convertToStoryViewResponse(savedView, null);
        } catch (DataIntegrityViolationException ex) {
            StoryViewEntity existingView = storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId)
                    .orElseThrow(() -> ex);
            return storyHelper.convertToStoryViewResponse(existingView, null);
        }
    }

    @Transactional(readOnly = true)
    public Page<StoryViewResponse> getStoryViewers(String ownerId, Long storyId, Pageable pageable) {
            StoryEntity story = storyRepository.findByIdAndUserId(storyId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found with id: " + storyId));

            Page<StoryViewEntity> viewerPage = storyViewRepository.findByStoryIdOrderByCreatedAtDesc(storyId, pageable);
            List<String> viewerIds = viewerPage.getContent().stream()
                .map(StoryViewEntity::getViewerId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
            Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(viewerIds);

            List<StoryViewResponse> responses = viewerPage.getContent().stream()
                .map(view -> storyHelper.convertToStoryViewResponse(view, profiles.get(view.getViewerId())))
                .toList();

            return new PageImpl<>(responses, pageable, viewerPage.getTotalElements());
        }

    public void deleteStory(String userId, Long storyId) {
        StoryEntity story = storyRepository.findByIdAndUserId(storyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found with id: " + storyId));

        if (!story.isDeleted()) {
            story.setDeleted(true);
            story.setUpdatedAt(new Date());
            storyRepository.save(story);
        }
    }

    public int cleanupStories(int batchSize, long gracePeriodMs) {
        Date cutoff = new Date(System.currentTimeMillis() - Math.max(gracePeriodMs, 0L));
        List<StoryEntity> candidates = storyRepository.findCleanupCandidates(cutoff, PageRequest.of(0, Math.max(batchSize, 1)));
        if (candidates.isEmpty()) {
            return 0;
        }

        List<Long> storyIds = extractStoryIds(candidates);
        List<StoryMedia> mediaItems = storyMediaRepository.findByStory_IdIn(storyIds);
        List<String> publicIds = mediaItems.stream()
                .map(StoryMedia::getPublicId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        mediaServiceClient.deleteMediaByPublicIds(publicIds);
        storyViewRepository.deleteByStoryIdIn(storyIds);
        storyMediaRepository.deleteByStory_IdIn(storyIds);
        storyRepository.deleteAllByIdInBatch(storyIds);
        return storyIds.size();
    }

    private StoryResponse enrichStoryResponse(String viewerId, StoryEntity story, boolean includeOwnerAsViewed) {
        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(List.of(story.getUserId()));
        boolean viewed = includeOwnerAsViewed && viewerId.equals(story.getUserId()) || isViewedByCurrentUser(viewerId, story);
        return storyHelper.convertToStoryResponse(story, viewed, profiles.get(story.getUserId()));
    }

    private List<StoryEntity> loadFeedCandidates(String userId, Date now) {
        List<StoryEntity> ownStories = storyRepository.findByUserIdAndIsDeletedFalseAndExpiresAtAfterOrderByCreatedAtDesc(userId, now);
        List<String> friendIds = friendRepository.findAllAcceptedFriendIds(userId);
        if (friendIds.isEmpty()) {
            return ownStories;
        }

        List<StoryEntity> feedCandidates = storyRepository.findFeedCandidates(userId, friendIds, now);
        if (feedCandidates.isEmpty()) {
            return ownStories;
        }
        return feedCandidates.stream()
                .filter(story -> userId.equals(story.getUserId())
                        || "PUBLIC".equalsIgnoreCase(story.getVisibility())
                        || "FRIEND".equalsIgnoreCase(story.getVisibility()))
                .toList();
    }

    private List<StoryFeedGroupResponse> buildFeedGroups(String viewerId, List<StoryEntity> stories) {
        List<Long> storyIds = extractStoryIds(stories);
        Set<Long> viewedStoryIds = loadViewedStoryIds(viewerId, storyIds);

        List<String> ownerIds = stories.stream()
                .map(StoryEntity::getUserId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(ownerIds);

        Map<String, StoryFeedGroupResponse> grouped = new LinkedHashMap<>();
        for (StoryEntity story : stories) {
            UserProfileSummary ownerProfile = profiles.get(story.getUserId());
            StoryFeedGroupResponse group = grouped.computeIfAbsent(story.getUserId(), ownerId -> {
                StoryFeedGroupResponse response = new StoryFeedGroupResponse();
                response.setUserId(ownerId);
                if (ownerProfile != null) {
                    response.setUsername(ownerProfile.getUsername());
                    response.setFullName(ownerProfile.getFullName());
                    response.setAvatarUrl(ownerProfile.getAvatarUrl());
                }
                response.setHasUnseen(false);
                response.setLatestStoryAt(story.getCreatedAt());
                return response;
            });

            boolean viewedByCurrentUser = isViewedByCurrentUser(viewerId, story.getId(), story.getUserId(), viewedStoryIds);
            StoryResponse storyResponse = storyHelper.convertToStoryResponse(story, viewedByCurrentUser, ownerProfile);
            group.getStories().add(storyResponse);
            if (group.getLatestStoryAt() == null || story.getCreatedAt().after(group.getLatestStoryAt())) {
                group.setLatestStoryAt(story.getCreatedAt());
            }
            if (!viewedByCurrentUser) {
                group.setHasUnseen(true);
            }
        }

        List<StoryFeedGroupResponse> groups = new ArrayList<>(grouped.values());
        groups.sort(Comparator
                .comparing((StoryFeedGroupResponse group) -> !viewerId.equals(group.getUserId()))
                .thenComparing((StoryFeedGroupResponse group) -> !group.isHasUnseen())
                .thenComparing(StoryFeedGroupResponse::getLatestStoryAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return groups;
    }

    private Set<Long> loadViewedStoryIds(String viewerId, List<Long> storyIds) {
        if (storyIds.isEmpty()) {
            return Set.of();
        }

        return storyViewRepository.findByViewerIdAndStoryIdIn(viewerId, storyIds).stream()
                .map(StoryViewEntity::getStoryId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean isViewedByCurrentUser(String viewerId, StoryEntity story) {
        return isViewedByCurrentUser(viewerId, story.getId(), story.getUserId(), null);
    }

    private boolean isViewedByCurrentUser(String viewerId, Long storyId, String ownerId, Set<Long> viewedStoryIds) {
        if (viewerId.equals(ownerId)) {
            return true;
        }
        if (viewedStoryIds != null) {
            return viewedStoryIds.contains(storyId);
        }
        return storyViewRepository.existsByStoryIdAndViewerId(storyId, viewerId);
    }

    private StoryEntity getActiveStory(Long storyId) {
        StoryEntity story = storyRepository.findByIdAndIsDeletedFalse(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found with id: " + storyId));
        ensureStoryActive(story);
        return story;
    }

    private void validateCanViewStory(String viewerId, StoryEntity story) {
        List<String> acceptedFriendIds = viewerId.equals(story.getUserId())
                ? List.of()
                : friendRepository.findAllAcceptedFriendIds(viewerId);
        if (!canViewStoryDirectly(viewerId, story, acceptedFriendIds)) {
            throw new ResourceNotFoundException("Story not found with id: " + story.getId());
        }
    }

    private boolean canViewStoryDirectly(String viewerId, StoryEntity story, List<String> acceptedFriendIds) {
        if (viewerId.equals(story.getUserId())) {
            return true;
        }

        ensureStoryActive(story);

        String visibility = story.getVisibility();
        if ("PRIVATE".equalsIgnoreCase(visibility)) {
            return false;
        }
        if ("FRIEND".equalsIgnoreCase(visibility)) {
            return acceptedFriendIds.contains(story.getUserId());
        }
        return true;
    }

    private void ensureStoryActive(StoryEntity story) {
        Date now = new Date();
        if (story.isDeleted() || story.getExpiresAt() == null || !story.getExpiresAt().after(now)) {
            throw new ResourceNotFoundException("Story not found with id: " + story.getId());
        }
    }

    private void validateCreateRequest(StoryCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Story request is required");
        }

        String caption = normalizeOptionalText(request.getCaption(), 500);
        List<StoryMediaRequest> media = request.getMedia() == null ? List.of() : request.getMedia();
        if (!StringUtils.hasText(caption) && media.isEmpty()) {
            throw new IllegalArgumentException("Story must contain caption or at least one media item");
        }
        if (media.size() > 1) {
            throw new IllegalArgumentException("Phase 1 only supports one media item per story");
        }
    }

    private List<StoryMedia> mapToMediaEntities(StoryEntity story, List<StoryMediaRequest> mediaRequests) {
        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return List.of();
        }

        List<StoryMedia> mediaItems = new ArrayList<>();
        for (StoryMediaRequest mediaRequest : mediaRequests) {
            StoryMedia item = new StoryMedia();
            item.setStory(story);
            item.setPublicId(normalizeText(mediaRequest.getPublicId()));
            item.setMediaUrl(normalizeRequiredText(mediaRequest.getMediaUrl(), "mediaUrl"));
            item.setMediaType(resolveMediaType(mediaRequest.getMediaType()));
            item.setProvider(resolveProvider(mediaRequest.getProvider()));
            item.setWidth(mediaRequest.getWidth());
            item.setHeight(mediaRequest.getHeight());
            item.setBytes(mediaRequest.getBytes());
            item.setCreatedAt(story.getCreatedAt());
            item.setUpdatedAt(story.getUpdatedAt());
            mediaItems.add(item);
        }
        return mediaItems;
    }

    private List<Long> extractStoryIds(Collection<StoryEntity> stories) {
        return stories.stream().map(StoryEntity::getId).toList();
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Field length must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveMediaType(String mediaType) {
        if (!StringUtils.hasText(mediaType)) {
            return "IMAGE";
        }

        String normalized = mediaType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"IMAGE".equals(normalized) && !"VIDEO".equals(normalized)) {
            throw new IllegalArgumentException("Invalid mediaType. Allowed values: IMAGE, VIDEO");
        }
        return normalized;
    }

    private String resolveProvider(String provider) {
        return StringUtils.hasText(provider) ? provider.trim().toUpperCase(java.util.Locale.ROOT) : "CLOUDINARY";
    }
}