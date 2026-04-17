package com.socialmedia.social_media_media_service.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.socialmedia.social_media_media_service.entities.UserStorageDocument;

@Repository
public interface UserStorageRepository extends MongoRepository<UserStorageDocument, String> {
}
