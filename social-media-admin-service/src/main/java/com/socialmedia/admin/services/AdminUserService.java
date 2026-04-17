package com.socialmedia.admin.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.socialmedia.admin.dto.RoleDTO;
import com.socialmedia.admin.dto.UserAdminDTO;
import com.socialmedia.admin.entities.mongo.RoleDocument;
import com.socialmedia.admin.entities.mongo.UserDocument;
import com.socialmedia.admin.exceptions.ForbiddenException;
import com.socialmedia.admin.repositories.mongo.RoleDocumentRepository;
import com.socialmedia.admin.repositories.mongo.UserDocumentRepository;

@Service
public class AdminUserService {

    private final UserDocumentRepository userRepository;
    private final RoleDocumentRepository roleRepository;

    public AdminUserService(UserDocumentRepository userRepository, RoleDocumentRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public List<UserAdminDTO> getAllUsers(String query) {
        List<UserDocument> users;
        if (query != null && !query.isBlank()) {
            users = userRepository
                    .findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                            query, query, query);
        } else {
            users = userRepository.findAll();
        }
        return users.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long countAll() {
        return userRepository.count();
    }

    public long countBanned() {
        return userRepository.findAll().stream()
                .filter(u -> Boolean.FALSE.equals(u.getIsActive()))
                .count();
    }

    public void banUser(String targetUserId, String currentAdminUserId) {
        preventSelfAction(targetUserId, currentAdminUserId);
        UserDocument user = findUserOrThrow(targetUserId);
        preventAdminTargeting(user);
        user.setIsActive(false);
        userRepository.save(user);
    }

    public void unbanUser(String targetUserId, String currentAdminUserId) {
        preventSelfAction(targetUserId, currentAdminUserId);
        UserDocument user = findUserOrThrow(targetUserId);
        user.setIsActive(true);
        user.setLoginFailedCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    public void changeRole(String targetUserId, String newRoleName, String currentAdminUserId, String currentAdminRole) {
        preventSelfAction(targetUserId, currentAdminUserId);

        // Only ADMIN can assign ADMIN role — prevent privilege escalation
        if ("ADMIN".equals(newRoleName) && !"ADMIN".equals(currentAdminRole)) {
            throw new ForbiddenException("Only ADMIN can assign ADMIN role");
        }

        // Validate role exists
        RoleDocument newRole = roleRepository.findByRoleName(newRoleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + newRoleName));

        UserDocument user = findUserOrThrow(targetUserId);

        // Cannot change role of another ADMIN (only ADMIN can, and even then, protect against removing last admin)
        if (user.getRole() != null && "ADMIN".equals(user.getRole().getRoleName()) && !"ADMIN".equals(currentAdminRole)) {
            throw new ForbiddenException("Cannot change role of an ADMIN user");
        }

        user.setRole(newRole);
        userRepository.save(user);
    }

    public String getUsername(String userId) {
        return userRepository.findById(userId)
                .map(UserDocument::getUsername)
                .orElse("unknown");
    }

    // ===== Private helpers =====

    private UserDocument findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private void preventSelfAction(String targetId, String currentId) {
        if (targetId.equals(currentId)) {
            throw new ForbiddenException("Cannot modify your own account");
        }
    }

    private void preventAdminTargeting(UserDocument user) {
        if (user.getRole() != null && "ADMIN".equals(user.getRole().getRoleName())) {
            throw new ForbiddenException("Cannot ban an ADMIN user");
        }
    }

    private UserAdminDTO toDTO(UserDocument user) {
        RoleDTO roleDTO = null;
        if (user.getRole() != null) {
            roleDTO = new RoleDTO(user.getRole().getId(), user.getRole().getRoleName());
        }
        return new UserAdminDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roleDTO,
                user.getIsActive(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
