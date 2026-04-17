package com.socialmedia.social_media_chat_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialmedia.social_media_chat_service.document.ConversationDocument;
import com.socialmedia.social_media_chat_service.document.ConversationMemberDocument;
import com.socialmedia.social_media_chat_service.document.MessageDocument;
import com.socialmedia.social_media_chat_service.document.enums.MessageStatus;
import com.socialmedia.social_media_chat_service.document.enums.MessageType;
import com.socialmedia.social_media_chat_service.dto.request.MarkAsReadRequest;
import com.socialmedia.social_media_chat_service.dto.request.SendMessageRequest;
import com.socialmedia.social_media_chat_service.dto.response.MessagePageResponse;
import com.socialmedia.social_media_chat_service.dto.response.MessageResponse;
import com.socialmedia.social_media_chat_service.exception.BadRequestException;
import com.socialmedia.social_media_chat_service.exception.NotFoundException;
import com.socialmedia.social_media_chat_service.repository.ConversationMemberRepository;
import com.socialmedia.social_media_chat_service.repository.ConversationRepository;
import com.socialmedia.social_media_chat_service.repository.MessageRepository;

@Service
public class MessageService {

    private static final Duration RECALL_LIMIT = Duration.ofMinutes(15);

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ChatEventProducer chatEventProducer;
    private final MongoTemplate mongoTemplate;

    public MessageService(ConversationService conversationService,
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            ChatEventProducer chatEventProducer,
            MongoTemplate mongoTemplate) {
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.chatEventProducer = chatEventProducer;
        this.mongoTemplate = mongoTemplate;
    }

    @Transactional
    public MessageResponse sendMessage(String senderId, SendMessageRequest request) {
        validateMessageRequest(request);
        ConversationDocument conversation = conversationService.requireMemberConversation(senderId, request.getConversationId());

        long nextSeqNo = messageRepository.findFirstByConversationIdOrderBySeqNoDesc(conversation.getId())
                .map(MessageDocument::getSeqNo)
                .orElse(0L) + 1;

        MessageDocument savedMessage = messageRepository.save(MessageDocument.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .seqNo(nextSeqNo)
                .messageType(request.getMessageType() == null ? MessageType.TEXT : request.getMessageType())
                .content(StringUtils.hasText(request.getContent()) ? request.getContent().trim() : null)
                .attachments(toAttachmentInfos(request.getAttachments()))
                .build());

        conversation.setLastMessageId(savedMessage.getId());
        conversation.setLastMessagePreview(buildPreview(savedMessage));
        conversation.setLastMessageSenderId(senderId);
        conversation.setLastMessageAt(savedMessage.getCreatedAt() != null ? savedMessage.getCreatedAt() : Instant.now());
        conversationRepository.save(conversation);

        List<ConversationMemberDocument> members = conversationMemberRepository
                .findByConversationIdAndActiveTrue(conversation.getId());

        for (ConversationMemberDocument member : members) {
            if (!senderId.equals(member.getUserId())) {
                member.setUnreadCount(member.getUnreadCount() + 1);
            }
        }
        conversationMemberRepository.saveAll(members);

        List<String> recipients = members.stream()
                .map(ConversationMemberDocument::getUserId)
                .toList();

        MessageResponse response = toMessageResponse(savedMessage);
        chatEventProducer.publishMessageCreated(response, recipients);

        return response;
    }

    public MessagePageResponse getConversationMessages(String userId, String conversationId, Long cursor, int limit) {
        if ("undefined".equals(conversationId) || "null".equals(conversationId)) {
            return MessagePageResponse.builder().messages(Collections.emptyList()).nextCursor(0L).hasMore(false).totalCount(0).build();
        }
        conversationService.requireMemberConversation(userId, conversationId);
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        Page<MessageDocument> page;
        if (cursor != null && cursor > 0) {
            page = messageRepository.findByConversationIdPaginated(conversationId, cursor,
                    PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "seqNo")));
        } else {
            page = messageRepository.findByConversationIdDesc(conversationId,
                    PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "seqNo")));
        }

        List<MessageResponse> messages = page.getContent().stream()
                .map(this::toMessageResponse)
                .toList();

        long nextCursor = messages.isEmpty() ? 0 : messages.get(messages.size() - 1).getSeqNo();

        return MessagePageResponse.builder()
                .messages(messages)
                .nextCursor(nextCursor)
                .hasMore(page.hasNext())
                .totalCount((int) messageRepository.countByConversationId(conversationId))
                .build();
    }

    @Transactional
    public void markAsRead(String userId, String conversationId, MarkAsReadRequest request) {
        conversationService.requireMemberConversation(userId, conversationId);
        MessageDocument readBoundary = resolveReadBoundary(conversationId, request);
        Instant readAt = Instant.now();

        ConversationMemberDocument member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversation member not found"));

        member.setLastReadAt(readAt);
        if (readBoundary != null) {
            member.setLastReadMessageId(readBoundary.getId());
        }
        member.setUnreadCount(0);

        conversationMemberRepository.save(member);
        markMessagesAsSeen(userId, conversationId, readBoundary, readAt);

        List<String> recipients = conversationService.getActiveMemberUserIds(conversationId);
        chatEventProducer.publishMessageRead(conversationId,
                readBoundary != null ? readBoundary.getId() : request.getMessageId(),
                userId,
                recipients);
    }

    private MessageDocument resolveReadBoundary(String conversationId, MarkAsReadRequest request) {
        if (request != null && StringUtils.hasText(request.getMessageId())) {
            return messageRepository.findByIdAndConversationId(request.getMessageId(), conversationId)
                    .orElseThrow(() -> new NotFoundException("Message not found"));
        }

        return messageRepository.findFirstByConversationIdOrderBySeqNoDesc(conversationId)
                .orElse(null);
    }

    private void markMessagesAsSeen(String userId, String conversationId, MessageDocument readBoundary, Instant readAt) {
        Criteria criteria = Criteria.where("conversationId").is(conversationId)
                .and("senderId").ne(userId)
                .and("status").ne(MessageStatus.SEEN);

        if (readBoundary != null) {
            criteria = criteria.and("seqNo").lte(readBoundary.getSeqNo());
        }

        Query query = new Query(criteria);
        Update update = new Update()
                .set("status", MessageStatus.SEEN)
                .set("updatedAt", readAt);

        mongoTemplate.updateMulti(query, update, MessageDocument.class);
    }

    @Transactional
    public MessageResponse recallMessage(String userId, String messageId) {
        MessageDocument message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        conversationService.requireMemberConversation(userId, message.getConversationId());

        if (!userId.equals(message.getSenderId())) {
            throw new BadRequestException("Only sender can recall message");
        }

        Instant createdAt = message.getCreatedAt() == null ? Instant.now() : message.getCreatedAt();
        if (Duration.between(createdAt, Instant.now()).compareTo(RECALL_LIMIT) > 0) {
            throw new BadRequestException("Recall window has expired");
        }

        message.setDeletedForEveryone(true);
        message.setEdited(false);
        message.setContent("This message was recalled");
        message.setAttachments(Collections.emptyList());
        message.setMessageType(MessageType.SYSTEM);

        MessageDocument saved = messageRepository.save(message);
        MessageResponse response = toMessageResponse(saved);
        List<String> recipients = conversationService.getActiveMemberUserIds(message.getConversationId());
        chatEventProducer.publishMessageRecalled(response, recipients);
        return response;
    }

    private void validateMessageRequest(SendMessageRequest request) {
        if (request == null || !StringUtils.hasText(request.getConversationId())) {
            throw new BadRequestException("conversationId is required");
        }

        boolean hasContent = StringUtils.hasText(request.getContent());
        boolean hasAttachment = request.getAttachments() != null && !request.getAttachments().isEmpty();

        if (!hasContent && !hasAttachment) {
            throw new BadRequestException("Message content or attachments is required");
        }
    }

    private String buildPreview(MessageDocument message) {
        if (message.isDeletedForEveryone()) {
            return "This message was recalled";
        }

        if (StringUtils.hasText(message.getContent())) {
            String trimmed = message.getContent().trim();
            return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
        }

        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            return "[Attachment]";
        }

        return "";
    }

    private List<MessageDocument.AttachmentInfo> toAttachmentInfos(List<SendMessageRequest.AttachmentDto> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }

        return attachments.stream()
                .map(attachment -> MessageDocument.AttachmentInfo.builder()
                        .publicId(attachment.getPublicId())
                        .mediaUrl(attachment.getMediaUrl())
                        .mediaType(attachment.getMediaType())
                        .build())
                .toList();
    }

    private MessageResponse toMessageResponse(MessageDocument message) {
        List<MessageResponse.AttachmentDto> attachmentDtos = message.getAttachments() == null
                ? Collections.emptyList()
                : message.getAttachments().stream()
                .map(att -> MessageResponse.AttachmentDto.builder()
                        .publicId(att.getPublicId())
                        .mediaUrl(att.getMediaUrl())
                        .mediaType(att.getMediaType())
                        .build())
                .toList();

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .seqNo(message.getSeqNo())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .attachments(attachmentDtos)
                .replyToMessageId(message.getReplyToMessageId())
                .status(message.getStatus())
                .edited(message.isEdited())
                .deletedForEveryone(message.isDeletedForEveryone())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}
