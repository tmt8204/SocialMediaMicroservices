package com.socialmedia.social_media_social_service.service;

import java.util.Date;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_social_service.dto.FriendDTO.FriendActionResponse;
import com.socialmedia.social_media_social_service.dto.FriendDTO.FriendListItemResponse;
import com.socialmedia.social_media_social_service.dto.FriendDTO.FriendRequestResponse;
import com.socialmedia.social_media_social_service.dto.FriendDTO.RelationshipStatusResponse;
import com.socialmedia.social_media_social_service.entities.FriendEntity;
import com.socialmedia.social_media_social_service.entities.enums.FriendStatus;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.repositories.FriendRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;

    public FriendActionResponse sendFriendRequest(String userId, String targetUserId) {
        String requesterId = normalizeRequiredUserId(userId, "userId");
        String receiverId = normalizeRequiredUserId(targetUserId, "targetUserId");
        validateDifferentUsers(requesterId, receiverId);

        String pairKey = buildPairKey(requesterId, receiverId);
        Date now = new Date();

        FriendEntity relationship = friendRepository.findByPairKey(pairKey).orElse(null);
        if (relationship == null) {
            FriendEntity newRelationship = new FriendEntity();
            newRelationship.setUserId(requesterId);
            newRelationship.setFriendTo(receiverId);
            newRelationship.setPairKey(pairKey);
            newRelationship.setStatus(FriendStatus.PENDING);
            newRelationship.setActionBy(requesterId);
            newRelationship.setCreatedAt(now);
            newRelationship.setUpdatedAt(now);
            friendRepository.save(newRelationship);

            return buildActionResponse(requesterId, receiverId, "PENDING_SENT", "Friend request sent successfully");
        }

        return handleExistingRelationship(requesterId, receiverId, relationship, now);
    }

    public FriendActionResponse acceptFriendRequest(String userId, String requesterId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        String senderId = normalizeRequiredUserId(requesterId, "requesterId");
        validateDifferentUsers(currentUserId, senderId);

        FriendEntity relationship = friendRepository.findRelationshipBetween(currentUserId, senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request not found between users"));

        if (relationship.getStatus() != FriendStatus.PENDING || !isSameDirection(relationship, senderId, currentUserId)) {
            throw new IllegalArgumentException("No pending friend request from user: " + senderId);
        }

        relationship.setStatus(FriendStatus.ACCEPTED);
        relationship.setActionBy(currentUserId);
        relationship.setUpdatedAt(new Date());
        friendRepository.save(relationship);

        return buildActionResponse(currentUserId, senderId, "FRIEND", "Friend request accepted");
    }

    public FriendActionResponse rejectFriendRequest(String userId, String requesterId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        String senderId = normalizeRequiredUserId(requesterId, "requesterId");
        validateDifferentUsers(currentUserId, senderId);

        FriendEntity relationship = friendRepository.findRelationshipBetween(currentUserId, senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request not found between users"));

        if (relationship.getStatus() != FriendStatus.PENDING || !isSameDirection(relationship, senderId, currentUserId)) {
            throw new IllegalArgumentException("No pending friend request from user: " + senderId);
        }

        relationship.setStatus(FriendStatus.REJECTED);
        relationship.setActionBy(currentUserId);
        relationship.setUpdatedAt(new Date());
        friendRepository.save(relationship);

        return buildActionResponse(currentUserId, senderId, "REJECTED", "Friend request rejected");
    }

    public FriendActionResponse cancelFriendRequest(String userId, String targetUserId) {
        String requesterId = normalizeRequiredUserId(userId, "userId");
        String receiverId = normalizeRequiredUserId(targetUserId, "targetUserId");
        validateDifferentUsers(requesterId, receiverId);

        FriendEntity relationship = friendRepository.findRelationshipBetween(requesterId, receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request not found between users"));

        if (relationship.getStatus() != FriendStatus.PENDING || !isSameDirection(relationship, requesterId, receiverId)) {
            throw new IllegalArgumentException("Only the sender can cancel a pending friend request");
        }

        relationship.setStatus(FriendStatus.CANCELLED);
        relationship.setActionBy(requesterId);
        relationship.setUpdatedAt(new Date());
        friendRepository.save(relationship);

        return buildActionResponse(requesterId, receiverId, "CANCELLED", "Friend request cancelled successfully");
    }

    public FriendActionResponse unfriend(String userId, String targetUserId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        String otherUserId = normalizeRequiredUserId(targetUserId, "targetUserId");
        validateDifferentUsers(currentUserId, otherUserId);

        FriendEntity relationship = friendRepository.findRelationshipBetween(currentUserId, otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend relationship not found between users"));

        if (relationship.getStatus() != FriendStatus.ACCEPTED) {
            throw new IllegalArgumentException("Users are not in an accepted friendship state");
        }

        relationship.setStatus(FriendStatus.UNFRIENDED);
        relationship.setActionBy(currentUserId);
        relationship.setUpdatedAt(new Date());
        friendRepository.save(relationship);

        return buildActionResponse(currentUserId, otherUserId, "NOT_FRIEND", "Unfriended successfully");
    }

    @Transactional(readOnly = true)
    public Page<FriendListItemResponse> getFriends(String userId, Pageable pageable) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");

        return friendRepository.findRelationshipsByUserIdAndStatus(currentUserId, FriendStatus.ACCEPTED, pageable)
                .map(relationship -> FriendListItemResponse.builder()
                        .otherUserId(resolveOtherUserId(relationship, currentUserId))
                        .status("FRIEND")
                        .requestedAt(relationship.getCreatedAt())
                        .respondedAt(relationship.getUpdatedAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> getPendingRequests(String userId, String type, Pageable pageable) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        String normalizedType = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "incoming";

        return switch (normalizedType) {
            case "incoming" -> friendRepository.findByFriendToAndStatusOrderByCreatedAtDesc(currentUserId, FriendStatus.PENDING, pageable)
                    .map(relationship -> buildPendingResponse(relationship, "PENDING_RECEIVED"));
            case "outgoing" -> friendRepository.findByUserIdAndStatusOrderByCreatedAtDesc(currentUserId, FriendStatus.PENDING, pageable)
                    .map(relationship -> buildPendingResponse(relationship, "PENDING_SENT"));
            default -> throw new IllegalArgumentException("Invalid request type. Allowed values: incoming, outgoing");
        };
    }

    @Transactional(readOnly = true)
    public RelationshipStatusResponse getRelationshipStatus(String userId, String targetUserId) {
        String currentUserId = normalizeRequiredUserId(userId, "userId");
        String otherUserId = normalizeRequiredUserId(targetUserId, "targetUserId");
        validateDifferentUsers(currentUserId, otherUserId);

        String pairKey = buildPairKey(currentUserId, otherUserId);
        FriendEntity relationship = friendRepository.findByPairKey(pairKey).orElse(null);

        return RelationshipStatusResponse.builder()
                .userId(currentUserId)
                .targetUserId(otherUserId)
                .status(resolveRelationshipStatus(relationship, currentUserId))
                .build();
    }

    private FriendActionResponse handleExistingRelationship(String requesterId, String receiverId,
                                                            FriendEntity relationship, Date now) {
        return switch (relationship.getStatus()) {
            case ACCEPTED -> buildActionResponse(requesterId, receiverId, "FRIEND", "Users are already friends");
            case PENDING -> handlePendingRelationship(requesterId, receiverId, relationship, now);
            case REJECTED, CANCELLED, UNFRIENDED -> {
                relationship.setUserId(requesterId);
                relationship.setFriendTo(receiverId);
                relationship.setPairKey(buildPairKey(requesterId, receiverId));
                relationship.setStatus(FriendStatus.PENDING);
                relationship.setActionBy(requesterId);
                relationship.setCreatedAt(now);
                relationship.setUpdatedAt(now);
                friendRepository.save(relationship);
                yield buildActionResponse(requesterId, receiverId, "PENDING_SENT", "Friend request sent successfully");
            }
            case BLOCKED -> throw new IllegalArgumentException("This relationship is blocked");
        };
    }

    private FriendActionResponse handlePendingRelationship(String requesterId, String receiverId,
                                                           FriendEntity relationship, Date now) {
        if (isSameDirection(relationship, requesterId, receiverId)) {
            return buildActionResponse(requesterId, receiverId, "PENDING_SENT", "Friend request already sent");
        }

        if (isSameDirection(relationship, receiverId, requesterId)) {
            relationship.setStatus(FriendStatus.ACCEPTED);
            relationship.setActionBy(requesterId);
            relationship.setUpdatedAt(now);
            friendRepository.save(relationship);
            return buildActionResponse(requesterId, receiverId, "FRIEND", "Friend request accepted");
        }

        throw new IllegalArgumentException("Invalid friend relationship state");
    }

    private FriendRequestResponse buildPendingResponse(FriendEntity relationship, String status) {
        return FriendRequestResponse.builder()
                .requesterId(relationship.getUserId())
                .targetUserId(relationship.getFriendTo())
                .status(relationship.getStatus().name())
                .requestedAt(relationship.getCreatedAt())
                .updatedAt(relationship.getUpdatedAt())
                .build();
    }

    private FriendActionResponse buildActionResponse(String userId, String targetUserId, String status, String message) {
        return FriendActionResponse.builder()
                .userId(userId)
                .targetUserId(targetUserId)
                .status(status)
                .message(message)
                .build();
    }

    private String resolveRelationshipStatus(FriendEntity relationship, String currentUserId) {
        if (relationship == null) {
            return "NOT_FRIEND";
        }

        return switch (relationship.getStatus()) {
            case ACCEPTED -> "FRIEND";
            case PENDING -> relationship.getUserId().equals(currentUserId) ? "PENDING_SENT" : "PENDING_RECEIVED";
            case BLOCKED -> "BLOCKED";
            case REJECTED, CANCELLED, UNFRIENDED -> "NOT_FRIEND";
        };
    }

    private boolean isSameDirection(FriendEntity relationship, String requesterId, String receiverId) {
        return requesterId.equals(relationship.getUserId()) && receiverId.equals(relationship.getFriendTo());
    }

    private String resolveOtherUserId(FriendEntity relationship, String currentUserId) {
        return currentUserId.equals(relationship.getUserId()) ? relationship.getFriendTo() : relationship.getUserId();
    }

    private void validateDifferentUsers(String userId, String targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot send a friend request to yourself");
        }
    }

    private String normalizeRequiredUserId(String userId, String fieldName) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return userId.trim();
    }

    private String buildPairKey(String userA, String userB) {
        return userA.compareTo(userB) <= 0 ? userA + ":" + userB : userB + ":" + userA;
    }
}
