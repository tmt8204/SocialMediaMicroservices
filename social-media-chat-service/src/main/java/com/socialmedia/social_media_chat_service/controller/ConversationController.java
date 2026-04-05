package com.socialmedia.social_media_chat_service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_chat_service.dto.response.ConversationResponse;
import com.socialmedia.social_media_chat_service.service.ConversationService;

@RestController
@RequestMapping("/api/chat/conversations")
public class ConversationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/direct/{targetUserId}")
    public ResponseEntity<ConversationResponse> createOrGetDirectConversation(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(conversationService.createOrGetDirectConversation(currentUserId, targetUserId));
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getMyConversations(
            @RequestHeader(USER_ID_HEADER) String currentUserId) {
        return ResponseEntity.ok(conversationService.getMyConversations(currentUserId));
    }
}
