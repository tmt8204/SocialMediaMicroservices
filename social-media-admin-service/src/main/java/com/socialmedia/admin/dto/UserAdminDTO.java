package com.socialmedia.admin.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminDTO {
    private String _id;
    private String username;
    private String email;
    private String fullName;
    private RoleDTO role;
    private Boolean isActive;
    private Instant lastLoginAt;
    private Instant createdAt;
}
