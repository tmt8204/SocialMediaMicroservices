package com.socialmedia.social_media_chat_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic chatMessageEventsTopic(ChatKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopics().getMessageEvents())
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatReadEventsTopic(ChatKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopics().getReadEvents())
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatNotificationEventsTopic(ChatKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopics().getNotificationEvents())
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatDeadLetterTopic(ChatKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopics().getDeadLetter())
                .partitions(8)
                .replicas(1)
                .build();
    }
}
