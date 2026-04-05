package com.socialmedia.social_media_chat_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.socialmedia.social_media_chat_service.config.ChatKafkaProperties;
import com.socialmedia.social_media_chat_service.event.ChatMessageEvent;
import com.socialmedia.social_media_chat_service.event.ChatReadEvent;
import com.socialmedia.social_media_chat_service.service.EventDedupService;

@Component
public class ChatRealtimeConsumer {

    private final ChatKafkaProperties kafkaProperties;
    private final EventDedupService eventDedupService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatRealtimeConsumer(ChatKafkaProperties kafkaProperties,
            EventDedupService eventDedupService,
            SimpMessagingTemplate messagingTemplate) {
        this.kafkaProperties = kafkaProperties;
        this.eventDedupService = eventDedupService;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(
            topics = "${chat.kafka.topics.message-events}",
            groupId = "${chat.kafka.realtime-fanout-group}-${chat.kafka.instance-id}")
    public void consumeMessageEvent(ChatMessageEvent event, Acknowledgment acknowledgment) {
        try {
            if (isOwnEvent(event.originInstanceId()) || !eventDedupService.markIfNew(event.eventId())) {
                acknowledgment.acknowledge();
                return;
            }

            messagingTemplate.convertAndSend("/topic/conversations/" + event.conversationId(), event);
            if (event.recipientIds() != null) {
                for (String recipientId : event.recipientIds()) {
                    messagingTemplate.convertAndSendToUser(recipientId, "/queue/events", event);
                }
            }
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            throw ex;
        }
    }

    @KafkaListener(
            topics = "${chat.kafka.topics.read-events}",
            groupId = "${chat.kafka.realtime-fanout-group}-${chat.kafka.instance-id}")
    public void consumeReadEvent(ChatReadEvent event, Acknowledgment acknowledgment) {
        try {
            if (isOwnEvent(event.originInstanceId()) || !eventDedupService.markIfNew(event.eventId())) {
                acknowledgment.acknowledge();
                return;
            }

            if (event.recipientIds() != null) {
                for (String recipientId : event.recipientIds()) {
                    messagingTemplate.convertAndSendToUser(recipientId, "/queue/events", event);
                }
            }
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            throw ex;
        }
    }

    private boolean isOwnEvent(String originInstanceId) {
        return originInstanceId != null && originInstanceId.equals(kafkaProperties.getInstanceId());
    }
}
