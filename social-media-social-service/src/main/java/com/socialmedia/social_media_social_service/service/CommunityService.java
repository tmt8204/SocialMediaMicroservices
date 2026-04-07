package com.socialmedia.social_media_social_service.service;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityActionResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityJoinRequestResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityMemberResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityMineResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityOverviewResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CommunityResponse;
import com.socialmedia.social_media_social_service.dto.CommunityDTO.CreateCommunityRequest;
import com.socialmedia.social_media_social_service.dto.ProfileDTO.UserProfileSummary;
import com.socialmedia.social_media_social_service.entities.CommunityEntity;
import com.socialmedia.social_media_social_service.entities.CommunityMemberEntity;
import com.socialmedia.social_media_social_service.entities.enums.CommunityMemberStatus;
import com.socialmedia.social_media_social_service.entities.enums.CommunityPrivacy;
import com.socialmedia.social_media_social_service.entities.enums.CommunityRole;
import com.socialmedia.social_media_social_service.entities.enums.PostContextType;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.repositories.CommunityMemberRepository;
import com.socialmedia.social_media_social_service.repositories.CommunityRepository;
import com.socialmedia.social_media_social_service.repositories.PostRepository;

import lombok.AllArgsConstructor;

@Service
@Transactional
@AllArgsConstructor
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final UserProfileClient userProfileClient;
    private final PostRepository postRepository;

    public CommunityResponse createCommunity(String userId, CreateCommunityRequest request) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");

        CommunityEntity community = new CommunityEntity();
        community.setName(normalizeRequiredText(request.getName(), "name", 150));
        community.setDescription(normalizeNullableText(request.getDescription()));
        community.setCoverUrl(normalizeNullableText(request.getCoverUrl()));
        community.setPrivacy(resolvePrivacy(request.getPrivacy()));
        community.setMemberCount(1);
        community.setCreatedBy(currentUserId);
        community.setCreatedAt(new Date());
        community.setUpdatedAt(new Date());

        CommunityEntity savedCommunity = communityRepository.save(community);

        CommunityMemberEntity creatorMembership = new CommunityMemberEntity();
        creatorMembership.setCommunityId(savedCommunity.getId());
        creatorMembership.setUserId(currentUserId);
        creatorMembership.setRole(CommunityRole.ADMIN);
        creatorMembership.setStatus(CommunityMemberStatus.APPROVED);
        creatorMembership.setJoinedAt(new Date());
        creatorMembership.setCreatedAt(new Date());
        creatorMembership.setUpdatedAt(new Date());
        communityMemberRepository.save(creatorMembership);

        return toCommunityResponse(savedCommunity, creatorMembership, getProfile(currentUserId));
    }

    @Transactional(readOnly = true)
    public CommunityResponse getCommunityDetail(String userId, Long communityId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);
        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, currentUserId)
                .orElse(null);

        return toCommunityResponse(community, membership, getProfile(community.getCreatedBy()));
    }

    public CommunityResponse joinCommunity(String userId, Long communityId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);
        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, currentUserId)
                .orElse(null);
        Date now = new Date();

        if (membership != null) {
            if (membership.getStatus() == CommunityMemberStatus.APPROVED || membership.getStatus() == CommunityMemberStatus.PENDING) {
                throw new IllegalArgumentException("You have already joined or requested to join this community");
            }

            updateMembershipForJoin(membership, community, now);
            community.setUpdatedAt(now);
            communityRepository.save(community);
            communityMemberRepository.save(membership);
            return toCommunityResponse(community, membership, getProfile(community.getCreatedBy()));
        }

        CommunityMemberEntity newMembership = new CommunityMemberEntity();
        newMembership.setCommunityId(community.getId());
        newMembership.setUserId(currentUserId);
        newMembership.setRole(CommunityRole.MEMBER);
        newMembership.setStatus(resolveJoinStatus(community));
        newMembership.setJoinedAt(newMembership.getStatus() == CommunityMemberStatus.APPROVED ? now : null);
        newMembership.setCreatedAt(now);
        newMembership.setUpdatedAt(now);

        if (newMembership.getStatus() == CommunityMemberStatus.APPROVED) {
            community.setMemberCount(community.getMemberCount() + 1);
        }
        community.setUpdatedAt(now);

        communityRepository.save(community);
        communityMemberRepository.save(newMembership);

        return toCommunityResponse(community, newMembership, getProfile(community.getCreatedBy()));
    }

    public void leaveCommunity(String userId, Long communityId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);
        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Community membership not found for user: " + currentUserId));

        if (membership.getRole() == CommunityRole.ADMIN && membership.getStatus() == CommunityMemberStatus.APPROVED) {
            throw new IllegalArgumentException("ADMIN cannot leave the community");
        }

        if (membership.getStatus() == CommunityMemberStatus.APPROVED && community.getMemberCount() > 0) {
            community.setMemberCount(community.getMemberCount() - 1);
        }

        Date now = new Date();
        community.setUpdatedAt(now);
        membership.setStatus(CommunityMemberStatus.LEFT);
        membership.setUpdatedAt(now);
        membership.setJoinedAt(null);

        communityRepository.save(community);
        communityMemberRepository.save(membership);
    }

    public void deleteCommunity(String userId, Long communityId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);
        CommunityMemberEntity membership = requireAdminMembership(communityId, currentUserId);

        if (membership.getStatus() != CommunityMemberStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved ADMIN can delete community");
        }

        community.setDeleted(true);
        community.setUpdatedAt(new Date());
        communityRepository.save(community);
        postRepository.softDeleteByCommunityIdAndPostContext(communityId, PostContextType.COMMUNITY);
    }

    public CommunityResponse updateCover(String userId, Long communityId, String coverUrl) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);
        requireApprovedAdminMembership(communityId, currentUserId);

        community.setCoverUrl(normalizeRequiredText(coverUrl, "coverUrl", 2048));
        community.setUpdatedAt(new Date());
        CommunityEntity updatedCommunity = communityRepository.save(community);

        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, currentUserId).orElse(null);
        return toCommunityResponse(updatedCommunity, membership, getProfile(updatedCommunity.getCreatedBy()));
    }

    public CommunityResponse updatePrivacy(String userId, Long communityId, String privacy) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);
        requireApprovedAdminMembership(communityId, currentUserId);

        community.setPrivacy(resolvePrivacy(privacy));
        community.setUpdatedAt(new Date());
        CommunityEntity updatedCommunity = communityRepository.save(community);

        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, currentUserId).orElse(null);
        return toCommunityResponse(updatedCommunity, membership, getProfile(updatedCommunity.getCreatedBy()));
    }

        @Transactional(readOnly = true)
        public Page<CommunityMemberResponse> getMembers(String userId, Long communityId, Pageable pageable) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);

        if (community.getPrivacy() == CommunityPrivacy.PRIVATE) {
            requireApprovedMembership(communityId, currentUserId);
        }

        Page<CommunityMemberEntity> page = communityMemberRepository.findByCommunityIdAndStatusOrderByJoinedAtAsc(
            communityId,
            CommunityMemberStatus.APPROVED,
            pageable);

        List<CommunityMemberResponse> responses = page.getContent().stream()
            .map(this::toCommunityMemberResponse)
            .toList();

        enrichMemberResponses(responses);
        return new PageImpl<>(responses, pageable, page.getTotalElements());
        }

        @Transactional(readOnly = true)
        public Page<CommunityJoinRequestResponse> getPendingRequests(String userId, Long communityId, Pageable pageable) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        requireApprovedAdminMembership(communityId, currentUserId);

        Page<CommunityMemberEntity> page = communityMemberRepository.findByCommunityIdAndStatusOrderByCreatedAtDesc(
            communityId,
            CommunityMemberStatus.PENDING,
            pageable);

        List<CommunityJoinRequestResponse> responses = page.getContent().stream()
            .map(this::toCommunityJoinRequestResponse)
            .toList();

        enrichJoinRequestResponses(responses);
        return new PageImpl<>(responses, pageable, page.getTotalElements());
        }

        public CommunityActionResponse approveJoinRequest(String adminId, Long communityId, String targetUserId) {
        String currentUserId = normalizeRequiredUserId(adminId, "userId");
        String memberUserId = normalizeRequiredUserId(targetUserId, "targetUserId");
        requireApprovedAdminMembership(communityId, currentUserId);

        CommunityEntity community = getActiveCommunity(communityId);
        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Pending community request not found for user: " + memberUserId));

        if (membership.getStatus() != CommunityMemberStatus.PENDING) {
            throw new IllegalArgumentException("Only pending request can be approved");
        }

        Date now = new Date();
        membership.setStatus(CommunityMemberStatus.APPROVED);
        membership.setJoinedAt(now);
        membership.setUpdatedAt(now);
        community.setMemberCount(community.getMemberCount() + 1);
        community.setUpdatedAt(now);

        communityMemberRepository.save(membership);
        communityRepository.save(community);

        return CommunityActionResponse.builder()
            .communityId(communityId)
            .userId(memberUserId)
            .status(CommunityMemberStatus.APPROVED.name())
            .message("Community request approved successfully")
            .build();
        }

        public CommunityActionResponse rejectJoinRequest(String adminId, Long communityId, String targetUserId) {
        String currentUserId = normalizeRequiredUserId(adminId, "userId");
        String memberUserId = normalizeRequiredUserId(targetUserId, "targetUserId");
        requireApprovedAdminMembership(communityId, currentUserId);

        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Pending community request not found for user: " + memberUserId));

        if (membership.getStatus() != CommunityMemberStatus.PENDING) {
            throw new IllegalArgumentException("Only pending request can be rejected");
        }

        membership.setStatus(CommunityMemberStatus.REJECTED);
        membership.setJoinedAt(null);
        membership.setUpdatedAt(new Date());
        communityMemberRepository.save(membership);

        return CommunityActionResponse.builder()
            .communityId(communityId)
            .userId(memberUserId)
            .status(CommunityMemberStatus.REJECTED.name())
            .message("Community request rejected successfully")
            .build();
        }

        @Transactional(readOnly = true)
        public CommunityMineResponse getMyCommunities(String userId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");

        List<CommunityMemberEntity> memberships = communityMemberRepository
            .findByUserIdAndStatusOrderByUpdatedAtDesc(currentUserId, CommunityMemberStatus.APPROVED, Pageable.unpaged())
            .getContent();

        Map<Long, CommunityEntity> communitiesById = getCommunitiesByIds(
            memberships.stream().map(CommunityMemberEntity::getCommunityId).toList());

        List<CommunityOverviewResponse> owned = memberships.stream()
            .filter(membership -> membership.getRole() == CommunityRole.ADMIN)
            .map(membership -> toOverviewResponse(communitiesById.get(membership.getCommunityId()), membership))
            .filter(java.util.Objects::nonNull)
            .toList();

        List<CommunityOverviewResponse> joined = memberships.stream()
            .filter(membership -> membership.getRole() != CommunityRole.ADMIN)
            .map(membership -> toOverviewResponse(communitiesById.get(membership.getCommunityId()), membership))
            .filter(java.util.Objects::nonNull)
            .toList();

        return CommunityMineResponse.builder()
            .owned(owned)
            .joined(joined)
            .build();
        }

        @Transactional(readOnly = true)
        public Page<CommunityOverviewResponse> discoverCommunities(String userId, Pageable pageable) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        List<Long> excludedIds = communityMemberRepository.findCommunityIdsByUserIdAndStatuses(
            currentUserId,
            List.of(CommunityMemberStatus.APPROVED, CommunityMemberStatus.PENDING));

        Page<CommunityEntity> page = excludedIds.isEmpty()
            ? communityRepository.findByIsDeletedFalseOrderByMemberCountDesc(pageable)
            : communityRepository.findDiscoverCommunities(excludedIds, pageable);

        List<CommunityOverviewResponse> responses = page.getContent().stream()
            .map(community -> toOverviewResponse(community, null))
            .toList();

        return new PageImpl<>(responses, pageable, page.getTotalElements());
        }

    private void updateMembershipForJoin(CommunityMemberEntity membership, CommunityEntity community, Date now) {
        CommunityMemberStatus nextStatus = resolveJoinStatus(community);
        membership.setStatus(nextStatus);
        membership.setRole(CommunityRole.MEMBER);
        membership.setUpdatedAt(now);
        membership.setJoinedAt(nextStatus == CommunityMemberStatus.APPROVED ? now : null);

        if (nextStatus == CommunityMemberStatus.APPROVED) {
            community.setMemberCount(community.getMemberCount() + 1);
        }
    }

    private CommunityMemberStatus resolveJoinStatus(CommunityEntity community) {
        return community.getPrivacy() == CommunityPrivacy.PUBLIC
                ? CommunityMemberStatus.APPROVED
                : CommunityMemberStatus.PENDING;
    }

    private CommunityMemberEntity requireAdminMembership(Long communityId, String userId) {
        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Only ADMIN can manage this community"));

        if (membership.getRole() != CommunityRole.ADMIN) {
            throw new IllegalArgumentException("Only ADMIN can manage this community");
        }

        return membership;
    }

    private void requireApprovedAdminMembership(Long communityId, String userId) {
        CommunityMemberEntity membership = requireAdminMembership(communityId, userId);
        if (membership.getStatus() != CommunityMemberStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved ADMIN can manage this community");
        }
    }

    private CommunityMemberEntity requireApprovedMembership(Long communityId, String userId) {
        CommunityMemberEntity membership = communityMemberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this community"));

        if (membership.getStatus() != CommunityMemberStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved member can access this community resource");
        }

        return membership;
    }

    @Transactional(readOnly = true)
    public CommunityEntity requireReadableCommunity(String userId, Long communityId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);
        if (community.getPrivacy() == CommunityPrivacy.PRIVATE) {
            requireApprovedMembership(communityId, currentUserId);
        }
        return community;
    }

    @Transactional(readOnly = true)
    public CommunityEntity requireWritableCommunity(String userId, Long communityId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        CommunityEntity community = getActiveCommunity(communityId);
        requireApprovedMembership(communityId, currentUserId);
        return community;
    }

    @Transactional(readOnly = true)
    public List<Long> getJoinedCommunityIds(String userId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        return communityMemberRepository.findCommunityIdsByUserIdAndStatuses(
                currentUserId,
                List.of(CommunityMemberStatus.APPROVED));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getCommunityNamesByIds(Collection<Long> communityIds) {
        return getCommunitiesByIds(communityIds).values().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CommunityEntity::getId,
                        CommunityEntity::getName,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private CommunityEntity getActiveCommunity(Long communityId) {
        if (communityId == null) {
            throw new IllegalArgumentException("communityId is required");
        }

        return communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));
    }

    private UserProfileSummary getProfile(String userId) {
        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(java.util.List.of(userId));
        return profiles.get(userId);
    }

    private Map<Long, CommunityEntity> getCommunitiesByIds(Collection<Long> communityIds) {
        if (communityIds == null || communityIds.isEmpty()) {
            return Map.of();
        }

        return communityRepository.findAllById(communityIds).stream()
                .filter(community -> !community.isDeleted())
                .collect(java.util.stream.Collectors.toMap(CommunityEntity::getId, community -> community, (left, right) -> left, LinkedHashMap::new));
    }

    private CommunityOverviewResponse toOverviewResponse(CommunityEntity community, CommunityMemberEntity membership) {
        if (community == null || community.isDeleted()) {
            return null;
        }

        return CommunityOverviewResponse.builder()
                .id(community.getId())
                .name(community.getName())
                .coverUrl(community.getCoverUrl())
                .privacy(community.getPrivacy().name())
                .memberCount(community.getMemberCount())
                .myRole(membership == null ? null : membership.getRole().name())
                .myStatus(membership == null ? "NOT_JOINED" : membership.getStatus().name())
                .build();
    }

    private CommunityMemberResponse toCommunityMemberResponse(CommunityMemberEntity membership) {
        return CommunityMemberResponse.builder()
                .communityId(membership.getCommunityId())
                .userId(membership.getUserId())
                .role(membership.getRole().name())
                .status(membership.getStatus().name())
                .joinedAt(membership.getJoinedAt())
                .build();
    }

    private CommunityJoinRequestResponse toCommunityJoinRequestResponse(CommunityMemberEntity membership) {
        return CommunityJoinRequestResponse.builder()
                .communityId(membership.getCommunityId())
                .userId(membership.getUserId())
                .status(membership.getStatus().name())
                .requestedAt(membership.getCreatedAt())
                .updatedAt(membership.getUpdatedAt())
                .build();
    }

    private void enrichMemberResponses(List<CommunityMemberResponse> responses) {
        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(
                responses.stream().map(CommunityMemberResponse::getUserId).toList());

        responses.forEach(response -> {
            UserProfileSummary profile = profiles.get(response.getUserId());
            if (profile != null) {
                response.setUsername(profile.getUsername());
                response.setFullName(profile.getFullName());
                response.setAvatarUrl(profile.getAvatarUrl());
            }
        });
    }

    private void enrichJoinRequestResponses(List<CommunityJoinRequestResponse> responses) {
        Map<String, UserProfileSummary> profiles = userProfileClient.getProfilesByUserIds(
                responses.stream().map(CommunityJoinRequestResponse::getUserId).toList());

        responses.forEach(response -> {
            UserProfileSummary profile = profiles.get(response.getUserId());
            if (profile != null) {
                response.setUsername(profile.getUsername());
                response.setFullName(profile.getFullName());
                response.setAvatarUrl(profile.getAvatarUrl());
            }
        });
    }

    private CommunityResponse toCommunityResponse(CommunityEntity community,
                                                  CommunityMemberEntity membership,
                                                  UserProfileSummary creatorProfile) {
        return CommunityResponse.builder()
                .id(community.getId())
                .name(community.getName())
                .description(community.getDescription())
                .coverUrl(community.getCoverUrl())
                .privacy(community.getPrivacy().name())
                .memberCount(community.getMemberCount())
                .createdBy(community.getCreatedBy())
                .createdByUsername(creatorProfile == null ? null : creatorProfile.getUsername())
                .createdByFullName(creatorProfile == null ? null : creatorProfile.getFullName())
                .createdByAvatarUrl(creatorProfile == null ? null : creatorProfile.getAvatarUrl())
                .myRole(membership == null ? null : membership.getRole().name())
                .myStatus(resolveMyStatus(membership))
                .isMember(membership != null && membership.getStatus() == CommunityMemberStatus.APPROVED)
                .canManage(membership != null
                        && membership.getRole() == CommunityRole.ADMIN
                        && membership.getStatus() == CommunityMemberStatus.APPROVED)
                .createdAt(community.getCreatedAt())
                .updatedAt(community.getUpdatedAt())
                .build();
    }

    private String resolveMyStatus(CommunityMemberEntity membership) {
        if (membership == null) {
            return "NOT_JOINED";
        }

        return membership.getStatus().name();
    }

    private String normalizeRequiredUserId(String userId, String fieldName) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return userId.trim();
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private CommunityPrivacy resolvePrivacy(String privacy) {
        String normalized = normalizeRequiredText(privacy, "privacy", 50).toUpperCase();
        try {
            return CommunityPrivacy.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid privacy. Allowed values: PUBLIC, PRIVATE");
        }
    }
}