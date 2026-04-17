package com.socialmedia.admin.entities.mongo;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    private String adminUserId;
    private String adminUsername;
    private String action;        // BAN_USER, UNBAN_USER, DELETE_POST, CHANGE_ROLE, etc.
    private String targetType;    // USER, POST, COMMENT, COMMUNITY
    private String targetId;
    private String details;
    private String ipAddress;

    @CreatedDate
    private Instant createdAt;
}
