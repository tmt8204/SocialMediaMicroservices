package com.socialmedia.social_media_notification_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "notification.kafka")
public class NotificationKafkaProperties {

    private String consumerGroup = "notification-service-group";
    private final Topics topics = new Topics();

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public Topics getTopics() {
        return topics;
    }

    public static class Topics {
        private String socialEvents;
        private String chatEvents;
        private String deadLetter;

        public String getSocialEvents() {
            return socialEvents;
        }

        public void setSocialEvents(String socialEvents) {
            this.socialEvents = socialEvents;
        }

        public String getChatEvents() {
            return chatEvents;
        }

        public void setChatEvents(String chatEvents) {
            this.chatEvents = chatEvents;
        }

        public String getDeadLetter() {
            return deadLetter;
        }

        public void setDeadLetter(String deadLetter) {
            this.deadLetter = deadLetter;
        }
    }
}