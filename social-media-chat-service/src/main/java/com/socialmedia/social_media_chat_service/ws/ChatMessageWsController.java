package com.socialmedia.social_media_chat_service.ws;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.socialmedia.social_media_chat_service.dto.request.MarkAsReadRequest;
import com.socialmedia.social_media_chat_service.dto.request.SendMessageRequest;
import com.socialmedia.social_media_chat_service.dto.request.TypingIndicatorRequest;
import com.socialmedia.social_media_chat_service.dto.response.MessageResponse;
import com.socialmedia.social_media_chat_service.service.ConversationService;
import com.socialmedia.social_media_chat_service.service.MessageService;
import com.socialmedia.social_media_chat_service.service.PresenceService;

@Controller
public class ChatMessageWsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageWsController.class);

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    public ChatMessageWsController(MessageService messageService,
            ConversationService conversationService,
            SimpMessagingTemplate messagingTemplate,
            PresenceService presenceService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        String senderId = principal.getName();
        LOGGER.info("ws chat.send senderId={} conversationId={} messageType={}",
            senderId,
            request.getConversationId(),
            request.getMessageType());

        MessageResponse message = messageService.sendMessage(senderId, request);

        List<String> recipients = conversationService.getActiveMemberUserIds(request.getConversationId());
        LOGGER.info("ws chat.send persisted messageId={} conversationId={} recipients={}",
            message.getId(),
            request.getConversationId(),
            recipients);

        for (String recipientId : recipients) {
            messagingTemplate.convertAndSendToUser(recipientId, "/queue/messages", message);
        }

        messagingTemplate.convertAndSend("/topic/conversations/" + request.getConversationId(), message);
    }

    @MessageMapping("/chat.read")
    public void markAsRead(@Payload MarkAsReadRequest request, Principal principal) {
        String userId = principal.getName();
        LOGGER.info("ws chat.read userId={} conversationId={} messageId={}",
            userId,
            request.getConversationId(),
            request.getMessageId());
        messageService.markAsRead(userId, request.getConversationId(), request);

        List<String> recipients = conversationService.getActiveMemberUserIds(request.getConversationId());
        Map<String, Object> event = Map.of(
                "eventType", "MESSAGE_READ",
                "conversationId", request.getConversationId(),
                "messageId", request.getMessageId(),
                "userId", userId);

        for (String recipientId : recipients) {
            messagingTemplate.convertAndSendToUser(recipientId, "/queue/events", event);
        }
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingIndicatorRequest request, Principal principal) {
        String userId = principal.getName();
        LOGGER.info("ws chat.typing userId={} conversationId={} typing={}",
            userId,
            request.getConversationId(),
            request.isTyping());
        conversationService.requireMemberConversation(userId, request.getConversationId());
        presenceService.setTyping(request.getConversationId(), userId, request.isTyping());

        List<String> recipients = conversationService.getActiveMemberUserIds(request.getConversationId())
                .stream()
                .filter(memberId -> !memberId.equals(userId))
                .toList();

        Map<String, Object> event = Map.of(
                "eventType", "TYPING",
                "conversationId", request.getConversationId(),
                "userId", userId,
                "typing", request.isTyping());

        for (String recipientId : recipients) {
            messagingTemplate.convertAndSendToUser(recipientId, "/queue/events", event);
        }
    }
}
