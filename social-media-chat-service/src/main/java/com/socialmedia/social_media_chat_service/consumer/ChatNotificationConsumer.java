package com.socialmedia.social_media_chat_service.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.socialmedia.social_media_chat_service.event.ChatNotificationEvent;
import com.socialmedia.social_media_chat_service.service.EventDedupService;

@Component
public class ChatNotificationConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatNotificationConsumer.class);

    private final EventDedupService eventDedupService;

    public ChatNotificationConsumer(EventDedupService eventDedupService) {
        this.eventDedupService = eventDedupService;
    }

    @KafkaListener(
            topics = "${chat.kafka.topics.notification-events}",
            groupId = "${chat.kafka.notification-audit-group:chat-notification-audit-group}")
    public void consumeNotificationAudit(ChatNotificationEvent event, Acknowledgment acknowledgment) {
        if (!eventDedupService.markIfNew(event.eventId())) {
            acknowledgment.acknowledge();
            return;
        }

        LOGGER.info("notification-event eventId={} recipient={} conversation={} online={}",
                event.eventId(), event.recipientId(), event.conversationId(), event.recipientOnline());
        acknowledgment.acknowledge();
    }
}
