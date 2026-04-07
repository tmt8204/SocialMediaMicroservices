1) Hiện trạng sau khi đọc code
`social-media-chat-service`

Hiện mới có skeleton rất cơ bản:
- `SocialMediaChatServiceApplication`
- `pom.xml`
- `application.properties`

Chưa thấy các phần quan trọng cho chat real-time:
- `WebSocketConfig`
- `ChatController` / `MessageController`
- `ChatMessageService`
- entity/document cho conversation, message, member
- repository cho MongoDB / Redis
- interceptor để auth khi WS `CONNECT`

`pom.xml` hiện mới có:
- validation
- devtools
- lombok

Chưa có dependency cho:
- WebSocket / STOMP
- MongoDB
- Redis
- REST API đầy đủ cho chat history

`application.properties` hiện mới có:

```properties
spring.application.name=social-media-chat-service
```

`api-gateway`

Gateway hiện đã có route REST cho chat-service:

```properties
spring.cloud.gateway.server.webflux.routes[4].id=chat-service
spring.cloud.gateway.server.webflux.routes[4].uri=${services.chat-service-url:http://localhost:8085}
spring.cloud.gateway.server.webflux.routes[4].predicates[0]=Path=/api/chat/**
```

Tuy nhiên hiện chưa thấy route riêng cho WebSocket như:
- `/ws/chat/**`
- `uri=ws://localhost:8085`

=> Kết luận: chat-service hiện vẫn ở mức skeleton, chưa có tầng DB + nghiệp vụ + WebSocket real-time hoàn chỉnh.

2) Mục tiêu
Hoàn thiện chat-service theo hướng real-time qua WebSocket, nhưng vẫn tách rõ trách nhiệm giữa REST và WS:

- REST dùng cho:
  - tạo / lấy conversation
  - lấy lịch sử tin nhắn
  - phân trang message cũ
  - lấy danh sách cuộc trò chuyện
- WebSocket dùng cho:
  - gửi tin nhắn real-time
  - nhận tin nhắn real-time
  - typing indicator
  - read receipt / delivered receipt
  - online / offline presence

Đồng thời đảm bảo:
- hỗ trợ chat 1-1 trước, mở rộng group chat sau
- lưu lịch sử message bền vững
- không lưu file nhị phân trong chat-service
- media đính kèm đi qua `media-service`, chat-service chỉ lưu metadata/url
- gateway đóng vai trò entrypoint cho cả REST và WS

3) Kiến trúc đề xuất
Phương án khuyến nghị

Tách 3 lớp rõ ràng:

- `api-gateway`: nhận request REST + upgrade WebSocket
- `chat-service`: xử lý domain chat, persist message, phát sự kiện real-time
- `media-service`: upload file, trả metadata để chat-service đính kèm vào message

Cách triển khai phù hợp nhất cho giai đoạn hiện tại:
- REST + STOMP over WebSocket cho kết nối client real-time
- MongoDB cho dữ liệu chat
- Redis cho presence / pub-sub nếu chạy nhiều instance
- Kafka cho event-driven giữa `chat-service`, `notification-service`, analytics và các consumer nội bộ

Lưu ý về vai trò:
- `WebSocket` dùng để đẩy sự kiện ngay tới frontend đang online
- `Kafka` không thay thế WebSocket, mà dùng để phát tán event nội bộ, retry và scale consumer theo kiểu bất đồng bộ

Flow đề xuất

Bước 1 — Mở conversation
- Client gọi REST tạo hoặc lấy `direct conversation` theo `targetUserId`
- chat-service trả về `conversationId`

Bước 2 — Kết nối WebSocket
- Client connect tới gateway qua endpoint kiểu:
  - `ws://localhost:8080/ws/chat`
- Sau handshake, gateway forward sang `chat-service`

Bước 3 — Subscribe channel
- user subscribe các queue/topic của mình:
  - `/user/queue/messages`
  - `/user/queue/conversations`
  - hoặc `/topic/conversations/{conversationId}`

Bước 4 — Gửi message real-time
- Client gửi payload tới `/app/chat.send`
- chat-service validate user có thuộc conversation không
- lưu message vào DB
- update snapshot `lastMessage`, `lastMessageAt`
- push message real-time cho người nhận

Bước 5 — Read / delivered / typing
- Client gửi event `read`, `delivered`, `typing`
- chat-service cập nhật trạng thái theo conversation/member
- push event realtime sang peer

Cách này hợp microservice hơn vì:
- REST và realtime tách trách nhiệm rõ
- dễ scale chat-service độc lập
- dễ thêm notification-service cho offline push
- phù hợp với gateway đang làm entrypoint chung

4) Thiết kế DB khuyến nghị
Khuyến nghị dùng `MongoDB` cho chat-service.

Lý do:
- message là dữ liệu tăng rất nhanh
- cần phân trang theo thời gian / cursor
- JSON document phù hợp với attachments, reply, metadata
- hệ sinh thái hiện tại đã có MongoDB ở `auth-service`, `user-service`, `api-gateway`

4.1. Collection `conversations`
Lưu thông tin tổng quan của cuộc trò chuyện.

Gợi ý fields:

```json
{
  "_id": "conv_001",
  "type": "DIRECT",
  "directKey": "minUser:maxUser",
  "createdBy": "userA",
  "participantIds": ["userA", "userB"],
  "lastMessageId": "msg_999",
  "lastMessagePreview": "Hello",
  "lastMessageSenderId": "userA",
  "lastMessageAt": "2026-04-01T10:00:00Z",
  "createdAt": "2026-04-01T09:00:00Z",
  "updatedAt": "2026-04-01T10:00:00Z",
  "active": true
}
```

Index nên có:
- unique `directKey` cho chat 1-1
- index `participantIds`
- index `lastMessageAt desc`

Ghi chú:
- với `DIRECT`, `directKey = min(userA, userB) + ":" + max(userA, userB)` để tránh tạo trùng 2 conversation cho cùng một cặp user
- với `GROUP`, không dùng `directKey`

4.2. Collection `conversation_members`
Lưu trạng thái theo từng user trong conversation.

Gợi ý fields:

```json
{
  "_id": "member_001",
  "conversationId": "conv_001",
  "userId": "userA",
  "role": "OWNER",
  "joinedAt": "2026-04-01T09:00:00Z",
  "lastReadMessageId": "msg_998",
  "lastReadAt": "2026-04-01T09:59:00Z",
  "lastDeliveredMessageId": "msg_999",
  "unreadCount": 0,
  "muted": false,
  "pinned": false,
  "hidden": false,
  "active": true
}
```

Index nên có:
- unique `(conversationId, userId)`
- index `(userId, hidden, lastReadAt)`

Mục đích:
- tính unread theo từng user
- lưu trạng thái mute/pin/hide riêng cho mỗi người
- không phải update mọi message khi user đã đọc

4.3. Collection `messages`
Đây là collection chính cho lịch sử chat.

Gợi ý fields:

```json
{
  "_id": "msg_999",
  "conversationId": "conv_001",
  "senderId": "userA",
  "seqNo": 999,
  "messageType": "TEXT",
  "content": "Hello world",
  "attachments": [
    {
      "publicId": "social-media/chat/userA/abc123",
      "mediaUrl": "https://res.cloudinary.com/...",
      "mediaType": "IMAGE"
    }
  ],
  "replyToMessageId": null,
  "status": "SENT",
  "edited": false,
  "deletedForEveryone": false,
  "createdAt": "2026-04-01T10:00:00Z",
  "updatedAt": "2026-04-01T10:00:00Z"
}
```

Index nên có:
- index `(conversationId, seqNo desc)`
- index `(conversationId, createdAt desc)`
- index `senderId`

Khuyến nghị:
- phân trang theo `seqNo` hoặc `createdAt` bằng cursor, không dùng page number lớn
- `attachments` chỉ lưu metadata/url từ `media-service`, không giữ binary file

4.4. Presence / typing / session state
Không nên nhét toàn bộ presence vào DB chính.

Khuyến nghị:
- dùng `Redis` cho:
  - `online:{userId}`
  - `typing:{conversationId}:{userId}`
  - pub/sub khi scale nhiều node WebSocket

Nếu chưa dùng Redis ở phase đầu:
- có thể giữ tạm presence in-memory
- nhưng chỉ phù hợp khi chạy 1 instance chat-service

4.5. Thiết kế Kafka cho chat
Kafka nên đóng vai trò `event backbone` cho các luồng bất đồng bộ, không phải channel giao tiếp trực tiếp với browser.

Các event nên phát ra sau khi message đã lưu DB thành công:
- `MESSAGE_CREATED` (topic domain)
- `MESSAGE_READ` (topic domain)
- `MESSAGE_RECALLED` (topic domain)
- `CONVERSATION_CREATED` (topic domain)
- `CHAT_MESSAGE_CREATED` (topic notification, one event per recipient)

Topic khuyến nghị:

- `chat-message-events`
  - chứa event tạo message, recall, edit
- `chat-read-events`
  - chứa read receipt / seen event
- `chat-notification-events`
  - dành cho `notification-service` consume để bắn push khi recipient offline
- `chat-dead-letter`
  - chứa message lỗi để retry / điều tra

Partition key nên dùng:
- `conversationId` cho `chat-message-events` và `chat-read-events`
- `recipientId` cho `chat-notification-events`

Lý do:
- cần giữ thứ tự message trong cùng 1 conversation cho domain chat
- cần fanout tối ưu theo người nhận cho offline notification

Payload gợi ý cho `chat-message-events`:

```json
{
  "eventId": "evt_001",
  "eventType": "MESSAGE_CREATED",
  "conversationId": "conv_001",
  "messageId": "msg_999",
  "senderId": "userA",
  "messageType": "TEXT",
  "contentPreview": "Hello world",
  "createdAt": "2026-04-01T10:00:00Z"
}
```

Payload gợi ý cho `chat-notification-events` (1 event/recipient):

```json
{
  "eventId": "evt_001_userB",
  "eventType": "CHAT_MESSAGE_CREATED",
  "conversationId": "conv_001",
  "messageId": "msg_999",
  "senderId": "userA",
  "senderDisplayName": "Nguyen Van A",
  "recipientId": "userB",
  "recipientOnline": false,
  "messageType": "TEXT",
  "contentPreview": "Hello world",
  "createdAt": "2026-04-01T10:00:00Z"
}
```

Consumer group gợi ý:
- `chat-realtime-fanout-group`
  - chat-service consume lại để fan-out tới WebSocket sessions khi chạy nhiều instance
- `notification-service-group`
  - gửi push/in-app notification cho user offline
- `chat-analytics-group`
  - đếm volume chat, active conversation, monitoring
- `chat-moderation-group` *(nếu có)*
  - scan từ khoá nhạy cảm, abuse detection

Rule quan trọng:
- Message phải được `persist vào MongoDB trước`, rồi mới publish Kafka event
- Nên dùng `Outbox Pattern` nếu muốn tránh mất event khi DB save thành công nhưng publish Kafka thất bại
- Event cần có `eventId` / `messageId` để consumer xử lý idempotent
- Không nên gửi toàn bộ nội dung media lớn qua Kafka, chỉ gửi metadata và preview

=> Tóm lại:
- WebSocket = realtime cho client online
- Kafka = async integration, decouple và scale-out nội bộ

5) Nghiệp vụ nên chốt
5.1. Chat 1-1
- Mỗi cặp user chỉ có 1 direct conversation.
- Nếu conversation đã tồn tại thì trả lại conversation cũ, không tạo mới.
- Chỉ user thuộc conversation mới được đọc/gửi message.

5.2. Gửi tin nhắn
- Validate sender là member của conversation.
- Không cho gửi message rỗng nếu không có attachment.
- Nếu có attachment, client phải upload qua `media-service` trước.
- Sau khi lưu message:
  - update `conversations.lastMessage*`
  - tăng `unreadCount` cho các member còn lại
  - push real-time cho recipient

5.3. Read receipt
- Khi user mở chat hoặc đọc tới message mới nhất:
  - update `conversation_members.lastReadMessageId`
  - update `lastReadAt`
  - reset hoặc giảm `unreadCount`
- Với MVP, chỉ cần lưu `lastReadMessageId` theo member là đủ.
- Không cần tạo 1 record read receipt cho từng message nếu chưa có yêu cầu group lớn.

5.4. Delivered receipt
- Khi message đã được deliver tới session người nhận, có thể phát event `DELIVERED`.
- Nếu chưa cần quá chi tiết, có thể bỏ phase đầu và chỉ giữ `SENT` + `SEEN`.

5.5. Recall / delete
Nên tách rõ 2 nghiệp vụ:
- `delete for me`: chỉ ẩn message/conversation ở phía user hiện tại
- `recall for everyone`: đổi nội dung message thành placeholder như `This message was recalled`

Khuyến nghị giai đoạn đầu:
- hỗ trợ `recall` trong khoảng thời gian giới hạn, ví dụ 15 phút
- không xóa cứng record message để tránh lệch lịch sử

5.6. Online / offline / typing
- online/offline và typing chỉ là trạng thái realtime, không cần persist dài hạn
- chat-service không nên gọi sync trực tiếp `notification-service`
- chat-service publish `chat-notification-events` và `notification-service` consume bất đồng bộ

5.7. Flow nghiệp vụ khi có Kafka
Flow khuyến nghị khi user gửi tin nhắn:

Bước 1 — Client gửi message qua WebSocket
- payload đi vào `/app/chat.send`

Bước 2 — `chat-service` validate nghiệp vụ
- kiểm tra membership
- kiểm tra content / attachment hợp lệ

Bước 3 — Lưu message vào MongoDB
- tạo `messages`
- update `conversations.lastMessage*`
- update `conversation_members.unreadCount`

Bước 4 — Publish event sang Kafka
- publish domain event vào `chat-message-events`
- key = `conversationId`
- publish notification event vào `chat-notification-events` (1 event/recipient)
- key = `recipientId`

Bước 5 — Các consumer xử lý song song
- WebSocket fanout consumer đẩy ngay cho user đang online
- `notification-service` gửi push nếu user offline
- analytics / moderation xử lý nền

Cách này giúp tránh coupling mạnh giữa chat và notification, đồng thời dễ retry khi downstream lỗi.

6) API và WebSocket nên có
6.1. REST API
Tạo hoặc lấy direct conversation

`POST /api/chat/conversations/direct/{targetUserId}`

Response:

```json
{
  "conversationId": "conv_001",
  "type": "DIRECT",
  "participantIds": ["userA", "userB"],
  "lastMessagePreview": "Hello"
}
```

Lấy danh sách conversation của user

`GET /api/chat/conversations`

Lấy lịch sử tin nhắn

`GET /api/chat/conversations/{conversationId}/messages?cursor=999&limit=20`

Đánh dấu đã đọc

`PUT /api/chat/conversations/{conversationId}/read`

Thu hồi message

`PUT /api/chat/messages/{messageId}/recall`

6.2. WebSocket / STOMP
Đề xuất endpoint:

- Handshake endpoint: `/ws/chat`
- App destinations:
  - `/app/chat.send`
  - `/app/chat.read`
  - `/app/chat.typing`
- User queues:
  - `/user/queue/messages`
  - `/user/queue/events`
- Topic theo conversation:
  - `/topic/conversations/{conversationId}`

Payload gửi message gợi ý:

```json
{
  "conversationId": "conv_001",
  "clientMessageId": "cmsg_123",
  "messageType": "TEXT",
  "content": "Hello from websocket",
  "attachments": []
}
```

Payload typing gợi ý:

```json
{
  "conversationId": "conv_001",
  "typing": true
}
```

7) Thay đổi cần làm ở `social-media-chat-service`
7.1. Bổ sung dependency
Nên thêm vào `pom.xml`:

- `spring-boot-starter-webmvc`
- `spring-boot-starter-websocket`
- `spring-boot-starter-data-mongodb`
- `spring-boot-starter-data-redis` (nếu dùng presence/pub-sub)
- `spring-kafka`
- validation

7.2. Bổ sung config
Trong `application.properties` hoặc env:

```properties
server.port=8085
spring.application.name=social-media-chat-service
spring.data.mongodb.uri=${CHAT_MONGO_URI:mongodb://localhost:27017/socialmediachatdb}
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
chat.kafka.topics.message-events=chat-message-events
chat.kafka.topics.read-events=chat-read-events
chat.kafka.topics.notification-events=chat-notification-events
chat.kafka.topics.dead-letter=chat-dead-letter
services.user-service-url=${USER_SERVICE_URL:http://localhost:8082}
services.media-service-url=${MEDIA_SERVICE_URL:http://localhost:8084}
services.notification-service-url=${NOTIFICATION_SERVICE_URL:http://localhost:8086}
```

Nếu dùng STOMP broker đơn giản:

```properties
chat.websocket.allowed-origins=http://localhost:3000,http://localhost:5173
```

Nếu dùng Kafka production tốt hơn, nên thêm:

```properties
spring.kafka.producer.acks=all
spring.kafka.producer.retries=10
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=manual
```

7.3. Các class nên có
Đề xuất thêm:

- `config/WebSocketConfig.java`
- `config/WebSocketAuthChannelInterceptor.java`
- `config/KafkaTopicConfig.java`
- `controller/ConversationController.java`
- `controller/MessageController.java`
- `ws/ChatMessageWsController.java`
- `service/ConversationService.java`
- `service/MessageService.java`
- `service/PresenceService.java`
- `service/ChatEventProducer.java`
- `consumer/ChatRealtimeConsumer.java`
- `consumer/ChatNotificationConsumer.java`
- `outbox/OutboxEventDocument.java` *(nếu áp dụng outbox pattern)*
- `document/ConversationDocument.java`
- `document/ConversationMemberDocument.java`
- `document/MessageDocument.java`
- `repository/ConversationRepository.java`
- `repository/ConversationMemberRepository.java`
- `repository/MessageRepository.java`
- `dto/*`

8) WebSocket config trong `api-gateway`
Hiện gateway mới route REST `/api/chat/**`.

Để hỗ trợ real-time, nên bổ sung thêm route WS riêng:

```properties
spring.cloud.gateway.server.webflux.routes[6].id=chat-ws
spring.cloud.gateway.server.webflux.routes[6].uri=${services.chat-service-ws-url:ws://localhost:8085}
spring.cloud.gateway.server.webflux.routes[6].predicates[0]=Path=/ws/chat/**
```

Và thêm biến môi trường:

```properties
services.chat-service-ws-url=ws://localhost:8085
```

**Quan trọng: Gateway nên hỗ trợ cả HTTP + WebSocket, không nên là pure WebSocket**

Lý do:
- Gateway cần route REST calls cho tất cả services (auth, user, social, media, notification)
- WebSocket là stateful, khó scale hơn HTTP, không phù hợp làm routing layer
- Chat service cần cả HTTP (lịch sử, REST CRUD) + WebSocket (real-time push)
- Client có thể gọi `/api/chat/**` (REST) và `/ws/chat/**` (STOMP) mà không cần reconnect

Design này tách trách nhiệm rõ ràng:
- REST `/api/chat/**` → HTTP request-response
- WebSocket `/ws/chat/**` → STOMP push channel
- Gateway chỉ làm forwarding, không handle application logic

Lưu ý rất quan trọng:

`JwtAuthenticationGatewayFilter` hiện đang check JWT qua HTTP header `Authorization`.
Với browser WebSocket handshake, cách này thường không ổn định vì frontend khó set custom header trong lệnh `new WebSocket(...)` thuần.

Có 2 hướng:

**Hướng khuyến nghị**
- cho phép handshake path `/ws/chat/**` đi qua gateway
- validate JWT ở `chat-service` bằng `ChannelInterceptor` tại frame `CONNECT`
- token được gửi trong STOMP native headers

Ví dụ thêm public path ở gateway:

```properties
gateway.auth.public-paths[3]=/ws/chat/**
```

Sau đó auth thật sự ở `chat-service`.

**Hướng thay thế**
- giữ auth ở gateway
- sửa `JwtAuthenticationGatewayFilter` để đọc token từ query param như `?token=...` cho path `/ws/chat/**`

=> Với Spring STOMP, hướng khuyến nghị thường sạch hơn và dễ làm frontend hơn.

9) Kế hoạch triển khai đề xuất
Phase 1 — Dựng nền chat-service
- thêm MongoDB + WebSocket dependency
- tạo collections `conversations`, `conversation_members`, `messages`
- dựng REST API lấy/create conversation + history

Phase 2 — Realtime WebSocket
- thêm `WebSocketConfig`
- thêm `ChannelInterceptor` để auth JWT khi `CONNECT`
- hỗ trợ `chat.send`, `chat.read`, `chat.typing`

Phase 3 — Kafka integration
- thêm `spring-kafka`
- publish `MESSAGE_CREATED` / `MESSAGE_READ` vào `chat-message-events`
- publish `CHAT_MESSAGE_CREATED` vào `chat-notification-events` theo recipient
- nối `notification-service` và realtime fanout consumer
- cân nhắc `Outbox Pattern` để publish event an toàn

Phase 4 — Gateway integration
- thêm route `/ws/chat/**` trong `api-gateway`
- chốt cách truyền token cho WS
- test end-to-end qua gateway thay vì connect trực tiếp service

Phase 5 — Hardening
- unread count
- recall/edit message
- offline notification qua `notification-service`
- Redis pub/sub + presence cho multi-instance
- DLQ / retry / idempotent consumer cho Kafka
- test race condition và reconnect

10) Kết luận
`chat-service` nên đi theo hướng:

- `MongoDB` cho lịch sử chat
- `WebSocket/STOMP` cho real-time event tới frontend
- `Kafka` cho event-driven integration giữa các service
- `REST` cho bootstrap và lịch sử
- `api-gateway` route cả HTTP lẫn WS
- `media-service` xử lý file đính kèm
- `notification-service` xử lý người dùng offline

Ưu điểm:
- đúng hướng microservice
- dễ scale theo tải chat real-time
- tách rõ domain chat với media và notification
- phù hợp với kiến trúc gateway đang có

Nếu cần triển khai nhanh MVP, nên ưu tiên thứ tự:
1. direct chat
2. history + unread
3. WebSocket realtime
4. read receipt / typing
5. group chat + Redis scale-out
