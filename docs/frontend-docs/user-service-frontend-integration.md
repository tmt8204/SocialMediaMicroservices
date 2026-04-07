# User Service Frontend Integration

## 1. Tổng quan

`social-media-user-service` chịu trách nhiệm quản lý profile người dùng trong hệ thống.

Service này hiện hỗ trợ:

- lấy profile của chính mình
- lấy profile người khác theo `username`
- search user theo `username` hoặc `fullName`
- update profile

## Base URL

Khuyến nghị frontend gọi qua gateway:

- `http://localhost:8080/api/users`

Nếu gọi trực tiếp vào service:

- `http://localhost:8082/api/users`

## Authentication

Khi gọi qua gateway, frontend nên gửi:

```http
Authorization: Bearer <accessToken>
```

Gateway sẽ inject `X-User-Id` xuống user-service cho các endpoint cần user hiện tại.

Nếu test trực tiếp service local thì phải tự gửi `X-User-Id` cho endpoint `/me` và `/update-profile`.

---

## 2. Hiện trạng quan trọng cần biết

User-service không tự tạo profile khi frontend gọi API register. Profile hiện được tạo bất đồng bộ từ Kafka event `auth.user.created` do auth-service publish.

Điều này có nghĩa là sau khi register thành công ở auth-service:

1. auth-service trả token ngay
2. auth-service publish Kafka event `CreateProfileEvent`
3. user-service consume event và tạo profile MongoDB

Trong điều kiện bình thường việc này sẽ nhanh, nhưng frontend vẫn nên hiểu đây là flow async.

### Hệ quả cho frontend

- sau khi vừa register xong, nếu gọi `/api/users/me` quá sớm trong lúc event chưa được consume, có thể gặp `404 User not found`
- cách an toàn là sau register, frontend có thể retry ngắn cho `/api/users/me` hoặc tạm dùng user info từ `AuthResponse` trước

---

## 3. Danh sách endpoint

| Method | URL | Mô tả |
| --- | --- | --- |
| GET | `/api/users/me` | Lấy profile của current user |
| GET | `/api/users/{username}` | Lấy profile người khác theo username |
| GET | `/api/users/search?q=...` | Search theo username hoặc fullName |
| PUT | `/api/users/update-profile` | Cập nhật profile của current user |

---

## 4. Data model

## 4.1. UserResponse

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "bio": "Hello world",
  "coverUrl": "https://cdn.example.com/cover.jpg",
  "birthDay": "2000-01-01",
  "location": "Ho Chi Minh City",
  "relationship": "Single",
  "phone": 123456789,
  "updateAt": "2026-04-06T10:00:00Z"
}
```

### Field hiện có

- `username`: string
- `email`: string
- `fullName`: string
- `avatarUrl`: string
- `bio`: string
- `coverUrl`: string
- `birthDay`: string
- `location`: string
- `relationship`: string
- `phone`: integer
- `updateAt`: ISO timestamp

### Lưu ý rất quan trọng

Field thời gian hiện tại theo code là:

- `updateAt`

chứ không phải:

- `updatedAt`

Frontend nên map đúng theo field đang trả về thực tế.

## 4.2. UpdateProfileRequest

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "bio": "Hello world",
  "coverUrl": "https://cdn.example.com/cover.jpg",
  "birthDay": "2000-01-01",
  "location": "Ho Chi Minh City",
  "relationship": "Single",
  "phone": 123456789,
  "updateAt": "2026-04-06T10:00:00Z"
}
```

### Validation hiện tại

- `username`: bắt buộc
- `email`: bắt buộc
- `fullName`: bắt buộc
- các field còn lại optional

Lưu ý:

- `updateAt` có trong DTO request nhưng backend không dùng giá trị từ client; service sẽ tự set lại bằng `Instant.now()` khi update thành công

---

## 5. API chi tiết

## 5.1. Lấy profile của tôi

### Method + URL

- `GET /api/users/me`

### Request

Headers:

```http
Authorization: Bearer <accessToken>
```

### Response

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "bio": "Hello world",
  "coverUrl": "https://cdn.example.com/cover.jpg",
  "birthDay": "2000-01-01",
  "location": "Ho Chi Minh City",
  "relationship": "Single",
  "phone": 123456789,
  "updateAt": "2026-04-06T10:00:00Z"
}
```

### Lỗi có thể gặp

Ví dụ `404`:

```json
{
  "timestamp": "2026-04-06T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "User not found",
  "path": "/api/users/me"
}
```

Điều này đặc biệt có thể xảy ra ngay sau register nếu profile async chưa được tạo xong.

## 5.2. Lấy profile người khác theo username

### Method + URL

- `GET /api/users/{username}`

Ví dụ:

```http
GET http://localhost:8080/api/users/johndoe
Authorization: Bearer <accessToken>
```

### Response

Shape giống `UserResponse`.

### Lưu ý cho frontend

- API này lấy theo `username`, không phải `userId`
- backend dùng tìm kiếm `username` không phân biệt hoa thường

## 5.3. Search user

### Method + URL

- `GET /api/users/search?q={keyword}`

Ví dụ:

```http
GET http://localhost:8080/api/users/search?q=john
Authorization: Bearer <accessToken>
```

### Response

```json
[
  {
    "username": "johndoe",
    "email": "john@example.com",
    "fullName": "John Doe",
    "avatarUrl": "https://cdn.example.com/avatar.jpg",
    "bio": "Hello world",
    "coverUrl": "https://cdn.example.com/cover.jpg",
    "birthDay": "2000-01-01",
    "location": "Ho Chi Minh City",
    "relationship": "Single",
    "phone": 123456789,
    "updateAt": "2026-04-06T10:00:00Z"
  }
]
```

### Search behavior hiện tại

- search theo regex MongoDB trên `username` hoặc `fullName`
- không phân biệt hoa thường
- nếu `q` null hoặc blank, backend trả `[]`, không lỗi

### Lưu ý cho FE

- response hiện chưa có `userId`
- nếu UI cần `userId`, hiện user-service chưa expose field này trong `UserResponse`

## 5.4. Update profile

### Method + URL

- `PUT /api/users/update-profile`

### Request

Headers:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Body:

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "bio": "Hello world",
  "coverUrl": "https://cdn.example.com/cover.jpg",
  "birthDay": "2000-01-01",
  "location": "Ho Chi Minh City",
  "relationship": "Single",
  "phone": 123456789,
  "updateAt": "2026-04-06T10:00:00Z"
}
```

### Response

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "bio": "Hello world",
  "coverUrl": "https://cdn.example.com/cover.jpg",
  "birthDay": "2000-01-01",
  "location": "Ho Chi Minh City",
  "relationship": "Single",
  "phone": 123456789,
  "updateAt": "2026-04-06T10:30:00Z"
}
```

### Behavior thực tế

- update theo current user lấy từ `X-User-Id`
- backend check trùng `username` và `email` không phân biệt hoa thường
- nếu trùng với account khác thì trả `409 Conflict`

Ví dụ lỗi `409`:

```json
{
  "timestamp": "2026-04-06T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Username already exists",
  "path": "/api/users/update-profile"
}
```

---

## 6. Error format

User-service có format lỗi khá chuẩn và thống nhất.

## Validation error

```json
{
  "timestamp": "2026-04-06T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Full name is required",
  "path": "/api/users/update-profile"
}
```

## Missing header

```json
{
  "timestamp": "2026-04-06T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Missing required header: X-User-Id",
  "path": "/api/users/me"
}
```

## Invalid JSON

```json
{
  "timestamp": "2026-04-06T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid request body",
  "path": "/api/users/update-profile"
}
```

## Not found

```json
{
  "timestamp": "2026-04-06T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "User not found",
  "path": "/api/users/me"
}
```

---

## 7. Frontend usage

## 7.1. Flow sau login hoặc sau app reload

1. Lấy `accessToken` từ auth state
2. Gọi `GET /api/users/me`
3. Nếu thành công, lưu profile vào store
4. Nếu vừa register xong mà nhận `404`, retry ngắn hoặc dùng tạm data từ `AuthResponse`

## 7.2. Flow sau register

Khuyến nghị practical cho frontend:

1. register qua auth-service
2. lưu `AuthResponse`
3. dùng tạm `username`, `email`, `fullName` từ `AuthResponse`
4. gọi `GET /api/users/me`
5. nếu `404`, retry sau 500ms đến 1s trong vài lần ngắn

Lý do:

- profile được tạo qua Kafka async, không phải transaction đồng bộ với register

## 7.3. Flow update profile

1. user chọn avatar/cover mới nếu có
2. nếu cần upload ảnh, gọi media-service trước
3. lấy `mediaUrl` trả về
4. gọi `PUT /api/users/update-profile`
5. cập nhật lại local profile bằng response mới

Ví dụ:

```js
async function updateProfile(payload, accessToken) {
  const response = await fetch('http://localhost:8080/api/users/update-profile', {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to update profile');
  }

  return response.json();
}
```

## 7.4. Flow search user

1. debounce input search
2. gọi `GET /api/users/search?q=keyword`
3. render danh sách user
4. khi click user, điều hướng sang profile route dựa trên `username`

### Vì sao nên route theo username

API hiện hỗ trợ lấy profile người khác bằng `username`, không phải `userId`.

---

## 8. Những điểm FE cần biết trước

1. Profile user được tạo async từ Kafka event sau register ở auth-service.
2. `UserResponse` hiện không trả `userId`.
3. Field thời gian hiện là `updateAt`, không phải `updatedAt`.
4. Search trả `[]` nếu `q` rỗng, không ném lỗi.
5. `GET /api/users/{username}` dùng username case-insensitive.
6. `PUT /api/users/update-profile` check trùng `username` và `email` không phân biệt hoa thường.

---

## 9. Contract FE nên dùng ngay

Nếu chỉ lấy phần tối thiểu để tích hợp nhanh:

### REST

- `GET /api/users/me`
- `GET /api/users/{username}`
- `GET /api/users/search?q={keyword}`
- `PUT /api/users/update-profile`

### Field nên lưu trong frontend profile store

- `username`
- `email`
- `fullName`
- `avatarUrl`
- `bio`
- `coverUrl`
- `birthDay`
- `location`
- `relationship`
- `phone`
- `updateAt`
