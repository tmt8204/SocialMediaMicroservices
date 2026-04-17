package com.socialmedia.admin.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.socialmedia.admin.dto.ChangeRoleRequest;
import com.socialmedia.admin.dto.StatsResponse;
import com.socialmedia.admin.dto.UserAdminDTO;
import com.socialmedia.admin.entities.jpa.CommentEntity;
import com.socialmedia.admin.entities.jpa.CommunityEntity;
import com.socialmedia.admin.entities.jpa.PostEntity;
import com.socialmedia.admin.entities.mongo.AuditLog;
import com.socialmedia.admin.security.RequireRole;
import com.socialmedia.admin.services.AdminSocialService;
import com.socialmedia.admin.services.AdminUserService;
import com.socialmedia.admin.services.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminSocialService adminSocialService;
    private final AuditLogService auditLogService;

    public AdminController(AdminUserService adminUserService,
                           AdminSocialService adminSocialService,
                           AuditLogService auditLogService) {
        this.adminUserService = adminUserService;
        this.adminSocialService = adminSocialService;
        this.auditLogService = auditLogService;
    }

    // ===========================
    //  DASHBOARD STATS
    // ===========================
    @GetMapping("/stats")
    @RequireRole({"ADMIN", "MODERATOR"})
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        try {
            String role = request.getHeader("X-Role");

            StatsResponse stats = new StatsResponse();
            stats.setTotalUsers(adminUserService.countAll());
            stats.setTotalBannedUsers(adminUserService.countBanned());

            // MODERATOR and ADMIN see content counts
            if ("ADMIN".equals(role) || "MODERATOR".equals(role)) {
                stats.setTotalPosts(adminSocialService.countPosts());
                stats.setTotalComments(adminSocialService.countComments());
                stats.setTotalCommunities(adminSocialService.countCommunities());
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    // ===========================
    //  USER MANAGEMENT (ADMIN only)
    // ===========================
    @GetMapping("/users")
    @RequireRole({"ADMIN"})
    public ResponseEntity<List<UserAdminDTO>> getUsers(@RequestParam(value = "q", defaultValue = "") String query) {
        return ResponseEntity.ok(adminUserService.getAllUsers(query));
    }

    @PutMapping("/users/{id}/ban")
    @RequireRole({"ADMIN"})
    public ResponseEntity<Map<String, String>> banUser(@PathVariable String id, HttpServletRequest request) {
        String adminUserId = request.getHeader("X-User-Id");
        String adminUsername = adminUserService.getUsername(adminUserId);
        String targetUsername = adminUserService.getUsername(id);

        adminUserService.banUser(id, adminUserId);

        auditLogService.log(adminUserId, adminUsername, "BAN_USER", "USER", id,
                "Banned user: " + targetUsername, getClientIp(request));

        return ResponseEntity.ok(Map.of("message", "User banned successfully"));
    }

    @PutMapping("/users/{id}/unban")
    @RequireRole({"ADMIN"})
    public ResponseEntity<Map<String, String>> unbanUser(@PathVariable String id, HttpServletRequest request) {
        String adminUserId = request.getHeader("X-User-Id");
        String adminUsername = adminUserService.getUsername(adminUserId);
        String targetUsername = adminUserService.getUsername(id);

        adminUserService.unbanUser(id, adminUserId);

        auditLogService.log(adminUserId, adminUsername, "UNBAN_USER", "USER", id,
                "Unbanned user: " + targetUsername, getClientIp(request));

        return ResponseEntity.ok(Map.of("message", "User unbanned successfully"));
    }

    @PutMapping("/users/{id}/role")
    @RequireRole({"ADMIN"})
    public ResponseEntity<Map<String, String>> changeRole(@PathVariable String id,
                                                          @Valid @RequestBody ChangeRoleRequest changeRoleRequest,
                                                          HttpServletRequest request) {
        String adminUserId = request.getHeader("X-User-Id");
        String adminRole = request.getHeader("X-Role");
        String adminUsername = adminUserService.getUsername(adminUserId);
        String targetUsername = adminUserService.getUsername(id);

        adminUserService.changeRole(id, changeRoleRequest.getRoleName(), adminUserId, adminRole);

        auditLogService.log(adminUserId, adminUsername, "CHANGE_ROLE", "USER", id,
                "Changed role of " + targetUsername + " to " + changeRoleRequest.getRoleName(), getClientIp(request));

        return ResponseEntity.ok(Map.of("message", "Role changed successfully"));
    }

    // ===========================
    //  POST MANAGEMENT (ADMIN + MODERATOR)
    // ===========================
    @GetMapping("/posts")
    @RequireRole({"ADMIN", "MODERATOR"})
    public ResponseEntity<List<PostEntity>> getPosts() {
        return ResponseEntity.ok(adminSocialService.getAllPosts());
    }

    @DeleteMapping("/posts/{id}")
    @RequireRole({"ADMIN", "MODERATOR"})
    public ResponseEntity<Map<String, String>> deletePost(@PathVariable Long id, HttpServletRequest request) {
        String adminUserId = request.getHeader("X-User-Id");
        String adminUsername = adminUserService.getUsername(adminUserId);

        adminSocialService.deletePost(id);

        auditLogService.log(adminUserId, adminUsername, "DELETE_POST", "POST", String.valueOf(id),
                "Deleted post #" + id, getClientIp(request));

        return ResponseEntity.ok(Map.of("message", "Post deleted successfully"));
    }

    // ===========================
    //  COMMENT MANAGEMENT (ADMIN + MODERATOR)
    // ===========================
    @GetMapping("/comments")
    @RequireRole({"ADMIN", "MODERATOR"})
    public ResponseEntity<List<CommentEntity>> getComments() {
        return ResponseEntity.ok(adminSocialService.getAllComments());
    }

    @DeleteMapping("/comments/{id}")
    @RequireRole({"ADMIN", "MODERATOR"})
    public ResponseEntity<Map<String, String>> deleteComment(@PathVariable Long id, HttpServletRequest request) {
        String adminUserId = request.getHeader("X-User-Id");
        String adminUsername = adminUserService.getUsername(adminUserId);

        adminSocialService.deleteComment(id);

        auditLogService.log(adminUserId, adminUsername, "DELETE_COMMENT", "COMMENT", String.valueOf(id),
                "Deleted comment #" + id, getClientIp(request));

        return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
    }

    // ===========================
    //  COMMUNITY MANAGEMENT (ADMIN + MODERATOR)
    // ===========================
    @GetMapping("/communities")
    @RequireRole({"ADMIN", "MODERATOR"})
    public ResponseEntity<List<CommunityEntity>> getCommunities() {
        return ResponseEntity.ok(adminSocialService.getAllCommunities());
    }

    @DeleteMapping("/communities/{id}")
    @RequireRole({"ADMIN", "MODERATOR"})
    public ResponseEntity<Map<String, String>> deleteCommunity(@PathVariable Long id, HttpServletRequest request) {
        String adminUserId = request.getHeader("X-User-Id");
        String adminUsername = adminUserService.getUsername(adminUserId);

        adminSocialService.deleteCommunity(id);

        auditLogService.log(adminUserId, adminUsername, "DELETE_COMMUNITY", "COMMUNITY", String.valueOf(id),
                "Deleted community #" + id, getClientIp(request));

        return ResponseEntity.ok(Map.of("message", "Community deleted successfully"));
    }

    // ===========================
    //  AUDIT LOGS (ADMIN only)
    // ===========================
    @GetMapping("/audit-logs")
    @RequireRole({"ADMIN"})
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(auditLogService.getLogs(page, size));
    }

    // ===========================
    //  HELPERS
    // ===========================
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
