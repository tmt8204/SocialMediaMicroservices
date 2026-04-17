package com.socialmedia.admin.repositories.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.socialmedia.admin.entities.mongo.UserDocument;

public interface UserDocumentRepository extends MongoRepository<UserDocument, String> {
    Optional<UserDocument> findByUsername(String username);
    List<UserDocument> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String username, String email, String fullName);
}
