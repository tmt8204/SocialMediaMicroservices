# Chat Service Frontend Integration

## 1. Tổng quan

`social-media-chat-service` là chat service realtime dùng:

- Spring Boot
- WebSocket thuần, không bật SockJS
- STOMP protocol
- MongoDB để lưu conversation và message
- Kafka để fanout event nội bộ và notification audit

Chat service hiện hỗ trợ:

- tạo hoặc lấy direct conversation 1-1
- lấy danh sách conversation
- lấy lịch sử tin nhắn theo cursor
- gửi tin nhắn realtime qua STOMP
- mark as read
- recall message
- typing indicator

## Base URL

Khuyến nghị frontend gọi qua gateway:

- REST: `http://localhost:8080/api/chat`
- WebSocket STOMP endpoint: `ws://localhost:8080/ws/chat`

Nếu gọi trực tiếp vào chat-service:

- REST: `http://localhost:8085/api/chat`
- WebSocket STOMP endpoint: `ws://localhost:8085/ws/chat`

---

## 2. WebSocket

## Endpoint

Endpoint STOMP thực tế là:

- `/ws/chat`

Ví dụ qua gateway:

```text
ws://localhost:8080/ws/chat
```

Chat service không bật SockJS, nên frontend nên dùng STOMP client kết nối WebSocket trực tiếp.

## Protocol

- Protocol: `STOMP over WebSocket`
- Application destination prefix: `/app`
- User destination prefix: `/user`
- Broker prefixes: `/topic`, `/queue`

## Cách connect

Gateway hiện cho phép public path `/ws/chat/**`, nghĩa là gateway không đọc JWT ở lớp HTTP handshake cho WebSocket này.

Thay vào đó, chat-service tự xác thực JWT ở frame `CONNECT` của STOMP thông qua `WebSocketAuthChannelInterceptor`.

### Header nên gửi khi CONNECT

Khuyến nghị frontend luôn gửi native header:

```text
Authorization: Bearer <accessToken>
```

Ngoài ra code hiện tại còn chấp nhận fallback:

- `authorization: Bearer <accessToken>`
- `token: <accessToken>`

Khuyến nghị chỉ dùng `Authorization` để thống nhất.

### Ví dụ CONNECT frame

```text
CONNECT
accept-version:1.2
heart-beat:10000,10000
Authorization:Bearer <accessToken>

\u0000
```

Lưu ý:

- Giá trị `Authorization` phải bắt đầu bằng `Bearer `
- JWT phải là access token hợp lệ
- `sub` của JWT sẽ được dùng làm `Principal.getName()` trên WebSocket session

## Destinations FE cần dùng

### Client gửi

- `/app/chat.send`
- `/app/chat.read`
- `/app/chat.typing`

### Client subscribe

- `/user/queue/messages`
- `/user/queue/events`
- `/user/queue/errors`
- `/topic/conversations/{conversationId}`

## Mapping destination khuyến nghị cho frontend

### Kênh chính nên dùng

- `/user/queue/messages`: nhận message payload hoàn chỉnh để render chat bubble
- `/user/queue/events`: nhận event như read, typing, recall, conversation lifecycle
- `/user/queue/errors`: nhận lỗi STOMP do backend trả về

### Kênh nên dùng thận trọng

- `/topic/conversations/{conversationId}`

Lý do: theo implementation hiện tại, kênh topic này có thể nhận payload khác nhau tùy luồng:

- khi message được gửi trên cùng instance, server broadcast `MessageResponse`
- khi event đi qua Kafka fanout giữa nhiều instance, consumer broadcast `ChatMessageEvent`

Vì vậy, nếu frontend cần contract ổn định, nên ưu tiên `/user/queue/messages` và `/user/queue/events` thay vì coi `/topic/conversations/{conversationId}` là nguồn dữ liệu duy nhất.

---

## 3. Message Format

## 3.1. JSON khi gửi message

Destination:

- `/app/chat.send`

Payload:

```json
{
  "conversationId": "67ef2b4d9ab123456789abcd",
  "clientMessageId": "fe-msg-001",
  "messageType": "TEXT",
  "content": "Hello from frontend",
  "attachments": []
}
```

### Field

- `conversationId`: bắt buộc
- `clientMessageId`: hiện có trong DTO nhưng backend chưa dùng để dedupe
- `messageType`: optional, mặc định backend sẽ dùng `TEXT` nếu null
- `content`: optional nếu có attachment
- `attachments`: optional nếu có content

Rule validate thực tế:

- phải có `conversationId`
- phải có ít nhất một trong hai: `content` hoặc `attachments`

## 3.2. Gửi message có attachment

Chat-service không upload file nhị phân. Frontend phải upload file trước sang media-service rồi gửi metadata vào chat-service.

Ví dụ:

```json
{
  "conversationId": "67ef2b4d9ab123456789abcd",
  "clientMessageId": "fe-msg-002",
  "messageType": "IMAGE",
  "content": "",
  "attachments": [
    {
      "publicId": "social-media/chat/userA/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/chat.jpg",
      "mediaType": "IMAGE"
    }
  ]
}
```

## 3.3. JSON khi nhận message chính

Destination khuyến nghị để render message:

- `/user/queue/messages`

Payload là `MessageResponse`:

```json
{
  "id": "67ef2d129ab123456789abce",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "senderId": "userA",
  "seqNo": 1,
  "messageType": "TEXT",
  "content": "Hello from frontend",
  "attachments": [],
  "replyToMessageId": null,
  "status": "SENT",
  "edited": false,
  "deletedForEveryone": false,
  "createdAt": "2026-04-06T10:00:00Z",
  "updatedAt": "2026-04-06T10:00:00Z"
}
```

### Enum hiện tại

`messageType` hỗ trợ:

- `TEXT`
- `IMAGE`
- `VIDEO`
- `FILE`
- `AUDIO`
- `SYSTEM`

`status` hỗ trợ:

- `SENT`
- `DELIVERED`
- `SEEN`

`memberRole` trong conversation:

- `OWNER`
- `ADMIN`
- `MEMBER`

`conversationType`:

- `DIRECT`
- `GROUP`

## 3.4. JSON khi nhận event read/typing/recall

Destination khuyến nghị:

- `/user/queue/events`

### Read event từ WebSocket controller

Khi người dùng đọc message qua `/app/chat.read`, backend có thể đẩy event dạng map đơn giản:

```json
{
  "eventType": "MESSAGE_READ",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "messageId": "67ef2d129ab123456789abce",
  "userId": "userB"
}
```

### Typing event

```json
{
  "eventType": "TYPING",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "userId": "userB",
  "typing": true
}
```

### Kafka read event fanout

Trong multi-instance, backend cũng có thể đẩy `ChatReadEvent` qua `/user/queue/events` với payload record đầy đủ hơn:

```json
{
  "eventId": "d1a9b5a3-0d88-4b9b-b0cf-0af3f086c001",
  "eventType": "MESSAGE_READ",
  "originInstanceId": "2dd8df42-1f66-4aa0-9e10-b77b2f15f4f6",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "messageId": "67ef2d129ab123456789abce",
  "userId": "userB",
  "recipientIds": ["userA", "userB"],
  "createdAt": "2026-04-06T10:05:00Z"
}
```

### Recall event

Khi recall message, frontend có thể nhận event kiểu `ChatMessageEvent` trên `/user/queue/events` hoặc `/topic/conversations/{conversationId}`:

```json
{
  "eventId": "f2049fa4-7bf3-4679-8d73-f5f6f395b001",
  "eventType": "MESSAGE_RECALLED",
  "originInstanceId": "2dd8df42-1f66-4aa0-9e10-b77b2f15f4f6",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "messageId": "67ef2d129ab123456789abce",
  "senderId": "userA",
  "messageType": "SYSTEM",
  "contentPreview": "This message was recalled",
  "recipientIds": ["userA", "userB"],
  "createdAt": "2026-04-06T10:06:00Z"
}
```

### Lưu ý cho FE

Do `/user/queue/events` có thể chứa nhiều shape payload khác nhau, frontend nên branch theo `eventType` trước rồi mới parse field liên quan.

## 3.5. JSON lỗi WebSocket

Destination:

- `/user/queue/errors`

Payload:

```json
{
  "status": 400,
  "message": "conversationId is required",
  "error": "Bad Request",
  "timestamp": 1775479200000
}
```

Các mã lỗi có thể gặp:

- `400`: request không hợp lệ
- `401`: token sai hoặc thiếu trong CONNECT
- `403`: không thuộc conversation
- `500`: lỗi nội bộ

---

## 4. API REST

Frontend nên gọi qua gateway với header:

```http
Authorization: Bearer <accessToken>
```

Gateway sẽ tự inject `X-User-Id` xuống chat-service. Nếu gọi trực tiếp vào service để test local, có thể gửi `X-User-Id` thủ công.

## 4.1. Tạo hoặc lấy direct conversation

### Method + URL

- `POST /api/chat/conversations/direct/{targetUserId}`

Ví dụ:

```http
POST http://localhost:8080/api/chat/conversations/direct/userB
Authorization: Bearer <accessToken>
```

Response:

```json
{
  "conversationId": "67ef2b4d9ab123456789abcd",
  "type": "DIRECT",
  "participantIds": ["userA", "userB"],
  "lastMessageId": null,
  "lastMessagePreview": null,
  "lastMessageSenderId": null,
  "lastMessageAt": null,
  "createdAt": "2026-04-06T09:30:00Z"
}
```

Rule:

- nếu conversation 1-1 đã tồn tại thì backend trả conversation cũ
- nếu chưa có thì tạo mới
- không được tạo conversation với chính mình

## 4.2. Lấy danh sách conversation

### Method + URL

- `GET /api/chat/conversations`

Response:

```json
[
  {
    "conversationId": "67ef2b4d9ab123456789abcd",
    "type": "DIRECT",
    "participantIds": ["userA", "userB"],
    "lastMessageId": "67ef2d129ab123456789abce",
    "lastMessagePreview": "Hello from frontend",
    "lastMessageSenderId": "userA",
    "lastMessageAt": "2026-04-06T10:00:00Z",
    "createdAt": "2026-04-06T09:30:00Z"
  }
]
```

Lưu ý:

- response hiện chưa có `unreadCount`
- nếu frontend cần unread badge theo conversation, backend hiện chưa expose field này trong `ConversationResponse`

## 4.3. Lấy lịch sử message

### Method + URL

- `GET /api/chat/conversations/{conversationId}/messages?limit=20`
- `GET /api/chat/conversations/{conversationId}/messages?cursor=15&limit=20`

Response:

```json
{
  "messages": [
    {
      "id": "67ef2d129ab123456789abce",
      "conversationId": "67ef2b4d9ab123456789abcd",
      "senderId": "userA",
      "seqNo": 15,
      "messageType": "TEXT",
      "content": "Hello from frontend",
      "attachments": [],
      "replyToMessageId": null,
      "status": "SENT",
      "edited": false,
      "deletedForEveryone": false,
      "createdAt": "2026-04-06T10:00:00Z",
      "updatedAt": "2026-04-06T10:00:00Z"
    }
  ],
  "nextCursor": 15,
  "hasMore": false,
  "totalCount": 1
}
```

Rule:

- pagination dùng `cursor` theo `seqNo`, không dùng page number
- backend trả messages theo `seqNo desc`
- FE thường sẽ cần reverse list ở UI nếu muốn hiển thị cũ -> mới

## 4.4. Lấy danh sách member của conversation

### Method + URL

- `GET /api/chat/conversations/{conversationId}/members`

Response (array):

```json
[
  {
    "conversationId": "67ef2b4d9ab123456789abcd",
    "userId": "userA",
    "role": "OWNER",
    "lastReadMessageId": "67ef2d129ab123456789abce",
    "lastReadAt": "2026-04-06T10:05:00Z",
    "unreadCount": 0,
    "muted": false,
    "pinned": false,
    "hidden": false
  },
  {
    "conversationId": "67ef2b4d9ab123456789abcd",
    "userId": "userB",
    "role": "MEMBER",
    "lastReadMessageId": null,
    "lastReadAt": null,
    "unreadCount": 3,
    "muted": false,
    "pinned": false,
    "hidden": false
  }
]
```

Field:

- `role`: `OWNER` | `ADMIN` | `MEMBER`
- `unreadCount`: số message chưa đọc của member đó
- `lastReadMessageId`: message cuối member đó đã đọc tới
- `muted`, `pinned`, `hidden`: preference của member đó trong conversation

Lưu ý: đây là nội bộ conversation, hiện chưa expose `unreadCount` trong `ConversationResponse` từ `GET /api/chat/conversations`. Nếu cần badge unread trên màn danh sách, frontend có thể gọi endpoint này hoặc track local từ realtime event.

---

## 4.5. Mark as read

### Method + URL

- `PUT /api/chat/conversations/{conversationId}/read`

Body có thể gửi:

```json
{
  "conversationId": "67ef2b4d9ab123456789abcd",
  "messageId": "67ef2d129ab123456789abce"
}
```

Hoặc có thể không gửi body, backend sẽ dùng message mới nhất làm boundary đọc.

Response:

```http
204 No Content
```

Rule:

- reset `unreadCount` của member về 0
- đánh dấu các message từ người khác là `SEEN` tới boundary tương ứng
- publish read event ra Kafka

## 4.6. Recall message

### Method + URL

- `PUT /api/chat/messages/{messageId}/recall`

Response:

```json
{
  "id": "67ef2d129ab123456789abce",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "senderId": "userA",
  "seqNo": 15,
  "messageType": "SYSTEM",
  "content": "This message was recalled",
  "attachments": [],
  "replyToMessageId": null,
  "status": "SENT",
  "edited": false,
  "deletedForEveryone": true,
  "createdAt": "2026-04-06T10:00:00Z",
  "updatedAt": "2026-04-06T10:06:00Z"
}
```

Rule:

- chỉ sender mới recall được
- cửa sổ recall hiện tại là 15 phút
- sau recall, message bị đổi nội dung thành `This message was recalled`
- attachment bị xóa khỏi payload message

## 4.7. REST error format

Ví dụ lỗi:

```json
{
  "status": 400,
  "message": "Cannot create direct conversation with yourself",
  "error": "bad_request",
  "timestamp": 1775479200000
}
```

---

## 5. Kafka

Kafka không dùng trực tiếp với frontend, nhưng frontend nên hiểu để biết vì sao có các event realtime phụ trợ.

## Topic cấu hình hiện tại

- `chat-message-events`
- `chat-read-events`
- `chat-notification-events`
- `chat-dead-letter`

## Event được publish

### `MESSAGE_CREATED`

Publish sau khi lưu message thành công.

Payload Kafka:

```json
{
  "eventId": "9af95b79-df19-453d-bfc7-61259caab001",
  "eventType": "MESSAGE_CREATED",
  "originInstanceId": "instance-1",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "messageId": "67ef2d129ab123456789abce",
  "senderId": "userA",
  "messageType": "TEXT",
  "contentPreview": "Hello from frontend",
  "recipientIds": ["userA", "userB"],
  "createdAt": "2026-04-06T10:00:00Z"
}
```

### `MESSAGE_READ`

Publish sau khi mark as read.

```json
{
  "eventId": "28f39ef0-fd1e-43f8-8e91-d565c71cf001",
  "eventType": "MESSAGE_READ",
  "originInstanceId": "instance-1",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "messageId": "67ef2d129ab123456789abce",
  "userId": "userB",
  "recipientIds": ["userA", "userB"],
  "createdAt": "2026-04-06T10:05:00Z"
}
```

### `MESSAGE_RECALLED`

Publish sau khi recall message.

### `CONVERSATION_CREATED`

Publish khi direct conversation mới được tạo.

### `CHAT_MESSAGE_CREATED`

Đây là notification event cho mỗi recipient, dùng cho notification/audit.

Payload:

```json
{
  "eventId": "9af95b79-df19-453d-bfc7-61259caab001_userB",
  "eventType": "CHAT_MESSAGE_CREATED",
  "conversationId": "67ef2b4d9ab123456789abcd",
  "messageId": "67ef2d129ab123456789abce",
  "senderId": "userA",
  "senderDisplayName": "userA",
  "recipientId": "userB",
  "recipientOnline": true,
  "messageType": "TEXT",
  "contentPreview": "Hello from frontend",
  "createdAt": "2026-04-06T10:00:00Z"
}
```

## FE cần quan tâm gì từ Kafka

- FE không subscribe Kafka trực tiếp
- sự tồn tại của Kafka giải thích vì sao cùng một action có thể sinh thêm event trên `/user/queue/events`
- khi app scale nhiều instance, Kafka giúp event realtime đi đúng tới client đang nối vào instance khác

---

## 6. Frontend Usage

## 6.1. Flow chuẩn sau login

1. User login và lấy `accessToken`
2. Frontend connect STOMP tới `ws://localhost:8080/ws/chat`
3. Trong frame `CONNECT`, gửi header `Authorization: Bearer <accessToken>`
4. Subscribe:
   - `/user/queue/messages`
   - `/user/queue/events`
   - `/user/queue/errors`
5. Khi mở màn chat với ai đó, gọi REST tạo hoặc lấy direct conversation
6. Lấy message history qua REST
7. Gửi/nhận message realtime qua STOMP

## 6.2. Subscribe khuyến nghị

Khuyến nghị tối thiểu:

```text
/user/queue/messages
/user/queue/events
/user/queue/errors
```

Chỉ subscribe thêm topic conversation nếu UI thật sự cần broadcast theo room:

```text
/topic/conversations/{conversationId}
```

## 6.3. Ví dụ code kết nối STOMP

Ví dụ với `@stomp/stompjs`:

```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws/chat',
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`
  },
  reconnectDelay: 5000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  debug: (str) => console.log('[stomp]', str)
});

client.onConnect = () => {
  client.subscribe('/user/queue/messages', (frame) => {
    const message = JSON.parse(frame.body);
    console.log('message', message);
  });

  client.subscribe('/user/queue/events', (frame) => {
    const event = JSON.parse(frame.body);
    console.log('event', event);
  });

  client.subscribe('/user/queue/errors', (frame) => {
    const error = JSON.parse(frame.body);
    console.error('ws error', error);
  });
};

client.activate();
```

## 6.4. Ví dụ gửi message

```js
client.publish({
  destination: '/app/chat.send',
  body: JSON.stringify({
    conversationId: '67ef2b4d9ab123456789abcd',
    clientMessageId: crypto.randomUUID(),
    messageType: 'TEXT',
    content: 'Hello from frontend',
    attachments: []
  })
});
```

## 6.5. Ví dụ typing indicator

```js
client.publish({
  destination: '/app/chat.typing',
  body: JSON.stringify({
    conversationId: '67ef2b4d9ab123456789abcd',
    typing: true
  })
});
```

Khi user dừng gõ:

```js
client.publish({
  destination: '/app/chat.typing',
  body: JSON.stringify({
    conversationId: '67ef2b4d9ab123456789abcd',
    typing: false
  })
});
```

## 6.6. Ví dụ mark as read qua WebSocket

```js
client.publish({
  destination: '/app/chat.read',
  body: JSON.stringify({
    conversationId: '67ef2b4d9ab123456789abcd',
    messageId: '67ef2d129ab123456789abce'
  })
});
```

Hoặc gọi REST:

```js
await fetch('http://localhost:8080/api/chat/conversations/67ef2b4d9ab123456789abcd/read', {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    conversationId: '67ef2b4d9ab123456789abcd',
    messageId: '67ef2d129ab123456789abce'
  })
});
```

## 6.7. Handle reconnect

Khuyến nghị frontend:

1. dùng `reconnectDelay` để STOMP tự reconnect
2. sau mỗi lần reconnect, subscribe lại toàn bộ kênh
3. sau reconnect, gọi lại REST `GET /api/chat/conversations` và message history của conversation đang mở để đồng bộ state
4. clear typing state tạm trên UI nếu socket bị ngắt

### Vì sao nên resync bằng REST sau reconnect

- typing event là transient
- topic/event có thể bị lỡ trong lúc client offline
- unread state và recall nên được đồng bộ lại từ server source of truth

## 6.8. Mapping state khuyến nghị cho FE

### Nên dùng `MessageResponse` làm source of truth cho message item

UI message bubble nên map trực tiếp từ:

- `id`
- `conversationId`
- `senderId`
- `seqNo`
- `messageType`
- `content`
- `attachments`
- `status`
- `deletedForEveryone`
- `createdAt`

### Nên dùng `/user/queue/events` cho state phụ trợ

- `MESSAGE_READ` -> cập nhật read receipt
- `TYPING` -> hiện `typing...`
- `MESSAGE_RECALLED` -> thay đổi trạng thái message đã recall

### Không nên phụ thuộc hoàn toàn vào `/topic/conversations/{conversationId}`

Nguyên nhân là payload hiện chưa ổn định tuyệt đối giữa các luồng runtime.

---

## 7. Những điểm FE cần biết trước khi tích hợp

1. WebSocket auth nằm ở STOMP `CONNECT` header, không phải query param.
2. Gateway route `/ws/chat/**` là public ở lớp HTTP, nhưng chat-service vẫn bắt JWT trong frame `CONNECT`.
3. REST hiện không trả `unreadCount` trong `ConversationResponse` (`GET /api/chat/conversations`). Muốn lấy unread per member phải gọi `GET /api/chat/conversations/{conversationId}/members`.
4. `clientMessageId` đã có trong request nhưng backend chưa dùng để chống duplicate optimistic send.
5. Recall message chỉ hợp lệ trong 15 phút từ lúc tạo message.
6. Presence hiện là in-memory với TTL **3 phút** (online), chưa phải distributed presence.
7. Typing state là in-memory TTL **10 giây** per user per conversation, chỉ mang tính tạm thời.
8. Group conversation (`type: GROUP`) chưa được expose qua REST tạo mới; hiện chỉ hỗ trợ `DIRECT` qua `/api/chat/conversations/direct/{targetUserId}`.

---

## 8. Contract FE nên dùng ngay

Nếu chỉ lấy phần tối thiểu để frontend tích hợp nhanh, dùng bộ contract này:

### REST

- `POST /api/chat/conversations/direct/{targetUserId}`
- `GET /api/chat/conversations`
- `GET /api/chat/conversations/{conversationId}/messages?cursor=&limit=`
- `GET /api/chat/conversations/{conversationId}/members`
- `PUT /api/chat/conversations/{conversationId}/read`
- `PUT /api/chat/messages/{messageId}/recall`

### STOMP connect

- URL: `ws://localhost:8080/ws/chat`
- CONNECT header: `Authorization: Bearer <accessToken>`

### STOMP publish

- `/app/chat.send`
- `/app/chat.read`
- `/app/chat.typing`

### STOMP subscribe

- `/user/queue/messages`
- `/user/queue/events`
- `/user/queue/errors`
