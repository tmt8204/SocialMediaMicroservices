package com.socialmedia.social_media_notification_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.socialmedia.social_media_notification_service.service.NotificationIngestionService;

@Component
public class SocialNotificationConsumer {

    private final NotificationIngestionService notificationIngestionService;

    public SocialNotificationConsumer(NotificationIngestionService notificationIngestionService) {
        this.notificationIngestionService = notificationIngestionService;
    }

    @KafkaListener(
            topics = "${notification.kafka.topics.social-events}",
            groupId = "${notification.kafka.consumer-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String payload, Acknowledgment acknowledgment) {
        notificationIngestionService.processSocialEvent(payload);
        acknowledgment.acknowledge();
    }
}