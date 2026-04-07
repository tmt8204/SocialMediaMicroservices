# Chat Service Postman Guide

## Muc tieu

Tai lieu nay dung de test `social-media-chat-service` qua Postman.

Phan REST se test duoc truc tiep.
Phan WebSocket/STOMP duoc mo ta theo tung buoc de test bang Postman WebSocket request.

Mac dinh test qua `api-gateway`:

- REST: `http://localhost:8080`
- WebSocket: `ws://localhost:8080/ws/chat`

Neu test truc tiep vao chat-service thi dung:

- REST: `http://localhost:8085`
- WebSocket: `ws://localhost:8085/ws/chat`

## Bien mau de dung trong Postman

Tao environment hoac collection variables:

```text
baseUrl=http://localhost:8080
wsUrl=ws://localhost:8080/ws/chat
userId=userA
targetUserId=userB
conversationId=
messageId=
cursor=
limit=20
accessToken=
```

## Thu tu test khuyen nghi

1. Tao hoac lay direct conversation
2. Lay danh sach conversation
3. Gui message qua WebSocket
4. Lay message history de lay `messageId`
5. Mark as read
6. Recall message
7. Test typing event qua WebSocket

## 1. Tao hoac lay direct conversation

**Method**: `POST`

**URL**:

```http
{{baseUrl}}/api/chat/conversations/direct/{{targetUserId}}
```

**Headers**:

```http
X-User-Id: {{userId}}
```

**Body**: khong co

**Response mong doi**:

```json
{
  "conversationId": "67ef2b4d9ab123456789abcd",
  "type": "DIRECT",
  "participantIds": ["userA", "userB"],
  "lastMessageId": null,
  "lastMessagePreview": null,
  "lastMessageSenderId": null,
  "lastMessageAt": null,
  "createdAt": "2026-04-04T10:00:00Z"
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

## 2. Lay danh sach conversation

**Method**: `GET`

**URL**:

```http
{{baseUrl}}/api/chat/conversations
```

**Headers**:

```http
X-User-Id: {{userId}}
```

**Body**: khong co

## 3. Lay lich su message

**Method**: `GET`

**URL**:

```http
{{baseUrl}}/api/chat/conversations/{{conversationId}}/messages?limit={{limit}}
```

Neu da co cursor:

```http
{{baseUrl}}/api/chat/conversations/{{conversationId}}/messages?cursor={{cursor}}&limit={{limit}}
```

**Headers**:

```http
X-User-Id: {{userId}}
```

**Response mong doi**:

```json
{
  "messages": [
    {
      "id": "67ef2d129ab123456789abce",
      "conversationId": "67ef2b4d9ab123456789abcd",
      "senderId": "userA",
      "seqNo": 1,
      "messageType": "TEXT",
      "content": "Hello from websocket",
      "attachments": [],
      "replyToMessageId": null,
      "status": "SENT",
      "edited": false,
      "deletedForEveryone": false,
      "createdAt": "2026-04-04T10:05:00Z",
      "updatedAt": "2026-04-04T10:05:00Z"
    }
  ],
  "nextCursor": 1,
  "hasMore": false,
  "totalCount": 1
}
```

**Test script goi y**:

```javascript
pm.test('Status code is 200', function () {
  pm.response.to.have.status(200);
});

const json = pm.response.json();
if (json.nextCursor !== undefined && json.nextCursor !== null) {
  pm.environment.set('cursor', String(json.nextCursor));
}
if (json.messages && json.messages.length > 0 && json.messages[0].id) {
  pm.environment.set('messageId', json.messages[0].id);
}
```

## 4. Danh dau da doc

**Method**: `PUT`

**URL**:

```http
{{baseUrl}}/api/chat/conversations/{{conversationId}}/read
```

**Headers**:

```http
X-User-Id: {{userId}}
Content-Type: application/json
```

**Body**:

```json
{
  "conversationId": "{{conversationId}}",
  "messageId": "{{messageId}}"
}
```

**Response mong doi**:

```http
204 No Content
```

## 5. Recall message

**Method**: `PUT`

**URL**:

```http
{{baseUrl}}/api/chat/messages/{{messageId}}/recall
```

**Headers**:

```http
X-User-Id: {{userId}}
```

**Body**: khong co

**Response mong doi**:

```json
{
  "id": "{{messageId}}",
  "conversationId": "{{conversationId}}",
  "senderId": "userA",
  "seqNo": 1,
  "messageType": "SYSTEM",
  "content": "This message was recalled",
  "attachments": [],
  "replyToMessageId": null,
  "status": "SENT",
  "edited": false,
  "deletedForEveryone": true,
  "createdAt": "2026-04-04T10:05:00Z",
  "updatedAt": "2026-04-04T10:06:00Z"
}
```

## 6. Test WebSocket/STOMP trong Postman

### 6.1. Ket noi WebSocket

Tao WebSocket request trong Postman:

```text
{{wsUrl}}
```

Sau do gui `CONNECT` frame.

**Rule quan trong khi gui frame STOMP trong Postman**:

- Ky tu dau tien cua message phai la `CONNECT`, khong duoc co khoang trang, dong trong, BOM hoac ky tu an truoc no
- Moi header phai dung dung format `name:value`
- Phai co 1 dong trong giua header va body
- Frame phai ket thuc bang ky tu NULL o cuoi frame
- Khong duoc de ky tu NULL o dau frame, neu khong se gap loi `Illegal header: '\u0000CONNECT'`
- Neu Postman da giu lai noi dung cu, hay xoa toan bo khung message roi paste lai tu dau

**STOMP CONNECT frame**:

```text
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer {{accessToken}}

\u0000
```

**Dang sai thuong gap**:

```text
\u0000CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer {{accessToken}}

```

Dang nay sai vi ky tu NULL dang nam o dau frame, lam Spring parse `CONNECT` nhu mot header loi.

**Dang dung**:

```text
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer {{accessToken}}

\u0000
```

Luu y:

- token duoc validate tai `chat-service` trong `ChannelInterceptor`
- can la access token hop le
- browser thuong khong set duoc `Authorization` header trong raw WebSocket, nhung STOMP native header thi duoc
- neu Postman khong gui on dinh ky tu ket thuc NULL, uu tien dung client STOMP chuyen dung nhu `WebSocket King`, `Hoppscotch`, `webstomp-client` hoac frontend test page

### 6.2. Subscribe cac queue/topic

**Nhan message rieng theo user**:

```text
SUBSCRIBE
id:sub-user-messages
destination:/user/queue/messages

\u0000
```

**Nhan event read/typing theo user**:

```text
SUBSCRIBE
id:sub-user-events
destination:/user/queue/events

\u0000
```

**Nhan event theo conversation**:

```text
SUBSCRIBE
id:sub-conversation
destination:/topic/conversations/{{conversationId}}

\u0000
```

### 6.3. Gui message realtime

```text
SEND
destination:/app/chat.send
content-type:application/json

{"conversationId":"{{conversationId}}","clientMessageId":"cmsg_001","messageType":"TEXT","content":"Hello from Postman WS","attachments":[]}
\u0000
```

Ket qua mong doi:

- receiver nhan duoc o `/user/queue/messages`
- ca hai ben co the nhan o `/topic/conversations/{{conversationId}}`
- message duoc persist vao MongoDB

### 6.4. Gui read event

```text
SEND
destination:/app/chat.read
content-type:application/json

{"conversationId":"{{conversationId}}","messageId":"{{messageId}}"}
\u0000
```

Ket qua mong doi:

- peer nhan event `MESSAGE_READ` o `/user/queue/events`
- unreadCount cua member duoc reset

### 6.5. Gui typing event

```text
SEND
destination:/app/chat.typing
content-type:application/json

{"conversationId":"{{conversationId}}","typing":true}
\u0000
```

Ket qua mong doi:

- peer nhan event `TYPING` o `/user/queue/events`

## 7. Loi thuong gap

### Gui roi nhung khong thay `CONNECTED` hoac khong co message tra ve

Nguyen nhan pho bien nhat:

- Postman WebSocket dang gui raw text chu khong tao STOMP frame ket thuc bang byte NULL that
- chuoi `\u0000` ban nhap trong Postman thuong chi la 6 ky tu text, khong phai byte NULL
- vi frame chua duoc dong dung cach, Spring se khong tra `CONNECTED`

Cach xu ly khuyen nghi:

- khong dung Postman raw WebSocket de test STOMP neu no khong gui duoc NULL byte that
- dung file test browser nay thay the:

`docs/chat-stomp-test.html`

File nay su dung thu vien STOMP client dung chuan, tu dong tao CONNECT, SUBSCRIBE, SEND dung format.

Neu van muon dung Postman, ban can xac minh no gui byte NULL that o cuoi frame, khong phai chuoi text `\u0000`.

### 401 Unauthorized khi CONNECT

Nguyen nhan thuong gap:

- `Authorization` native header khong co token
- token het han
- token khong phai access token
- `jwt.secret` giua auth-service va chat-service khong khop

### `Illegal header: '\u0000CONNECT'`

Nguyen nhan:

- frame STOMP co ky tu NULL nam o dau message
- co dong trong hoac ky tu an truoc tu `CONNECT`
- paste frame tu tai lieu vao Postman nhung bi them ky tu dau dong

Cach xu ly:

- xoa sach o nhap message trong Postman
- go lai hoac paste lai sao cho ky tu dau tien la `C` trong `CONNECT`
- chi de ky tu NULL o cuoi frame
- thu lai voi frame mau toi gian:

```text
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer {{accessToken}}

\u0000
```

### 403 Forbidden khi lay message hoac mark read

Nguyen nhan:

- `X-User-Id` khong thuoc conversation

### 400 Bad Request khi send message

Nguyen nhan:

- thieu `conversationId`
- `content` rong va `attachments` cung rong

### Recall that bai

Nguyen nhan:

- user hien tai khong phai sender
- message da qua cua so recall 15 phut

## 8. Luong test nhanh nhat

1. Goi `POST /api/chat/conversations/direct/{{targetUserId}}`
2. Luu `conversationId`
3. CONNECT WS bang access token
4. SUBSCRIBE `/user/queue/messages`, `/user/queue/events`, `/topic/conversations/{{conversationId}}`
5. SEND `/app/chat.send`
6. Goi `GET /api/chat/conversations/{{conversationId}}/messages`
7. Luu `messageId`
8. Goi `PUT /api/chat/conversations/{{conversationId}}/read`
9. Goi `PUT /api/chat/messages/{{messageId}}/recall`

## 9. Test bang file HTML co san

Mo file sau trong browser:

`docs/chat-stomp-test.html`

Nhap:

- `ws://localhost:8080/ws/chat`
- access token hop le
- `conversationId`
- `messageId` neu can test read

Sau do:

1. Bam `Connect`
2. Kiem tra log co dong `CONNECTED`
3. Bam `Send /app/chat.send`
4. Kiem tra log `/user/queue/messages`, `/user/queue/events`, `/topic/conversations/{conversationId}`
