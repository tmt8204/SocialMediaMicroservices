package com.socialmedia.user_service_social_media.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.socialmedia.user_service_social_media.dto.CreateProfileEvent;
import com.socialmedia.user_service_social_media.entities.User;
import com.socialmedia.user_service_social_media.repositories.UserRepository;

import java.time.Instant;

/**
 * KafkaEventConsumer
 * 
 * Service to consume events from Kafka topics.
 * Handles consuming CreateProfileEvent to create user profiles in the database.
 */
@Service
public class KafkaEventConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaEventConsumer.class);
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Consume CreateProfileEvent from Kafka topic
     * Creates a new user profile when a user is registered in auth service.
     * 
     * @param event The CreateProfileEvent containing user information
     */
    @KafkaListener(topics = "${spring.kafka.topic.create-profile}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeCreateProfileEvent(CreateProfileEvent event) {
        logger.info("Received CreateProfileEvent: userId={}, username={}, email={}", 
                    event.getUserId(), event.getUsername(), event.getEmail());
        
        try {
            // Check if user already exists
            if (userRepository.findByUserId(event.getUserId()).isPresent()) {
                logger.warn("User profile already exists for userId: {}", event.getUserId());
                return;
            }

            if (userRepository.findByUsername(event.getUsername()).isPresent()) {
                logger.warn("User profile already exists for username: {}", event.getUsername());
                return;
            }
            
            // Create new user profile
            User user = new User();
            user.setUserId(event.getUserId());
            user.setUsername(event.getUsername());
            user.setEmail(event.getEmail());
            user.setFullName(event.getFullName());
            user.setUpdateAt(Instant.now());
            
            // Save user to database
            User savedUser = userRepository.save(user);
            logger.info("User profile created successfully: userId={}, username={}", 
                        savedUser.getUserId(), savedUser.getUsername());
            
        } catch (Exception e) {
            logger.error("Error consuming CreateProfileEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create user profile from Kafka event", e);
        }
    }
}
