package com.socialmedia.admin.repositories.mongo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.socialmedia.admin.entities.mongo.AuditLog;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
