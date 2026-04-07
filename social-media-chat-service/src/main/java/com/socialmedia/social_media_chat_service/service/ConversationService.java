package com.socialmedia.social_media_chat_service.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_chat_service.document.ConversationDocument;
import com.socialmedia.social_media_chat_service.document.ConversationMemberDocument;
import com.socialmedia.social_media_chat_service.document.enums.ConversationType;
import com.socialmedia.social_media_chat_service.document.enums.MemberRole;
import com.socialmedia.social_media_chat_service.dto.request.AddConversationMembersRequest;
import com.socialmedia.social_media_chat_service.dto.request.CreateGroupConversationRequest;
import com.socialmedia.social_media_chat_service.dto.request.UpdateConversationMemberRoleRequest;
import com.socialmedia.social_media_chat_service.dto.request.UpdateGroupConversationRequest;
import com.socialmedia.social_media_chat_service.dto.response.ConversationMemberInfoResponse;
import com.socialmedia.social_media_chat_service.dto.response.ConversationResponse;
import com.socialmedia.social_media_chat_service.exception.AccessDeniedException;
import com.socialmedia.social_media_chat_service.exception.BadRequestException;
import com.socialmedia.social_media_chat_service.exception.NotFoundException;
import com.socialmedia.social_media_chat_service.repository.ConversationMemberRepository;
import com.socialmedia.social_media_chat_service.repository.ConversationRepository;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ChatEventProducer chatEventProducer;

    public ConversationService(ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            ChatEventProducer chatEventProducer) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.chatEventProducer = chatEventProducer;
    }

    public ConversationResponse createOrGetDirectConversation(String currentUserId, String targetUserId) {
        if (!StringUtils.hasText(targetUserId)) {
            throw new BadRequestException("targetUserId must not be empty");
        }
        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException("Cannot create direct conversation with yourself");
        }

        String directKey = buildDirectKey(currentUserId, targetUserId);
        ConversationDocument conversation = conversationRepository.findByDirectKey(directKey)
                .orElseGet(() -> createDirectConversation(currentUserId, targetUserId, directKey));

        return toConversationResponse(conversation);
    }

    public List<ConversationResponse> getMyConversations(String userId) {
        return conversationRepository.findByUserIdSortedByLastMessage(userId)
                .stream()
                .map(this::toConversationResponse)
                .toList();
    }

    public ConversationResponse createGroupConversation(String currentUserId, CreateGroupConversationRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        String groupName = normalizeRequiredText(request.getGroupName(), "groupName");
        List<String> memberUserIds = normalizeMemberUserIds(request.getMemberUserIds(), currentUserId, true);
        Instant now = Instant.now();

        List<String> participantIds = new ArrayList<>();
        participantIds.add(currentUserId);
        participantIds.addAll(memberUserIds);

        ConversationDocument savedConversation = conversationRepository.save(ConversationDocument.builder()
                .type(ConversationType.GROUP)
                .createdBy(currentUserId)
                .groupName(groupName)
                .groupAvatarUrl(normalizeOptionalText(request.getGroupAvatarUrl()))
                .groupDescription(normalizeOptionalText(request.getGroupDescription()))
                .participantIds(participantIds)
                .active(true)
                .build());

        List<ConversationMemberDocument> members = new ArrayList<>();
        members.add(ConversationMemberDocument.builder()
                .conversationId(savedConversation.getId())
                .userId(currentUserId)
                .role(MemberRole.OWNER)
                .joinedAt(now)
                .active(true)
                .build());

        for (String memberUserId : memberUserIds) {
            members.add(ConversationMemberDocument.builder()
                    .conversationId(savedConversation.getId())
                    .userId(memberUserId)
                    .role(MemberRole.MEMBER)
                    .joinedAt(now)
                    .active(true)
                    .build());
        }

        conversationMemberRepository.saveAll(members);
        chatEventProducer.publishConversationCreated(savedConversation.getId(), currentUserId, participantIds);
        return toConversationResponse(savedConversation);
    }

    public List<ConversationMemberInfoResponse> getConversationMembers(String currentUserId, String conversationId) {
        ConversationDocument conversation = requireMemberConversation(currentUserId, conversationId);
        requireGroupConversation(conversation);

        return getActiveMemberDocuments(conversationId).stream()
                .map(this::toConversationMemberInfoResponse)
                .toList();
    }

    public ConversationResponse addGroupMembers(String currentUserId, String conversationId,
            AddConversationMembersRequest request) {
        ConversationDocument conversation = requireGroupManager(currentUserId, conversationId);
        List<String> requestedUserIds = request == null ? List.of() : request.getMemberUserIds();
        List<String> normalizedUserIds = normalizeMemberUserIds(requestedUserIds, currentUserId, false);

        if (normalizedUserIds.isEmpty()) {
            throw new BadRequestException("memberUserIds must contain at least one valid userId");
        }

        List<ConversationMemberDocument> existingMembers = conversationMemberRepository
                .findByConversationIdAndUserIdIn(conversationId, normalizedUserIds);
        Set<String> existingByUserId = existingMembers.stream()
                .map(ConversationMemberDocument::getUserId)
                .collect(java.util.stream.Collectors.toSet());

        Instant now = Instant.now();
        List<ConversationMemberDocument> membersToSave = new ArrayList<>();

        for (ConversationMemberDocument member : existingMembers) {
            if (!member.isActive()) {
                member.setActive(true);
                member.setHidden(false);
                member.setJoinedAt(now);
                if (member.getRole() == null) {
                    member.setRole(MemberRole.MEMBER);
                }
                membersToSave.add(member);
            }
        }

        for (String userId : normalizedUserIds) {
            if (!existingByUserId.contains(userId)) {
                membersToSave.add(ConversationMemberDocument.builder()
                        .conversationId(conversationId)
                        .userId(userId)
                        .role(MemberRole.MEMBER)
                        .joinedAt(now)
                        .active(true)
                        .build());
            }
        }

        if (membersToSave.isEmpty()) {
            throw new BadRequestException("All provided users are already active members");
        }

        conversationMemberRepository.saveAll(membersToSave);
        syncConversationParticipants(conversation);
        return toConversationResponse(conversationRepository.save(conversation));
    }

    public ConversationResponse updateGroupConversation(String currentUserId, String conversationId,
            UpdateGroupConversationRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        ConversationDocument conversation = requireGroupManager(currentUserId, conversationId);
        conversation.setGroupName(normalizeRequiredText(request.getGroupName(), "groupName"));
        conversation.setGroupAvatarUrl(normalizeOptionalText(request.getGroupAvatarUrl()));
        conversation.setGroupDescription(normalizeOptionalText(request.getGroupDescription()));

        return toConversationResponse(conversationRepository.save(conversation));
    }

    public ConversationResponse updateMemberRole(String currentUserId, String conversationId, String memberUserId,
            UpdateConversationMemberRoleRequest request) {
        if (request == null || request.getRole() == null) {
            throw new BadRequestException("role is required");
        }

        ConversationDocument conversation = requireGroupOwner(currentUserId, conversationId);
        ConversationMemberDocument targetMember = requireActiveMember(conversationId, memberUserId);
        MemberRole targetRole = request.getRole();

        if (targetRole == MemberRole.OWNER) {
            if (currentUserId.equals(memberUserId)) {
                return toConversationResponse(conversation);
            }

            ConversationMemberDocument currentOwner = requireActiveMember(conversationId, currentUserId);
            currentOwner.setRole(MemberRole.ADMIN);
            targetMember.setRole(MemberRole.OWNER);
            conversationMemberRepository.save(currentOwner);
            conversationMemberRepository.save(targetMember);
            return toConversationResponse(conversation);
        }

        if (currentUserId.equals(memberUserId) && targetRole != MemberRole.OWNER) {
            throw new BadRequestException("Owner cannot change their own role without transferring ownership");
        }

        if (targetMember.getRole() == MemberRole.OWNER) {
            throw new BadRequestException("Transfer ownership before changing owner role");
        }

        targetMember.setRole(targetRole);
        conversationMemberRepository.save(targetMember);
        return toConversationResponse(conversation);
    }

    public ConversationResponse removeGroupMember(String currentUserId, String conversationId, String memberUserId) {
        ConversationDocument conversation = requireGroupManager(currentUserId, conversationId);

        if (currentUserId.equals(memberUserId)) {
            throw new BadRequestException("Use leave endpoint to remove yourself from group");
        }

        ConversationMemberDocument actingMember = requireActiveMember(conversationId, currentUserId);
        ConversationMemberDocument targetMember = requireActiveMember(conversationId, memberUserId);

        if (targetMember.getRole() == MemberRole.OWNER) {
            throw new BadRequestException("Cannot remove group owner");
        }

        if (actingMember.getRole() == MemberRole.ADMIN && targetMember.getRole() == MemberRole.ADMIN) {
            throw new AccessDeniedException("Admin cannot remove another admin");
        }

        targetMember.setActive(false);
        conversationMemberRepository.save(targetMember);
        syncConversationParticipants(conversation);
        return toConversationResponse(conversationRepository.save(conversation));
    }

    public ConversationResponse leaveGroupConversation(String currentUserId, String conversationId) {
        ConversationDocument conversation = requireMemberConversation(currentUserId, conversationId);
        requireGroupConversation(conversation);

        ConversationMemberDocument currentMember = requireActiveMember(conversationId, currentUserId);
        List<ConversationMemberDocument> activeMembers = getActiveMemberDocuments(conversationId);

        if (currentMember.getRole() == MemberRole.OWNER && activeMembers.size() > 1) {
            throw new BadRequestException("Owner must transfer ownership before leaving the group");
        }

        currentMember.setActive(false);
        conversationMemberRepository.save(currentMember);
        syncConversationParticipants(conversation);
        return toConversationResponse(conversationRepository.save(conversation));
    }

    public ConversationDocument requireMemberConversation(String userId, String conversationId) {
        ConversationDocument conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        boolean isMember = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .map(ConversationMemberDocument::isActive)
                .orElse(false);

        if (!isMember) {
            throw new AccessDeniedException("User is not a member of this conversation");
        }

        return conversation;
    }

    public List<String> getActiveMemberUserIds(String conversationId) {
        return getActiveMemberDocuments(conversationId)
                .stream()
                .map(ConversationMemberDocument::getUserId)
                .toList();
    }

    private ConversationDocument requireGroupManager(String currentUserId, String conversationId) {
        ConversationDocument conversation = requireMemberConversation(currentUserId, conversationId);
        requireGroupConversation(conversation);

        ConversationMemberDocument member = requireActiveMember(conversationId, currentUserId);
        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new AccessDeniedException("Only owner or admin can manage group conversation");
        }

        return conversation;
    }

    private ConversationDocument requireGroupOwner(String currentUserId, String conversationId) {
        ConversationDocument conversation = requireMemberConversation(currentUserId, conversationId);
        requireGroupConversation(conversation);

        ConversationMemberDocument member = requireActiveMember(conversationId, currentUserId);
        if (member.getRole() != MemberRole.OWNER) {
            throw new AccessDeniedException("Only owner can perform this action");
        }

        return conversation;
    }

    private void requireGroupConversation(ConversationDocument conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BadRequestException("Conversation is not a group conversation");
        }
    }

    private ConversationMemberDocument requireActiveMember(String conversationId, String userId) {
        ConversationMemberDocument member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversation member not found"));

        if (!member.isActive()) {
            throw new AccessDeniedException("User is not an active member of this conversation");
        }

        return member;
    }

    private List<ConversationMemberDocument> getActiveMemberDocuments(String conversationId) {
        return conversationMemberRepository.findByConversationIdAndActiveTrue(conversationId)
                .stream()
                .sorted(Comparator.comparing(ConversationMemberDocument::getJoinedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private void syncConversationParticipants(ConversationDocument conversation) {
        List<String> participantIds = getActiveMemberDocuments(conversation.getId()).stream()
                .map(ConversationMemberDocument::getUserId)
                .toList();

        conversation.setParticipantIds(participantIds);
        conversation.setActive(!participantIds.isEmpty());
    }

    private List<String> normalizeMemberUserIds(List<String> memberUserIds, String currentUserId,
            boolean requireAtLeastOneOtherMember) {
        if (memberUserIds == null || memberUserIds.isEmpty()) {
            if (requireAtLeastOneOtherMember) {
                throw new BadRequestException("memberUserIds must contain at least one userId");
            }
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String memberUserId : memberUserIds) {
            String trimmed = normalizeOptionalText(memberUserId);
            if (trimmed != null && !trimmed.equals(currentUserId)) {
                normalized.add(trimmed);
            }
        }

        if (normalized.isEmpty() && requireAtLeastOneOtherMember) {
            throw new BadRequestException("Group conversation requires at least one other member");
        }

        return List.copyOf(normalized);
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BadRequestException(fieldName + " must not be empty");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ConversationDocument createDirectConversation(String currentUserId, String targetUserId, String directKey) {
        Instant now = Instant.now();
        List<String> participantIds = new ArrayList<>(List.of(currentUserId, targetUserId));
        participantIds.sort(Comparator.naturalOrder());

        ConversationDocument savedConversation = conversationRepository.save(ConversationDocument.builder()
                .type(ConversationType.DIRECT)
                .directKey(directKey)
                .createdBy(currentUserId)
                .participantIds(participantIds)
                .active(true)
                .build());

        conversationMemberRepository.save(ConversationMemberDocument.builder()
                .conversationId(savedConversation.getId())
                .userId(currentUserId)
                .role(MemberRole.OWNER)
                .joinedAt(now)
                .active(true)
                .build());

        conversationMemberRepository.save(ConversationMemberDocument.builder()
                .conversationId(savedConversation.getId())
                .userId(targetUserId)
                .role(MemberRole.MEMBER)
                .joinedAt(now)
                .active(true)
                .build());

        chatEventProducer.publishConversationCreated(savedConversation.getId(), currentUserId, participantIds);

        return savedConversation;
    }

    private String buildDirectKey(String firstUserId, String secondUserId) {
        return firstUserId.compareTo(secondUserId) <= 0
                ? firstUserId + ":" + secondUserId
                : secondUserId + ":" + firstUserId;
    }

    private ConversationResponse toConversationResponse(ConversationDocument conversation) {
        return ConversationResponse.builder()
                .conversationId(conversation.getId())
                .type(conversation.getType())
                .groupName(conversation.getGroupName())
                .groupAvatarUrl(conversation.getGroupAvatarUrl())
                .groupDescription(conversation.getGroupDescription())
                .participantIds(conversation.getParticipantIds())
                .lastMessageId(conversation.getLastMessageId())
                .lastMessagePreview(conversation.getLastMessagePreview())
                .lastMessageSenderId(conversation.getLastMessageSenderId())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private ConversationMemberInfoResponse toConversationMemberInfoResponse(ConversationMemberDocument member) {
        return ConversationMemberInfoResponse.builder()
                .conversationId(member.getConversationId())
                .userId(member.getUserId())
                .role(member.getRole() == null ? null : member.getRole().name())
                .lastReadMessageId(member.getLastReadMessageId())
                .lastReadAt(member.getLastReadAt())
                .unreadCount(member.getUnreadCount())
                .muted(member.isMuted())
                .pinned(member.isPinned())
                .hidden(member.isHidden())
                .build();
    }
}
