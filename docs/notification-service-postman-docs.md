# Notification Service Postman Guide

## Muc tieu

Tai lieu nay dung de test `social-media-notification-service` qua Postman.

Phan REST co the test truc tiep.
Phan realtime dung `SSE` qua endpoint `GET /api/notifications/stream`.

Mac dinh test qua `api-gateway`:

- REST + SSE: `http://localhost:8080`

Neu test truc tiep vao notification-service thi dung:

- REST + SSE: `http://localhost:8086`

## Endpoint hien co

- `GET /api/notifications`
- `GET /api/notifications/unread-count`
- `PUT /api/notifications/{notificationId}/read`
- `GET /api/notifications/stream`

## Bien mau de dung trong Postman

Tao environment hoac collection variables:

```text
baseUrl=http://localhost:8080
notificationServiceUrl=http://localhost:8086
wsUrl=ws://localhost:8080/ws/chat
senderUserId=userA
recipientUserId=userB
conversationId=
notificationId=
cursor=
limit=20
category=
accessToken=
```

## Thu tu test khuyen nghi

1. Mo stream SSE cho `recipientUserId`
2. Tao notification mau
3. Kiem tra `unread-count`
4. Lay danh sach notifications
5. Luu `notificationId`
6. Mark notification da doc
7. Kiem tra lai `unread-count`, list va SSE event

## Kich ban 2 user de test nhanh

Day la kich ban de tranh nham lan giua nguoi gui va nguoi nhan.

Quy uoc:

- `senderUserId = userA`
- `recipientUserId = userB`
- tab SSE phai mo bang token cua `userB`
- request gui tin nhan phai dung token cua `userA`

### Tab 1 - SSE cua nguoi nhan `userB`

Mo request:

```http
GET {{baseUrl}}/api/notifications/stream
```

Headers:

```http
Authorization: Bearer <token_userB>
X-User-Id: userB
```

Ky vong:

- thay `stream.ready`
- giu tab nay mo, khong dong request

### Tab 2 - Tao conversation bang `userA`

Request:

```http
POST {{baseUrl}}/api/chat/conversations/direct/userB
```

Headers:

```http
Authorization: Bearer <token_userA>
X-User-Id: userA
```

Luu `conversationId` tra ve.

### Tab 3 - Gui tin nhan bang `userA`

Mo Postman WebSocket den:

```text
ws://localhost:8080/ws/chat
```

Gui `CONNECT` frame voi token cua `userA`:

```text
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer <token_userA>

\u0000
```

Gui `SEND` frame:

```text
SEND
destination:/app/chat.send
content-type:application/json

{"conversationId":"<conversationId>","clientMessageId":"cmsg_notification_001","messageType":"TEXT","content":"Hello userB","attachments":[]}
\u0000
```

Ky vong:

- tab SSE cua `userB` nhan `notification.created`
- `GET /api/notifications/unread-count` voi `userB` tra ve `1`

### Tab 4 - Kiem tra list bang `userB`

Request:

```http
GET {{baseUrl}}/api/notifications?limit=20
```

Headers:

```http
Authorization: Bearer <token_userB>
X-User-Id: userB
```

Lay `notificationId` tu item dau tien.

### Tab 5 - Mark read bang `userB`

Request:

```http
PUT {{baseUrl}}/api/notifications/<notificationId>/read
```

Headers:

```http
Authorization: Bearer <token_userB>
X-User-Id: userB
```

Ky vong:

- tab SSE cua `userB` nhan `notification.read`
- `GET /api/notifications/unread-count` voi `userB` tra ve `0`

### Neu ban mo SSE bang `userA`

Ban se chi thay `stream.ready` va khong thay `notification.created` khi `userA` gui tin nhan.

Ly do:

- notification chat duoc tao cho `recipientId`
- trong direct chat, `recipientId` la `userB`
- `userA` la sender nen khong nhan chat notification cua chinh minh

## Cach tao notification mau

Hien tai luong chat la cach de test end-to-end ro rang nhat vi `notification-service` da consume `chat-notification-events`.

Luu y:

- `social-service` chua co producer day du cho `social-notification-events`, vi vay guide nay uu tien test bang chat notification.
- Neu trong MongoDB da co san notifications thi co the bo qua buoc tao mau va test truc tiep API list/read/stream.

## 1. Mo stream SSE

**Method**: `GET`

**URL**:

```http
{{baseUrl}}/api/notifications/stream
```

Neu test truc tiep vao service:

```http
{{notificationServiceUrl}}/api/notifications/stream
```

**Headers**:

```http
X-User-Id: {{recipientUserId}}
```

Neu gateway cua ban dang bat JWT auth thi them:

```http
Authorization: Bearer {{accessToken}}
```

**Body**: khong co

**Ket qua mong doi**:

Ket noi khong dong ngay va tra event dau tien:

```text
event: stream.ready
data: connected
```

Sau khi co notification moi, stream se co dang:

```text
event: notification.created
data: {"id":"67f0abcd1234567890abcd01","eventType":"CHAT_MESSAGE_CREATED","category":"CHAT","title":"Tin nhan moi","contentPreview":"Hello from Postman WS","actor":{"id":"userA","displayName":"userA"},"deeplink":"/chat/67f0cdef1234567890abcd02","resourceType":"CONVERSATION","resourceId":"67f0cdef1234567890abcd02","read":false,"readAt":null,"createdAt":"2026-04-05T10:00:00Z"}
```

Sau khi mark read thanh cong, stream se co dang:

```text
event: notification.read
data: {"id":"67f0abcd1234567890abcd01","read":true,"readAt":"2026-04-05T10:05:00Z"}
```

**Luu y khi test trong Postman**:

- Postman se giu request dang mo de hien streaming response.
- Hay mo request SSE trong tab rieng va giu no chay trong suot qua trinh test.
- Neu Postman cua ban khong hien streaming tot, co the dung browser `EventSource`, `curl`, hoac page test rieng cho frontend.

## 2. Tao notification mau bang chat-service

### 2.1. Tao hoac lay direct conversation

**Method**: `POST`

**URL**:

```http
{{baseUrl}}/api/chat/conversations/direct/{{recipientUserId}}
```

**Headers**:

```http
X-User-Id: {{senderUserId}}
```

Neu gateway cua ban dang bat JWT auth thi them:

```http
Authorization: Bearer {{accessToken}}
```

**Body**: khong co

**Response mong doi**:

```json
{
  "conversationId": "67f0cdef1234567890abcd02",
  "type": "DIRECT",
  "participantIds": ["userA", "userB"],
  "lastMessageId": null,
  "lastMessagePreview": null,
  "lastMessageSenderId": null,
  "lastMessageAt": null,
  "createdAt": "2026-04-05T10:00:00Z"
}
```

**Test script goi y**:

```javascript
pm.test('Status code is 200', function () {
  pm.response.to.have.status(200);
});

const json = pm.response.json();
if (json.conversationId) {
  pm.environment.set('conversationId', json.conversationId);
}
```

### 2.2. Gui message qua Postman WebSocket

Tao WebSocket request trong Postman:

```text
{{wsUrl}}
```

Neu test truc tiep vao chat-service:

```text
ws://localhost:8085/ws/chat
```

Gui frame `CONNECT`:

```text
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer {{accessToken}}

\u0000
```

Sau khi nhan `CONNECTED`, gui frame `SEND`:

```text
SEND
destination:/app/chat.send
content-type:application/json

{"conversationId":"{{conversationId}}","clientMessageId":"cmsg_notification_001","messageType":"TEXT","content":"Hello from Postman WS","attachments":[]}
\u0000
```

**Ket qua mong doi**:

- `chat-service` persist message
- `chat-service` publish `chat-notification-events`
- `notification-service` consume event va luu notification cho `recipientUserId`
- tab SSE o buoc 1 nhan `notification.created`

Neu can them chi tiet ve STOMP frame, xem them file:

- `docs/chat-service-postman-docs.md`

## 3. Kiem tra unread count

**Method**: `GET`

**URL**:

```http
{{baseUrl}}/api/notifications/unread-count
```

**Headers**:

```http
X-User-Id: {{recipientUserId}}
```

Neu gateway cua ban dang bat JWT auth thi them:

```http
Authorization: Bearer {{accessToken}}
```

**Body**: khong co

**Response mong doi**:

```json
{
  "unreadCount": 1
}
```

## 4. Lay danh sach notifications

**Method**: `GET`

**URL**:

```http
{{baseUrl}}/api/notifications?limit={{limit}}
```

Neu can loc theo read:

```http
{{baseUrl}}/api/notifications?limit={{limit}}&read=false
```

Neu can loc theo category:

```http
{{baseUrl}}/api/notifications?limit={{limit}}&category=CHAT
```

Neu da co `cursor`:

```http
{{baseUrl}}/api/notifications?limit={{limit}}&cursor={{cursor}}
```

**Headers**:

```http
X-User-Id: {{recipientUserId}}
```

Neu gateway cua ban dang bat JWT auth thi them:

```http
Authorization: Bearer {{accessToken}}
```

**Body**: khong co

**Response mong doi**:

```json
{
  "items": [
    {
      "id": "67f0abcd1234567890abcd01",
      "eventType": "CHAT_MESSAGE_CREATED",
      "category": "CHAT",
      "title": "Tin nhan moi",
      "contentPreview": "Hello from Postman WS",
      "actor": {
        "id": "userA",
        "displayName": "userA"
      },
      "deeplink": "/chat/67f0cdef1234567890abcd02",
      "resourceType": "CONVERSATION",
      "resourceId": "67f0cdef1234567890abcd02",
      "read": false,
      "readAt": null,
      "createdAt": "2026-04-05T10:00:00Z"
    }
  ],
  "nextCursor": null
}
```

**Test script goi y**:

```javascript
pm.test('Status code is 200', function () {
  pm.response.to.have.status(200);
});

const json = pm.response.json();
if (json.items && json.items.length > 0 && json.items[0].id) {
  pm.environment.set('notificationId', json.items[0].id);
}
if (json.nextCursor) {
  pm.environment.set('cursor', json.nextCursor);
}
```

## 5. Mark mot notification la da doc

**Method**: `PUT`

**URL**:

```http
{{baseUrl}}/api/notifications/{{notificationId}}/read
```

**Headers**:

```http
X-User-Id: {{recipientUserId}}
```

Neu gateway cua ban dang bat JWT auth thi them:

```http
Authorization: Bearer {{accessToken}}
```

**Body**: khong co

**Response mong doi**:

```json
{
  "id": "67f0abcd1234567890abcd01",
  "read": true,
  "readAt": "2026-04-05T10:05:00Z"
}
```

**Ket qua phu mong doi**:

- tab SSE nhan event `notification.read`
- `GET /api/notifications/unread-count` giam xuong
- `GET /api/notifications?read=true` tra ve notification vua duoc update

## 6. Kiem tra lai unread count sau khi mark read

**Method**: `GET`

**URL**:

```http
{{baseUrl}}/api/notifications/unread-count
```

**Headers**:

```http
X-User-Id: {{recipientUserId}}
```

**Response mong doi**:

```json
{
  "unreadCount": 0
}
```

## 7. Kiem tra list voi read=true

**Method**: `GET`

**URL**:

```http
{{baseUrl}}/api/notifications?read=true&limit={{limit}}
```

**Headers**:

```http
X-User-Id: {{recipientUserId}}
```

**Response mong doi**:

Phan tu vua mark read xuat hien voi:

```json
{
  "id": "67f0abcd1234567890abcd01",
  "read": true,
  "readAt": "2026-04-05T10:05:00Z"
}
```

## 8. Test duplicate event

Muc tieu cua buoc nay la xac nhan idempotent theo `sourceEventId`.

Voi Postman thuong khong de tao lai cung mot Kafka event y chang neu di qua chat-service, vi producer chat dang tao `eventId` moi moi lan gui message.

Do do:

- Duplicate event nen duoc verify bang integration test hoac bang producer Kafka rieng.
- Voi Postman end-to-end, ban co the gui 2 tin nhan khac nhau va xac nhan nhan 2 notifications khac nhau.

## Loi thuong gap

### Stream SSE khong nhan duoc event moi

Nguyen nhan thuong gap:

- tab SSE dong qua som
- gui message nhung nguoi nhan khong dung `recipientUserId`
- `chat-service` khong publish `chat-notification-events`
- `notification-service` khong ket noi duoc Kafka

### `GET /api/notifications` tra ve rong

Nguyen nhan thuong gap:

- chua tao notification mau thanh cong
- dang query bang sai `X-User-Id`
- dang loc `category` hoac `read` khong khop

### `PUT /read` tra `404 Notification not found`

Nguyen nhan thuong gap:

- `notificationId` khong ton tai
- notification thuoc user khac
- environment variable `notificationId` chua duoc cap nhat

### Chat gui thanh cong nhung notification khong duoc tao

Kiem tra:

- `chat-service` da chay chua
- `notification-service` da chay chua
- Kafka broker da chay chua
- topic `chat-notification-events` da ton tai chua

## Luong test nhanh nhat

1. Mo `GET /api/notifications/stream` voi `X-User-Id: {{recipientUserId}}`
2. Goi `POST /api/chat/conversations/direct/{{recipientUserId}}` voi `X-User-Id: {{senderUserId}}`
3. Gui `SEND /app/chat.send` qua Postman WebSocket
4. Kiem tra tab SSE nhan `notification.created`
5. Goi `GET /api/notifications/unread-count`
6. Goi `GET /api/notifications`
7. Luu `notificationId`
8. Goi `PUT /api/notifications/{{notificationId}}/read`
9. Kiem tra tab SSE nhan `notification.read`
10. Goi lai `GET /api/notifications/unread-count`

## Ghi chu thuc te hien tai

- Luong test bang chat-service dang kha dung nhat cho phase hien tai.
- Luong social notification se de test hon sau khi `social-service` publish day du `social-notification-events`.
- `notification-service` dang su dung `X-User-Id` de xac dinh recipient khi query va stream.
- Neu test qua gateway trong moi truong co auth that, hay them `Authorization: Bearer {{accessToken}}`.