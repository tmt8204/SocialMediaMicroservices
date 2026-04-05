package com.socialmedia.social_media_notification_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.socialmedia.social_media_notification_service.service.NotificationIngestionService;

@Component
public class ChatNotificationConsumer {

    private final NotificationIngestionService notificationIngestionService;

    public ChatNotificationConsumer(NotificationIngestionService notificationIngestionService) {
        this.notificationIngestionService = notificationIngestionService;
    }

    @KafkaListener(
            topics = "${notification.kafka.topics.chat-events}",
            groupId = "${notification.kafka.consumer-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String payload, Acknowledgment acknowledgment) {
        notificationIngestionService.processChatEvent(payload);
        acknowledgment.acknowledge();
    }
}