package com.socialmedia.social_media_chat_service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.social_media_chat_service.dto.request.AddConversationMembersRequest;
import com.socialmedia.social_media_chat_service.dto.request.CreateGroupConversationRequest;
import com.socialmedia.social_media_chat_service.dto.request.UpdateConversationMemberRoleRequest;
import com.socialmedia.social_media_chat_service.dto.request.UpdateGroupConversationRequest;
import com.socialmedia.social_media_chat_service.dto.response.ConversationMemberInfoResponse;
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

    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroupConversation(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @RequestBody CreateGroupConversationRequest request) {
        return ResponseEntity.ok(conversationService.createGroupConversation(currentUserId, request));
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getMyConversations(
            @RequestHeader(USER_ID_HEADER) String currentUserId) {
        return ResponseEntity.ok(conversationService.getMyConversations(currentUserId));
    }

    @GetMapping("/{conversationId}/members")
    public ResponseEntity<List<ConversationMemberInfoResponse>> getConversationMembers(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String conversationId) {
        return ResponseEntity.ok(conversationService.getConversationMembers(currentUserId, conversationId));
    }

    @PostMapping("/{conversationId}/members")
    public ResponseEntity<ConversationResponse> addConversationMembers(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String conversationId,
            @RequestBody AddConversationMembersRequest request) {
        return ResponseEntity.ok(conversationService.addGroupMembers(currentUserId, conversationId, request));
    }

    @PutMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> updateGroupConversation(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String conversationId,
            @RequestBody UpdateGroupConversationRequest request) {
        return ResponseEntity.ok(conversationService.updateGroupConversation(currentUserId, conversationId, request));
    }

    @PutMapping("/{conversationId}/members/{memberUserId}/role")
    public ResponseEntity<ConversationResponse> updateConversationMemberRole(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String conversationId,
            @PathVariable String memberUserId,
            @RequestBody UpdateConversationMemberRoleRequest request) {
        return ResponseEntity.ok(
                conversationService.updateMemberRole(currentUserId, conversationId, memberUserId, request));
    }

    @DeleteMapping("/{conversationId}/members/{memberUserId}")
    public ResponseEntity<ConversationResponse> removeConversationMember(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String conversationId,
            @PathVariable String memberUserId) {
        return ResponseEntity.ok(conversationService.removeGroupMember(currentUserId, conversationId, memberUserId));
    }

    @PostMapping("/{conversationId}/leave")
    public ResponseEntity<ConversationResponse> leaveConversation(
            @RequestHeader(USER_ID_HEADER) String currentUserId,
            @PathVariable String conversationId) {
        return ResponseEntity.ok(conversationService.leaveGroupConversation(currentUserId, conversationId));
    }
}
