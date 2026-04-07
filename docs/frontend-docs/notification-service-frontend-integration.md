# Notification Service Frontend Integration

## 1. Tổng quan

`social-media-notification-service` chịu trách nhiệm:

- lưu notification từ chat-service và social-service
- cung cấp REST API để list notification, unread count, mark as read
- phát realtime notification qua SSE cho frontend đang online

Notification-service hiện không có WebSocket riêng. Realtime hiện dùng:

- `Server-Sent Events (SSE)`

## Base URL

Khuyến nghị frontend gọi qua gateway:

- `http://localhost:8080/api/notifications`

Nếu gọi trực tiếp vào service:

- `http://localhost:8086/api/notifications`

## Authentication

Frontend nên gửi:

```http
Authorization: Bearer <accessToken>
```

Gateway sẽ validate JWT và inject `X-User-Id` xuống notification-service.

Nếu test trực tiếp service local thì cần tự gửi `X-User-Id`.

---

## 2. Danh sách endpoint

| Method | URL | Mô tả |
| --- | --- | --- |
| GET | `/api/notifications` | Lấy danh sách notifications có cursor |
| GET | `/api/notifications/unread-count` | Lấy số lượng chưa đọc |
| PUT | `/api/notifications/{notificationId}/read` | Đánh dấu một notification đã đọc |
| GET | `/api/notifications/stream` | Kết nối SSE realtime |

---

## 3. SSE realtime

## 3.1. Endpoint

- `GET /api/notifications/stream`

Ví dụ qua gateway:

```http
GET http://localhost:8080/api/notifications/stream
Authorization: Bearer <accessToken>
```

## 3.2. Cách hoạt động

Khi frontend mở SSE stream:

1. gateway xác thực JWT
2. gateway inject `X-User-Id`
3. notification-service tạo `SseEmitter`
4. backend gửi event đầu tiên `stream.ready`
5. khi có notification mới hoặc mark read, backend broadcast thêm event tương ứng

## 3.3. Event name hiện tại

Theo code hiện tại, frontend cần xử lý các event SSE sau:

- `stream.ready`
- `notification.created`
- `notification.read`

## 3.4. Event đầu tiên khi connect

```text
event: stream.ready
data: connected
```

## 3.5. Event khi có notification mới

```text
event: notification.created
data: {"id":"67f0abcd1234567890abcd01","eventType":"CHAT_MESSAGE_CREATED","category":"CHAT","title":"Tin nhan moi","contentPreview":"Hello from frontend","actor":{"id":"userA","displayName":"userA"},"deeplink":"/chat/67f0cdef1234567890abcd02","resourceType":"CONVERSATION","resourceId":"67f0cdef1234567890abcd02","read":false,"readAt":null,"createdAt":"2026-04-06T10:00:00Z"}
```

## 3.6. Event khi mark read

```text
event: notification.read
data: {"id":"67f0abcd1234567890abcd01","read":true,"readAt":"2026-04-06T10:05:00Z"}
```

## 3.7. Lưu ý quan trọng cho frontend

1. SSE stream hiện không có heartbeat `stream.ping` trong code hiện tại.
2. Backend dùng `SseEmitter` timeout bằng `0`, tức là stream có thể giữ mở vô thời hạn cho tới khi client/server/network đóng kết nối.
3. Frontend nên tự implement reconnect khi connection lỗi hoặc bị ngắt.

---

## 4. REST API

## 4.1. Lấy danh sách notifications

### Method + URL

- `GET /api/notifications`

### Query params

- `cursor`: optional
- `limit`: optional, mặc định `20`, tối đa `100`
- `read`: optional, `true` hoặc `false`
- `category`: optional, ví dụ `CHAT` hoặc `SOCIAL`

### Ví dụ request

```http
GET http://localhost:8080/api/notifications?limit=20&read=false&category=CHAT
Authorization: Bearer <accessToken>
```

### Response

```json
{
  "items": [
    {
      "id": "67f0abcd1234567890abcd01",
      "eventType": "CHAT_MESSAGE_CREATED",
      "category": "CHAT",
      "title": "Tin nhan moi",
      "contentPreview": "Hello from frontend",
      "actor": {
        "id": "userA",
        "displayName": "userA"
      },
      "deeplink": "/chat/67f0cdef1234567890abcd02",
      "resourceType": "CONVERSATION",
      "resourceId": "67f0cdef1234567890abcd02",
      "read": false,
      "readAt": null,
      "createdAt": "2026-04-06T10:00:00Z"
    }
  ],
  "nextCursor": "MjAyNi0wNC0wNlQxMDowMDowMFp8NjdmMGFiY2QxMjM0NTY3ODkwYWJjZDAx"
}
```

## Cursor pagination

Notification-service dùng cursor dựa trên:

- `createdAt`
- `id`

Frontend không cần tự sinh cursor. Chỉ cần:

1. gọi API lần đầu không có cursor
2. lấy `nextCursor` từ response
3. gọi tiếp `GET /api/notifications?cursor=<nextCursor>&limit=20`

Nếu `nextCursor = null`, nghĩa là không còn trang tiếp theo.

## 4.2. Lấy unread count

### Method + URL

- `GET /api/notifications/unread-count`

### Response

```json
{
  "unreadCount": 3
}
```

Frontend nên gọi API này:

- lúc app load
- sau reconnect SSE
- khi muốn sync badge chính xác từ server

## 4.3. Mark as read

### Method + URL

- `PUT /api/notifications/{notificationId}/read`

### Response

```json
{
  "id": "67f0abcd1234567890abcd01",
  "read": true,
  "readAt": "2026-04-06T10:05:00Z"
}
```

### Behavior thực tế

- nếu notification chưa đọc, backend update `read=true` và `readAt`
- nếu đã đọc rồi, backend vẫn trả response hiện tại nhưng không phát SSE `notification.read` thêm lần nữa

---

## 5. Data model FE cần dùng

## 5.1. NotificationResponse

```json
{
  "id": "67f0abcd1234567890abcd01",
  "eventType": "CHAT_MESSAGE_CREATED",
  "category": "CHAT",
  "title": "Tin nhan moi",
  "contentPreview": "Hello from frontend",
  "actor": {
    "id": "userA",
    "displayName": "userA"
  },
  "deeplink": "/chat/67f0cdef1234567890abcd02",
  "resourceType": "CONVERSATION",
  "resourceId": "67f0cdef1234567890abcd02",
  "read": false,
  "readAt": null,
  "createdAt": "2026-04-06T10:00:00Z"
}
```

Field quan trọng cho FE:

- `id`: notification id
- `eventType`: loại event gốc
- `category`: `CHAT` hoặc `SOCIAL`
- `title`: text hiển thị chính
- `contentPreview`: preview nội dung
- `actor.id`: user gây ra action
- `actor.displayName`: text hiển thị actor
- `deeplink`: đường dẫn frontend nên mở khi user click
- `resourceType`: ví dụ `CONVERSATION`, `POST`, `COMMENT`, `USER`
- `resourceId`: id resource liên quan
- `read`: trạng thái đã đọc
- `createdAt`: thời gian tạo notification

## 5.2. NotificationListResponse

```json
{
  "items": [],
  "nextCursor": "..."
}
```

## 5.3. NotificationReadResponse

```json
{
  "id": "67f0abcd1234567890abcd01",
  "read": true,
  "readAt": "2026-04-06T10:05:00Z"
}
```

## 5.4. UnreadCountResponse

```json
{
  "unreadCount": 3
}
```

---

## 6. Event type và mapping hiện tại

Frontend có thể dựa vào `eventType` và `category` để render icon, deeplink hoặc nhóm notification.

## Chat notification hiện có

- `CHAT_MESSAGE_CREATED`

Mapping hiện tại:

- `category = CHAT`
- `resourceType = CONVERSATION`
- `deeplink` mặc định: `/chat/{conversationId}`

## Social notification hiện có

- `SOCIAL_POST_REACTION_CREATED`
- `SOCIAL_COMMENT_CREATED`
- `SOCIAL_COMMENT_REACTION_CREATED`
- `SOCIAL_POST_CREATED`
- `SOCIAL_FRIEND_REQUEST_CREATED`
- `SOCIAL_FRIEND_REQUEST_ACCEPTED`

Mapping hiện tại:

- post-related -> `resourceType = POST`
- comment-related -> `resourceType = COMMENT`
- friend-related -> `resourceType = USER`

Ví dụ deeplink social:

- post/comment -> `/posts/{postId}` nếu event không truyền deeplink riêng
- friend request -> `/friends/requests`
- friend accepted -> `/friends`

---

## 7. Error format

Notification-service dùng format lỗi JSON khá ổn định.

## Bad request

```json
{
  "status": 400,
  "message": "limit must be greater than 0",
  "error": "bad_request",
  "timestamp": 1775479200000
}
```

## Not found

```json
{
  "status": 404,
  "message": "Notification not found",
  "error": "not_found",
  "timestamp": 1775479200000
}
```

## Internal error

```json
{
  "status": 500,
  "message": "Internal server error",
  "error": "internal_error",
  "timestamp": 1775479200000
}
```

---

## 8. Frontend usage

## 8.1. Flow chuẩn sau login

1. User login và có `accessToken`
2. Frontend gọi `GET /api/notifications/unread-count` để render badge ban đầu
3. Frontend gọi `GET /api/notifications?limit=20` để load list ban đầu
4. Frontend mở SSE stream `GET /api/notifications/stream`
5. Khi nhận `notification.created`, prepend item vào list và tăng badge
6. Khi user mở notification, gọi `PUT /api/notifications/{id}/read`
7. Khi nhận `notification.read`, sync lại item tương ứng trong UI

## 8.2. Ví dụ dùng `EventSource`

Vì `EventSource` chuẩn không cho custom header trong browser, có một điểm thực tế cần lưu ý:

- nếu hệ thống của bạn bắt buộc JWT ở header `Authorization`, browser `EventSource` thuần sẽ không gửi được header này

Với codebase hiện tại, khi đi qua gateway thì notification stream cần auth qua JWT, nên frontend web thường sẽ phải dùng một trong các cách sau:

1. dùng thư viện/fetch-based SSE hỗ trợ custom header
2. chuyển gateway/auth sang cookie-based auth cho SSE
3. nếu chỉ test local trực tiếp service, có thể gọi không qua gateway và tự set `X-User-Id` bằng công cụ test, nhưng đây không phải flow production

Ví dụ với thư viện hỗ trợ fetch SSE:

```js
import { fetchEventSource } from '@microsoft/fetch-event-source';

await fetchEventSource('http://localhost:8080/api/notifications/stream', {
  headers: {
    Authorization: `Bearer ${accessToken}`
  },
  onopen(response) {
    if (!response.ok) {
      throw new Error('Failed to open notification stream');
    }
  },
  onmessage(event) {
    if (event.event === 'stream.ready') {
      console.log('notification stream ready');
      return;
    }

    if (event.event === 'notification.created') {
      const payload = JSON.parse(event.data);
      console.log('new notification', payload);
      return;
    }

    if (event.event === 'notification.read') {
      const payload = JSON.parse(event.data);
      console.log('notification read', payload);
    }
  },
  onerror(err) {
    console.error('notification stream error', err);
    throw err;
  }
});
```

## 8.3. Handle reconnect

Khuyến nghị frontend:

1. tự reconnect khi SSE bị ngắt
2. sau reconnect, gọi lại `GET /api/notifications/unread-count`
3. nếu màn notification đang mở, gọi lại `GET /api/notifications?limit=20`
4. không chỉ dựa hoàn toàn vào event realtime để giữ state đúng tuyệt đối

Lý do:

- network có thể drop event trong lúc client offline
- SSE stream hiện không có heartbeat riêng trong code hiện tại
- unread badge nên được sync lại từ source of truth là REST API

## 8.4. Ví dụ mark as read

```js
async function markNotificationRead(notificationId, accessToken) {
  const response = await fetch(`http://localhost:8080/api/notifications/${notificationId}/read`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to mark as read');
  }

  return response.json();
}
```

## 8.5. Ví dụ load notifications với cursor

```js
async function getNotifications(accessToken, cursor) {
  const params = new URLSearchParams({ limit: '20' });
  if (cursor) params.set('cursor', cursor);

  const response = await fetch(`http://localhost:8080/api/notifications?${params.toString()}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to load notifications');
  }

  return response.json();
}
```

---

## 9. Những điểm FE cần biết trước

1. Realtime của notification-service là SSE, không phải WebSocket.
2. SSE event name hiện có chỉ là `stream.ready`, `notification.created`, `notification.read`.
3. Theo code hiện tại, tôi không thấy heartbeat `stream.ping` trong service này.
4. `GET /api/notifications` dùng cursor pagination, không dùng page number.
5. `category` hiện có ý nghĩa thực tế là `CHAT` hoặc `SOCIAL`.
6. Browser `EventSource` chuẩn không gửi custom `Authorization` header, nên frontend web nên dùng thư viện SSE hỗ trợ fetch/header hoặc đổi chiến lược auth cho SSE.

---

## 10. Contract FE nên dùng ngay

Nếu chỉ lấy phần tối thiểu để tích hợp nhanh:

### REST

- `GET /api/notifications?limit=20`
- `GET /api/notifications?cursor=<nextCursor>&limit=20`
- `GET /api/notifications/unread-count`
- `PUT /api/notifications/{notificationId}/read`

### SSE

- `GET /api/notifications/stream`

### SSE event names

- `stream.ready`
- `notification.created`
- `notification.read`
