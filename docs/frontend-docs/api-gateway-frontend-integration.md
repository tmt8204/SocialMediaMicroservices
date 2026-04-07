# API Gateway Frontend Integration

## 1. Tổng quan

`social-media-api-gateway` là entrypoint chung cho frontend khi gọi toàn bộ backend microservices.

Gateway hiện làm 3 việc chính:

- route request tới đúng service theo path
- validate JWT access token cho các path private
- inject thông tin user xuống downstream service qua header nội bộ

Frontend nên ưu tiên gọi gateway thay vì gọi trực tiếp từng service, vì:

- chỉ cần một base URL
- auth được xử lý tập trung
- các service phía sau có thể tin vào `X-User-Id` và `X-Role` do gateway set

## Base URL chung

- `http://localhost:8080`

## Gateway không phải business API riêng

Gateway không cung cấp business endpoint riêng cho frontend ngoài:

- route tới các service khác
- `actuator/health`
- `actuator/info`

Nói ngắn gọn: frontend dùng gateway như cổng vào duy nhất, không phải như một domain service riêng.

---

## 2. Route map hiện tại

Gateway hiện forward các path sau.

| Route qua gateway | Service đích mặc định |
| --- | --- |
| `/api/auth/**` | `http://localhost:8081` |
| `/api/users/**` | `http://localhost:8082` |
| `/api/social/**` | `http://localhost:8083` |
| `/api/media/**` | `http://localhost:8084` |
| `/api/chat/**` | `http://localhost:8085` |
| `/api/notifications/**` | `http://localhost:8086` |
| `/ws/chat/**` | `ws://localhost:8085` |

## Thực tế FE nên dùng

### REST

- `http://localhost:8080/api/auth/...`
- `http://localhost:8080/api/users/...`
- `http://localhost:8080/api/social/...`
- `http://localhost:8080/api/media/...`
- `http://localhost:8080/api/chat/...`
- `http://localhost:8080/api/notifications/...`

### WebSocket

- `ws://localhost:8080/ws/chat`

---

## 3. Authentication qua gateway

## 3.1. Header frontend phải gửi

Với mọi route private, frontend nên gửi:

```http
Authorization: Bearer <accessToken>
```

Gateway chỉ chấp nhận:

- header tồn tại
- prefix đúng là `Bearer `
- token là `ACCESS` token hợp lệ

Gateway sẽ reject:

- thiếu `Authorization`
- token hết hạn
- token sai chữ ký
- token không phải loại `ACCESS`
- token đã bị revoke hoặc không còn active

## 3.2. Claim JWT gateway cần

Gateway đọc từ JWT các field sau:

- `sub` -> user id
- `role` -> role user
- `token_type` -> phải là `ACCESS`

Nếu thiếu `sub` hoặc `role`, gateway trả `401`.

## 3.3. Gateway inject gì xuống downstream

Sau khi xác thực thành công, gateway sẽ gắn thêm header nội bộ:

- `X-User-Id: <jwt.sub>`
- `X-Role: <jwt.role>`

Frontend không cần tự gửi hai header này khi đi qua gateway.

### Vì sao điều này quan trọng

Các service như:

- user-service
- social-service
- media-service
- notification-service

đều đang dựa vào `X-User-Id` để biết current user.

---

## 4. Public path và private path

## 4.1. Public path hiện tại

Gateway hiện cho phép đi qua không cần JWT với các path sau:

- `/api/auth/**`
- `/actuator/health`
- `/actuator/info`
- `/ws/chat/**`
- `/notification-sse-test.html`

## 4.2. Ý nghĩa thực tế của từng public path

### `/api/auth/**`

Public vì login/register/refresh token phải gọi được khi chưa có phiên authenticated hoàn chỉnh.

### `/ws/chat/**`

Public ở lớp HTTP handshake của gateway, nhưng không có nghĩa là chat WebSocket không cần auth.

Thực tế:

- gateway không chặn JWT ở handshake WebSocket này
- chat-service sẽ tự xác thực JWT trong STOMP `CONNECT` frame

Frontend vẫn phải gửi JWT khi connect chat STOMP.

### `/api/notifications/stream`

Không nằm trong public path, nên SSE notification qua gateway vẫn cần JWT hợp lệ.

---

## 5. Revocation check

Gateway hiện có bật kiểm tra revoke token:

- `gateway.auth.revocation-check.enabled=true`
- mode hiện tại: `mongo`
- token type kiểm tra: `ACCESS`

Điều này có nghĩa là kể cả JWT còn hạn, gateway vẫn có thể chặn nếu token không còn active trong store MongoDB.

### Hệ quả cho frontend

Frontend có thể gặp `401` trong các trường hợp:

- user logout ở auth-service
- user login lại và token cũ bị xóa
- user đổi mật khẩu hoặc reset password làm token cũ bị revoke

Trong các trường hợp đó, frontend nên:

1. thử refresh token nếu còn refresh token hợp lệ
2. nếu refresh cũng fail, clear auth state và đưa user về login

---

## 6. Error format của gateway

Gateway hiện trả lỗi `401` trực tiếp từ filter với format JSON đơn giản:

```json
{
  "error": "unauthorized",
  "message": "Missing or invalid Authorization header"
}
```

Các message có thể gặp:

- `Missing or invalid Authorization header`
- `Invalid or expired token`
- `Missing required token claims`
- `Token revoked or unknown`

### Lưu ý cho FE

Format lỗi này khác với nhiều service phía sau, vì gateway chỉ trả:

- `error`
- `message`

không có `status`, `timestamp`, `path`.

Frontend nên ưu tiên đọc `message` khi xử lý lỗi từ gateway.

---

## 7. Frontend usage

## 7.1. Quy tắc chung

Frontend nên coi gateway là base URL duy nhất:

```text
http://localhost:8080
```

và map các domain như sau:

- auth -> `/api/auth/...`
- users -> `/api/users/...`
- social -> `/api/social/...`
- media -> `/api/media/...`
- chat REST -> `/api/chat/...`
- notifications -> `/api/notifications/...`
- chat WS -> `ws://localhost:8080/ws/chat`

## 7.2. Ví dụ REST wrapper

```js
const API_BASE_URL = 'http://localhost:8080';

async function apiFetch(path, options = {}) {
  const headers = {
    ...(options.headers || {}),
    'Content-Type': 'application/json'
  };

  if (authStore.accessToken) {
    headers.Authorization = `Bearer ${authStore.accessToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    let errorBody = null;
    try {
      errorBody = await response.json();
    } catch {
      errorBody = null;
    }

    const message = errorBody?.message || 'Request failed';
    throw new Error(message);
  }

  return response;
}
```

## 7.3. Ví dụ gọi API private qua gateway

```js
const response = await apiFetch('/api/users/me', {
  method: 'GET'
});

const profile = await response.json();
```

## 7.4. Ví dụ gọi API public qua gateway

```js
const response = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    username: 'johndoe',
    password: 'Password@123'
  })
});
```

## 7.5. Ví dụ connect chat WebSocket qua gateway

```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws/chat',
  connectHeaders: {
    Authorization: `Bearer ${authStore.accessToken}`
  },
  reconnectDelay: 5000
});

client.activate();
```

Lưu ý:

- route `/ws/chat` là public ở gateway
- nhưng JWT vẫn phải có trong STOMP `CONNECT` vì chat-service validate ở tầng STOMP, không phải ở gateway filter

## 7.6. Ví dụ SSE notification qua gateway

Vì notification stream đi qua gateway cần JWT, browser frontend nên dùng fetch-based SSE client có hỗ trợ header, ví dụ `@microsoft/fetch-event-source`.

```js
import { fetchEventSource } from '@microsoft/fetch-event-source';

await fetchEventSource('http://localhost:8080/api/notifications/stream', {
  headers: {
    Authorization: `Bearer ${authStore.accessToken}`
  },
  onmessage(event) {
    console.log(event.event, event.data);
  }
});
```

---

## 8. Điều frontend nên tránh

1. Không nên gọi trực tiếp từng service trong flow bình thường nếu đã có gateway.
2. Không nên tự gửi `X-User-Id` hoặc `X-Role` khi đã đi qua gateway.
3. Không nên dùng refresh token để gọi API private qua gateway.
4. Không nên assume mọi lỗi backend có cùng format JSON, vì gateway có format lỗi riêng.
5. Không nên assume public path `/ws/chat/**` nghĩa là chat không cần auth.

---

## 9. Những điểm FE cần biết trước

1. Gateway hiện validate access token và kiểm tra token active trong Mongo store.
2. Token cũ có thể bị gateway từ chối ngay cả khi JWT chưa hết hạn nếu auth-service đã revoke token.
3. Error `401` từ gateway có format đơn giản hơn error từ downstream service.
4. Notification SSE qua gateway cần chiến lược gửi `Authorization` header phù hợp với browser.
5. Chat WebSocket qua gateway cần JWT ở STOMP `CONNECT`, không phải chỉ ở HTTP handshake.

---

## 10. Contract FE nên dùng ngay

Nếu chỉ lấy phần tối thiểu để frontend chuẩn hóa việc gọi backend:

### Base URL

- `http://localhost:8080`

### REST prefix map

- `/api/auth`
- `/api/users`
- `/api/social`
- `/api/media`
- `/api/chat`
- `/api/notifications`

### WebSocket

- `ws://localhost:8080/ws/chat`

### Auth rule

- public: `/api/auth/**`
- private: gần như toàn bộ route còn lại
- header cho route private: `Authorization: Bearer <accessToken>`
