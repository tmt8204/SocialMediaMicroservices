package com.socialmedia.admin.repositories.mongo;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.socialmedia.admin.entities.mongo.RoleDocument;

public interface RoleDocumentRepository extends MongoRepository<RoleDocument, String> {
    Optional<RoleDocument> findByRoleName(String roleName);
}
