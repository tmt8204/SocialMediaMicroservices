package com.socialmedia.auth.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.socialmedia.auth.dto.CreateProfileEvent;

/**
 * KafkaEventProducer
 * 
 * Service to produce and publish events to Kafka topics.
 * Handles sending CreateProfileEvent to notify user-service about new user registration.
 */
@Service
public class KafkaEventProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaEventProducer.class);
    
    @Autowired
    private KafkaTemplate<String, CreateProfileEvent> kafkaTemplate;
    
    @Value("${spring.kafka.topic.create-profile}")
    private String createProfileTopic;

    /**
     * Publish CreateProfileEvent to Kafka topic
     * 
     * @param event The CreateProfileEvent to publish
     */
    public void publishCreateProfileEvent(CreateProfileEvent event) {
        try {
            logger.info("Publishing CreateProfileEvent for user: {} with userId: {}", 
                event.getUsername(), event.getUserId());
            
            // Send message with topic, key, and value
            kafkaTemplate.send(createProfileTopic, event.getUserId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to publish CreateProfileEvent for user: {}. Error: {}", 
                            event.getUsername(), ex.getMessage(), ex);
                    } else {
                        logger.info("Successfully published CreateProfileEvent for user: {} to topic: {}", 
                            event.getUsername(), createProfileTopic);
                    }
                });
        } catch (Exception e) {
            logger.error("Error publishing CreateProfileEvent for user: {}. Exception: {}", 
                event.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish CreateProfileEvent to Kafka", e);
        }
    }

    /**
     * Publish CreateProfileEvent synchronously (blocking)
     * Use this if you need to ensure the message is sent before proceeding
     * 
     * @param event The CreateProfileEvent to publish
     */
    public void publishCreateProfileEventSync(CreateProfileEvent event) {
        try {
            logger.info("Publishing CreateProfileEvent synchronously for user: {} with userId: {}", 
                event.getUsername(), event.getUserId());
            
            // Send synchronously and wait for result
            var sendResult = kafkaTemplate.send(createProfileTopic, event.getUserId(), event).get();
            logger.info("Successfully published CreateProfileEvent for user: {} to partition: {} with offset: {}", 
                event.getUsername(), 
                sendResult.getRecordMetadata().partition(),
                sendResult.getRecordMetadata().offset());
        } catch (Exception e) {
            logger.error("Error publishing CreateProfileEvent synchronously for user: {}. Exception: {}", 
                event.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish CreateProfileEvent to Kafka", e);
        }
    }
}
