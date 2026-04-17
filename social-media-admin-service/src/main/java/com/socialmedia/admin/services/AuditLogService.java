package com.socialmedia.admin.services;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.socialmedia.admin.entities.mongo.AuditLog;
import com.socialmedia.admin.repositories.mongo.AuditLogRepository;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log an admin action for audit trail.
     */
    public void log(String adminUserId, String adminUsername, String action,
                    String targetType, String targetId, String details, String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAdminUserId(adminUserId);
        auditLog.setAdminUsername(adminUsername);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetails(details);
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(Instant.now());
        auditLogRepository.save(auditLog);
    }

    public Page<AuditLog> getLogs(int page, int size) {
        return auditLogRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }
}
