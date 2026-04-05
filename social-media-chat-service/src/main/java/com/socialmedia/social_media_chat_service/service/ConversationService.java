package com.socialmedia.social_media_chat_service.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_chat_service.document.ConversationDocument;
import com.socialmedia.social_media_chat_service.document.ConversationMemberDocument;
import com.socialmedia.social_media_chat_service.document.enums.ConversationType;
import com.socialmedia.social_media_chat_service.document.enums.MemberRole;
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
        return conversationMemberRepository.findByConversationIdAndActiveTrue(conversationId)
                .stream()
                .map(ConversationMemberDocument::getUserId)
                .toList();
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
                .participantIds(conversation.getParticipantIds())
                .lastMessageId(conversation.getLastMessageId())
                .lastMessagePreview(conversation.getLastMessagePreview())
                .lastMessageSenderId(conversation.getLastMessageSenderId())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .build();
    }
}
