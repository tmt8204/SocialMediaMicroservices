package com.socialmedia.social_media_chat_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_chat_service.dto.request.MarkAsReadRequest;
import com.socialmedia.social_media_chat_service.dto.response.MessagePageResponse;
import com.socialmedia.social_media_chat_service.dto.response.MessageResponse;
import com.socialmedia.social_media_chat_service.service.MessageService;

@RestController
@RequestMapping("/api/chat")
public class MessageController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessagePageResponse> getConversationMessages(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String conversationId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(messageService.getConversationMessages(currentUserId, conversationId, cursor, limit));
    }

    @PutMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String conversationId,
            @RequestBody(required = false) MarkAsReadRequest request) {
        MarkAsReadRequest safeRequest = request == null ? new MarkAsReadRequest(conversationId, null) : request;
        safeRequest.setConversationId(conversationId);
        messageService.markAsRead(currentUserId, conversationId, safeRequest);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/messages/{messageId}/recall")
    public ResponseEntity<MessageResponse> recallMessage(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String messageId) {
        return ResponseEntity.ok(messageService.recallMessage(currentUserId, messageId));
    }
}
