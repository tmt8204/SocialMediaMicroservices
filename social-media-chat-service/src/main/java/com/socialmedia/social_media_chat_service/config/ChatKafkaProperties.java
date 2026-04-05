package com.socialmedia.social_media_chat_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chat.kafka")
public class ChatKafkaProperties {

    private final Topics topics = new Topics();

    private String instanceId;
    private String realtimeFanoutGroup = "chat-realtime-fanout-group";
    private String notificationAuditGroup = "chat-notification-audit-group";

    public Topics getTopics() {
        return topics;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getRealtimeFanoutGroup() {
        return realtimeFanoutGroup;
    }

    public void setRealtimeFanoutGroup(String realtimeFanoutGroup) {
        this.realtimeFanoutGroup = realtimeFanoutGroup;
    }

    public String getNotificationAuditGroup() {
        return notificationAuditGroup;
    }

    public void setNotificationAuditGroup(String notificationAuditGroup) {
        this.notificationAuditGroup = notificationAuditGroup;
    }

    public static class Topics {
        private String messageEvents;
        private String readEvents;
        private String notificationEvents;
        private String deadLetter;

        public String getMessageEvents() {
            return messageEvents;
        }

        public void setMessageEvents(String messageEvents) {
            this.messageEvents = messageEvents;
        }

        public String getReadEvents() {
            return readEvents;
        }

        public void setReadEvents(String readEvents) {
            this.readEvents = readEvents;
        }

        public String getNotificationEvents() {
            return notificationEvents;
        }

        public void setNotificationEvents(String notificationEvents) {
            this.notificationEvents = notificationEvents;
        }

        public String getDeadLetter() {
            return deadLetter;
        }

        public void setDeadLetter(String deadLetter) {
            this.deadLetter = deadLetter;
        }
    }
}
