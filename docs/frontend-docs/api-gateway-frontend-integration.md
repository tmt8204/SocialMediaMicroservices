# API Gateway Frontend Integration

## 1. Tổng quan

`social-media-api-gateway` là entrypoint chung cho frontend khi gọi backend.

Gateway chịu trách nhiệm:

- route request tới đúng service
- validate `ACCESS` token cho private API
- inject `X-User-Id` và `X-Role` xuống downstream service
- cho phép browser gửi request có cookie khi frontend gọi từ origin khác

### Base URL

- `http://localhost:8080`

Frontend nên dùng gateway làm base URL duy nhất.

---

## 2. Route map hiện tại

| Route qua gateway | Service đích mặc định |
| --- | --- |
| `/api/auth/**` | `http://localhost:8081` |
| `/api/users/**` | `http://localhost:8082` |
| `/api/social/**` | `http://localhost:8083` |
| `/api/media/**` | `http://localhost:8084` |
| `/api/chat/**` | `http://localhost:8085` |
| `/api/notifications/**` | `http://localhost:8086` |
| `/ws/chat/**` | `ws://localhost:8085` |

---

## 3. Auth model qua gateway

### 3.1 Access token

Với mọi private API, frontend gửi:

```http
Authorization: Bearer <accessToken>
```

Gateway chỉ chấp nhận token hợp lệ với `token_type=ACCESS`.

### 3.2 Refresh token

Refresh token không đi qua `Authorization` header.

Refresh token hiện nằm trong `HttpOnly cookie` do auth-service set qua gateway. Vì vậy với các request auth có liên quan đến refresh cookie, frontend phải dùng:

```ts
credentials: 'include'
```

Các request nên bật `credentials: 'include'`:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh-token`
- `POST /api/auth/logout`

---

## 4. Public path và private path

### Public path hiện tại

- `/api/auth/**`
- `/actuator/health`
- `/actuator/info`
- `/ws/chat/**`
- `/notification-sse-test.html`

### Private path

Các route business còn lại đi qua gateway đều nên được gọi với access token hợp lệ.

---

## 5. Gateway inject gì xuống downstream

Sau khi xác thực access token thành công, gateway gắn thêm:

- `X-User-Id: <jwt.sub>`
- `X-Role: <jwt.role>`

Frontend không tự set hai header này.

---

## 6. CORS và cookie

Gateway đã được cấu hình để cho phép frontend origin mặc định `http://localhost:3000` gọi kèm cookie.

Browser chỉ gửi refresh cookie trong request cross-origin nếu có đủ cả hai điều kiện:

- frontend dùng `credentials: 'include'`
- server bật CORS với `allowCredentials=true`

Nếu deploy FE ở origin khác, cần đổi `APP_CORS_ALLOWED_ORIGIN` tại gateway và auth-service.

---

## 7. Error handling cho FE

Gateway có thể trả `401` khi:

- thiếu `Authorization`
- access token hết hạn
- token sai chữ ký
- token không phải `ACCESS`
- token đã bị revoke

Luồng FE nên làm:

1. gọi API với access token trong memory
2. nếu nhận `401`, gọi `POST /api/auth/refresh-token` với `credentials: 'include'`
3. nếu refresh thành công, cập nhật access token trong memory và retry request cũ
4. nếu refresh thất bại, xóa auth state và chuyển về login

---

## 8. Request wrapper gợi ý

```ts
const API_BASE_URL = 'http://localhost:8080';

async function apiFetch(path: string, options: RequestInit = {}) {
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (authStore.accessToken) {
    headers.set('Authorization', `Bearer ${authStore.accessToken}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    credentials: path.startsWith('/api/auth/') ? 'include' : options.credentials
  });

  return response;
}
```

Với upload `FormData`, giữ nguyên `Content-Type` do browser tự sinh.
