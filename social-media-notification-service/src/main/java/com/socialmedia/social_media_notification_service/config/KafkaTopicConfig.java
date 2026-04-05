package com.socialmedia.social_media_notification_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic socialNotificationEventsTopic(NotificationKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopics().getSocialEvents())
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatNotificationEventsTopic(NotificationKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopics().getChatEvents())
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic notificationDeadLetterTopic(NotificationKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopics().getDeadLetter())
                .partitions(8)
                .replicas(1)
                .build();
    }
}