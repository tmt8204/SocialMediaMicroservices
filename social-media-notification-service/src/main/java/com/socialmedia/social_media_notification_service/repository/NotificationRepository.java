package com.socialmedia.social_media_notification_service.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.socialmedia.social_media_notification_service.document.NotificationDocument;

@Repository
public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {

    long countByRecipientIdAndReadFalse(String recipientId);

    Optional<NotificationDocument> findByIdAndRecipientId(String id, String recipientId);

    boolean existsBySourceEventId(String sourceEventId);
}