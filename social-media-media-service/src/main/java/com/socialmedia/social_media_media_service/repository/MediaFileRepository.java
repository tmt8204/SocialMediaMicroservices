package com.socialmedia.social_media_media_service.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.socialmedia.social_media_media_service.entities.MediaFileDocument;

@Repository
public interface MediaFileRepository extends MongoRepository<MediaFileDocument, String> {
    Optional<MediaFileDocument> findByPublicId(String publicId);
}
