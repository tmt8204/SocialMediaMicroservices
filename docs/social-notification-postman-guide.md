# Hướng dẫn test Social Notification qua Postman

## Kết quả review tích hợp

| Hạng mục | Trạng thái |
|---|---|
| `spring-kafka` trong pom.xml (social-service) | ✅ |
| Kafka producer config trong application.properties | ✅ |
| `SocialNotificationEvent.java` | ✅ |
| `SocialNotificationEventProducer.java` | ✅ |
| `ReactionService` hook → `SOCIAL_POST_REACTION_CREATED` | ✅ |
| `CommentService` hook → `SOCIAL_COMMENT_CREATED` | ✅ |
| `PostService` hook → `SOCIAL_POST_CREATED` (fanout) | ✅ |
| `NotificationMapper` hỗ trợ `SOCIAL_POST_CREATED` | ✅ |
| `SocialNotificationConsumer` listen topic `social-notification-events` | ✅ |
| Topic name khớp giữa 2 service | ✅ |
| SSE endpoint `/api/notifications/stream` | ✅ |

**Tích hợp hoàn chỉnh. Có thể test end-to-end.**

---

## Kiến trúc port

| Service | Port |
|---|---|
| API Gateway | `8080` |
| Auth Service | `8081` |
| Social Service | `8083` |
| Notification Service | `8086` |

Mọi request Postman đều gửi qua Gateway: `http://localhost:8080`

---

## Phần 1 — Chuẩn bị Postman Environment

Tạo Environment trong Postman với các biến sau:

| Variable | Value |
|---|---|
| `base_url` | `http://localhost:8080` |
| `token_userA` | _(điền sau khi login userA)_ |
| `token_userB` | _(điền sau khi login userB)_ |
| `token_userC` | _(điền sau khi login userC)_ |
| `post_id` | _(điền sau khi tạo post)_ |
| `comment_id` | _(điền sau khi tạo comment)_ |
| `notification_id` | _(điền sau khi lấy danh sách notification)_ |

---

## Phần 2 — Setup: Đăng ký và đăng nhập

### 2.1 Đăng ký userA

```
POST {{base_url}}/api/auth/register
Content-Type: application/json

{
  "username": "userA",
  "email": "userA@test.com",
  "password": "Test@1234"
}
```

Lặp lại cho **userB** và **userC** (thay username, email).

### 2.2 Đăng nhập — lấy token

```
POST {{base_url}}/api/auth/login
Content-Type: application/json

{
  "email": "userA@test.com",
  "password": "Test@1234"
}
```

**Response:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "...",
  "userId": "userA_id"
}
```

Lưu `accessToken` vào `token_userA`. Lặp lại cho userB và userC.

> Lưu ý: Ghi lại `userId` của từng user để dùng ở bước thiết lập bạn bè.

---

## Phần 3 — Setup: Kết bạn userA ↔ userB ↔ userC

### 3.1 userA gửi lời mời kết bạn tới userB

```
POST {{base_url}}/api/social/friends/request/{userB_id}
Authorization: Bearer {{token_userA}}
```

### 3.2 userB chấp nhận lời mời

```
PUT {{base_url}}/api/social/friends/{userA_id}/accept
Authorization: Bearer {{token_userB}}
```

### 3.3 userA gửi lời mời kết bạn tới userC và userC chấp nhận

Lặp lại 3.1 và 3.2 với userC.

**Kết quả:** userA có 2 accepted friends: userB, userC.

---

## Phần 4 — Test Case 1: Post PRIVATE — Không có notification

**Mô tả:** userA đăng bài visibility=PRIVATE → không ai được notify.

### Bước 1: userA tạo post PRIVATE

```
POST {{base_url}}/api/social/posts/create
Authorization: Bearer {{token_userA}}
Content-Type: application/json

{
  "content": "Bai viet nay chi minh tao xem",
  "visibility": "PRIVATE"
}
```

**Response 201:** Lưu `id` vào `post_id`.

### Bước 2: Kiểm tra userB và userC không có notification mới

```
GET {{base_url}}/api/notifications
Authorization: Bearer {{token_userB}}
```

```
GET {{base_url}}/api/notifications
Authorization: Bearer {{token_userC}}
```

**Expected:** `data` rỗng hoặc không có notification `SOCIAL_POST_CREATED` mới.

---

## Phần 5 — Test Case 2: Post FRIEND — Friends nhận notification

**Mô tả:** userA đăng bài visibility=FRIEND → userB và userC đều nhận notification.

### Bước 1: userA tạo post FRIEND

```
POST {{base_url}}/api/social/posts/create
Authorization: Bearer {{token_userA}}
Content-Type: application/json

{
  "content": "Chao ca nha, minh co bai moi nay!",
  "visibility": "FRIEND"
}
```

**Response 201:** Lưu `id` vào `post_id`.

### Bước 2: Đợi Kafka xử lý (~1-2 giây), rồi kiểm tra notification của userB

```
GET {{base_url}}/api/notifications
Authorization: Bearer {{token_userB}}
```

**Expected response:**
```json
{
  "data": [
    {
      "eventType": "SOCIAL_POST_CREATED",
      "category": "SOCIAL",
      "title": "Co bai viet moi",
      "resourceType": "POST",
      "resourceId": "<post_id>",
      "actorId": "<userA_id>",
      "deeplink": "/posts/<post_id>",
      "read": false
    }
  ]
}
```

### Bước 3: Kiểm tra notification của userC

```
GET {{base_url}}/api/notifications
Authorization: Bearer {{token_userC}}
```

**Expected:** userC cũng có 1 notification `SOCIAL_POST_CREATED` riêng, giống userB.

### Bước 4: Kiểm tra unread count

```
GET {{base_url}}/api/notifications/unread-count
Authorization: Bearer {{token_userB}}
```

**Expected:**
```json
{
  "count": 1
}
```

---

## Phần 6 — Test Case 3: Post PUBLIC — Chỉ friends nhận notification

**Mô tả:** userA đăng bài PUBLIC → chỉ friends (userB, userC) nhận, không broadcast.

### Bước 1: userA tạo post PUBLIC

```
POST {{base_url}}/api/social/posts/create
Authorization: Bearer {{token_userA}}
Content-Type: application/json

{
  "content": "Bai public cho moi nguoi",
  "visibility": "PUBLIC"
}
```

**Response 201:** Lưu `id` vào `post_id`.

### Bước 2: userB nhận notification

```
GET {{base_url}}/api/notifications
Authorization: Bearer {{token_userB}}
```

**Expected:** có `SOCIAL_POST_CREATED` mới từ userA.

### Ghi chú:
- userX (không phải friend) sẽ **không** có notification.
- userX vẫn có thể thấy bài qua feed public, nhưng đó là pull-based (họ tự vào xem), không phải push notification.

---

## Phần 7 — Test Case 4: React post — Owner nhận notification

**Mô tả:** userB react post của userA → userA nhận `SOCIAL_POST_REACTION_CREATED`.

### Bước 1: Cần có post của userA

Dùng `post_id` từ test case trước, hoặc tạo mới (bất kỳ visibility nào).

### Bước 2: userB react post của userA

```
POST {{base_url}}/api/social/posts/{{post_id}}/react
Authorization: Bearer {{token_userB}}
```

**Response 200:**
```json
{
  "postId": 15,
  "totalReacts": 1,
  "reactedByCurrentUser": true
}
```

### Bước 3: userA kiểm tra notification

```
GET {{base_url}}/api/notifications
Authorization: Bearer {{token_userA}}
```

**Expected:**
```json
{
  "data": [
    {
      "eventType": "SOCIAL_POST_REACTION_CREATED",
      "category": "SOCIAL",
      "title": "Co nguoi da tha cam xuc bai viet cua ban",
      "resourceType": "POST",
      "resourceId": "<post_id>",
      "actorId": "<userB_id>",
      "deeplink": "/posts/<post_id>",
      "read": false
    }
  ]
}
```

### Bước 4: Kiểm tra self-react không tạo notification

```
POST {{base_url}}/api/social/posts/{{post_id}}/react
Authorization: Bearer {{token_userA}}
```

Sau đó gọi lại `GET /api/notifications` của userA → không có notification mới từ chính mình.

### Bước 5: Kiểm tra react trùng không tạo thêm notification

Gọi lại step 2 (userB react lại bài đã react):

```
POST {{base_url}}/api/social/posts/{{post_id}}/react
Authorization: Bearer {{token_userB}}
```

**Expected response:** `reactedByCurrentUser: true` nhưng `totalReacts` không tăng.
**Expected notification:** không có notification mới thêm cho userA.

---

## Phần 8 — Test Case 5: Comment post — Owner nhận notification

**Mô tả:** userB comment vào post của userA → userA nhận `SOCIAL_COMMENT_CREATED`.

### Bước 1: userB comment vào post của userA

```
POST {{base_url}}/api/social/posts/{{post_id}}/comments
Authorization: Bearer {{token_userB}}
Content-Type: application/json

{
  "content": "Bai hay qua userA oi!"
}
```

**Response 201:** Lưu `id` vào `comment_id`.

### Bước 2: userA kiểm tra notification

```
GET {{base_url}}/api/notifications
Authorization: Bearer {{token_userA}}
```

**Expected:**
```json
{
  "data": [
    {
      "eventType": "SOCIAL_COMMENT_CREATED",
      "category": "SOCIAL",
      "title": "Co binh luan moi",
      "resourceType": "COMMENT",
      "resourceId": "<comment_id>",
      "actorId": "<userB_id>",
      "deeplink": "/posts/<post_id>",
      "read": false
    }
  ]
}
```

### Bước 3: Kiểm tra self-comment không tạo notification

```
POST {{base_url}}/api/social/posts/{{post_id}}/comments
Authorization: Bearer {{token_userA}}
Content-Type: application/json

{
  "content": "Minh comment chinh bai minh"
}
```

Sau đó gọi `GET /api/notifications` của userA → không có notification mới từ chính mình.

---

## Phần 9 — Test notification đọc/đánh dấu

### 9.1 Lấy danh sách notification và lưu notification_id

```
GET {{base_url}}/api/notifications
Authorization: Bearer {{token_userA}}
```

Lưu `id` của 1 notification vào `notification_id`.

### 9.2 Đánh dấu đã đọc

```
PUT {{base_url}}/api/notifications/{{notification_id}}/read
Authorization: Bearer {{token_userA}}
```

**Expected response:**
```json
{
  "id": "...",
  "read": true
}
```

### 9.3 Kiểm tra unread count giảm

```
GET {{base_url}}/api/notifications/unread-count
Authorization: Bearer {{token_userA}}
```

**Expected:** `count` giảm đi 1.

### 9.4 Lọc theo category và trạng thái đọc

```
GET {{base_url}}/api/notifications?category=SOCIAL&read=false
Authorization: Bearer {{token_userA}}
```

---

## Phần 10 — Test SSE (Server-Sent Events) realtime

SSE không test được trực tiếp bằng Postman thông thường. Dùng **browser DevTools** hoặc **EventSource** JavaScript.

### Cách test bằng browser console

Mở browser, vào Console, paste đoạn sau (thay token):

```javascript
const token_userA = "eyJ..."; // paste token ở đây

const evtSource = new EventSource(
  "http://localhost:8080/api/notifications/stream",
  {
    // SSE with custom header cần dùng fetch hoặc proxy
    // Browser EventSource không hỗ trợ custom header
    // → test trực tiếp với service port
  }
);
evtSource.onmessage = (e) => console.log("SSE:", e.data);
evtSource.onerror = (e) => console.error("SSE error:", e);
```

> **Lưu ý:** Browser `EventSource` không gửi được header tùy chỉnh. Để test SSE có `X-User-Id`, dùng port trực tiếp của notification-service (`localhost:8086`) và gọi endpoint `/api/notifications/stream` với header `X-User-Id: <userId>`. Có thể dùng extension **Yaak** hoặc **Insomnia** thay Postman để test SSE có header.

### Xác nhận SSE hoạt động

1. Mở SSE connection cho userA.
2. Từ Postman, dùng token của userB react post của userA.
3. Trong tối đa vài giây, console SSE của userA sẽ in ra event notification mới.

---

## Phần 11 — Bảng tổng hợp expected results

| Hành động | Actor | Recipient nhận notification | Event type |
|---|---|---|---|
| Tạo post PRIVATE | userA | Không ai | — |
| Tạo post FRIEND | userA | userB, userC (mỗi người 1 event) | `SOCIAL_POST_CREATED` |
| Tạo post PUBLIC | userA | userB, userC (mỗi người 1 event) | `SOCIAL_POST_CREATED` |
| React post userA | userB | userA | `SOCIAL_POST_REACTION_CREATED` |
| Self-react post | userA | Không ai | — |
| React trùng (đã react rồi) | userB | Không ai | — |
| Comment vào post userA | userB | userA | `SOCIAL_COMMENT_CREATED` |
| Self-comment | userA | Không ai | — |

---

## Phần 12 — Checklist khi test bị lỗi

| Triệu chứng | Nguyên nhân khả năng | Cách kiểm tra |
|---|---|---|
| Notification không xuất hiện sau 5s | Kafka chưa chạy | Kiểm tra Kafka broker `:9092` |
| Notification không xuất hiện sau 5s | Topic chưa được tạo | Xem log notification-service khi startup |
| Log `Unsupported social eventType` | Thiếu case trong mapper | Đã fix — kiểm tra lại build |
| Log `postId is required` | Payload event thiếu field | Kiểm tra `SocialNotificationEvent` object |
| Notification bị duplicate | `sourceEventId` không unique | `eventId` dùng UUID — không xảy ra |
| userA nhận notification từ chính mình | Skip điều kiện bị sai | Kiểm tra `!userId.equals(post.getUserId())` |
| Không connect được SSE | Gateway không forward header | Test trực tiếp port 8086 |
