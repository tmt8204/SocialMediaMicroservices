# Auth Service Frontend Integration

## 1. Tổng quan

### Chức năng chính

Auth service chịu trách nhiệm cho các nghiệp vụ sau:

- Đăng ký tài khoản mới
- Đăng nhập và cấp JWT
- Refresh access token bằng refresh token
- Logout và thu hồi token đã lưu
- Đổi mật khẩu khi user đã đăng nhập
- Quên mật khẩu và reset mật khẩu qua email
- Khóa tạm tài khoản sau nhiều lần đăng nhập sai

### Base URL

Frontend có thể gọi theo 2 cách:

- Qua API Gateway: `http://localhost:8080/api/auth`
- Gọi trực tiếp auth-service: `http://localhost:8081/api/auth`

Khuyến nghị frontend dùng URL qua gateway để đồng nhất với kiến trúc microservice của dự án.

---

## 2. Authentication

### Cách login

- Endpoint: `POST /api/auth/login`
- Qua gateway: `POST http://localhost:8080/api/auth/login`
- Content-Type: `application/json`

Request body:

```json
{
  "username": "johndoe",
  "password": "Password@123"
}
```

Response thành công:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.access-token",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

### JWT gồm gì

Service trả về 2 token trong `AuthResponse`:

- `accessToken`: dùng để gửi trong header `Authorization`
- `refreshToken`: dùng để gọi API refresh token

JWT hiện tại có các claim chính:

- `sub`: user id
- `role`: role của user, ví dụ `USER`
- `token_type`: `ACCESS` hoặc `REFRESH`
- `jti`: token id ngẫu nhiên
- `iat`: thời điểm phát hành
- `exp`: thời điểm hết hạn

### Thời gian hết hạn hiện tại

Theo cấu hình hiện tại:

- `accessToken`: 7 ngày
- `refreshToken`: 7 ngày

Lưu ý: access token và refresh token đang có cùng thời gian sống. Refresh token ở đây chủ yếu phục vụ rotation và revoke, chưa phải mô hình refresh token sống lâu hơn access token.

### Frontend phải lưu và gửi token như thế nào

Backend không set cookie, không dùng session, và trả token trực tiếp trong JSON. Vì vậy frontend phải tự lưu token.

Khuyến nghị tích hợp:

- Lưu `accessToken` trong memory state nếu có thể
- Lưu `refreshToken` ở nơi có thể phục hồi sau reload app
- Với web app, nếu dùng `localStorage` hoặc `sessionStorage`, cần chấp nhận tradeoff về XSS vì backend hiện chưa hỗ trợ `httpOnly cookie`

Mỗi khi gọi endpoint cần xác thực, frontend gửi:

```http
Authorization: Bearer <accessToken>
```

### Cơ chế refresh token

Endpoint refresh:

- `POST /api/auth/refresh-token`

Request:

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh-token"
}
```

Response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.new-access-token",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.new-refresh-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

Auth service đang dùng refresh token rotation:

- Refresh token cũ sẽ bị xóa ngay khi refresh thành công
- Frontend phải ghi đè cả `accessToken` lẫn `refreshToken` bằng cặp mới
- Nếu refresh thất bại, cần xóa phiên local và đưa user về màn hình login

Ngoài ra, các hành động sau sẽ revoke toàn bộ token của user:

- Login lại
- Logout
- Change password
- Reset password

---

## 3. API

## Danh sách endpoint

| Method | URL | Auth | Mô tả |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Không | Đăng ký tài khoản và trả về token luôn |
| POST | `/api/auth/login` | Không | Đăng nhập và trả về access token + refresh token |
| POST | `/api/auth/refresh-token` | Không | Lấy cặp token mới từ refresh token |
| POST | `/api/auth/logout` | Có Bearer token | Logout và thu hồi token đã cấp |
| POST | `/api/auth/change-password` | Có Bearer token | Đổi mật khẩu hiện tại |
| POST | `/api/auth/forgot-password` | Không | Gửi email reset password nếu email tồn tại |
| POST | `/api/auth/reset-password` | Không | Đặt lại mật khẩu bằng reset token |

## 3.1 Register

### Method + URL

- `POST /api/auth/register`

### Mô tả

Tạo tài khoản mới và trả về `AuthResponse`. Sau khi đăng ký thành công, frontend có thể xem user như đã login.

### Request

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "Password@123",
  "fullName": "John Doe"
}
```

Rule validate:

- `username`: bắt buộc, tối thiểu 6 ký tự
- `email`: bắt buộc, đúng format email
- `password`: tối thiểu 8 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt trong tập `@$!%*?&`
- `fullName`: bắt buộc

### Response

Thành công: `201 Created`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.access-token",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

Lỗi `400 Bad Request`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "password Password must contain at least 8 characters including uppercase, lowercase, digit and special character (@$!%*?&)",
  "path": "/api/auth/register"
}
```

Lỗi `409 Conflict`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Username already exists",
  "path": "/api/auth/register"
}
```

## 3.2 Login

### Method + URL

- `POST /api/auth/login`

### Mô tả

Đăng nhập bằng `username` và `password`.

Nếu login thành công:

- reset số lần đăng nhập sai
- cập nhật `lastLoginAt`
- xóa tất cả token cũ của user
- cấp cặp token mới

Nếu đăng nhập sai 5 lần liên tiếp, account bị khóa 24 giờ.

### Request

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "username": "johndoe",
  "password": "Password@123"
}
```

### Response

Thành công: `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.access-token",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

Lỗi `401 Unauthorized`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "path": "/api/auth/login"
}
```

Ví dụ lỗi khi account bị khóa:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Account is locked until 2026-04-07T09:00:00Z",
  "path": "/api/auth/login"
}
```

## 3.3 Refresh Token

### Method + URL

- `POST /api/auth/refresh-token`

### Mô tả

Nhận refresh token và trả về cặp token mới.

### Request

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh-token"
}
```

### Response

Thành công: `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.new-access-token",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.new-refresh-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

Lỗi `400 Bad Request`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "refreshToken Refresh token is required",
  "path": "/api/auth/refresh-token"
}
```

Lỗi `401 Unauthorized`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid refresh token",
  "path": "/api/auth/refresh-token"
}
```

Hoặc:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Refresh token is expired or revoked",
  "path": "/api/auth/refresh-token"
}
```

## 3.4 Logout

### Method + URL

- `POST /api/auth/logout`

### Mô tả

Thu hồi toàn bộ token của user hiện tại.

### Request

Headers:

```http
Authorization: Bearer <accessToken>
```

Body: không có

### Response

Thành công: `200 OK`

Response hiện tại là plain text, không phải JSON:

```text
Logged out successfully
```

Lỗi `401 Unauthorized`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Unauthorized",
  "path": "/api/auth/logout"
}
```

### Lưu ý tích hợp

Theo implementation hiện tại, endpoint này phụ thuộc `Authentication.getName()`, nhưng `JwtAuthFilter` chỉ gắn role vào security context và không gắn username hoặc userId vào principal. Điều này có thể khiến `/logout` trả `401` ngay cả khi gửi access token hợp lệ. Frontend nên chuẩn bị fallback: nếu logout API lỗi, vẫn xóa token local và đưa user về trạng thái logged out.

## 3.5 Change Password

### Method + URL

- `POST /api/auth/change-password`

### Mô tả

Đổi mật khẩu của user đang đăng nhập. Nếu thành công, service thu hồi toàn bộ token hiện có.

### Request

Headers:

```http
Content-Type: application/json
Authorization: Bearer <accessToken>
```

Body:

```json
{
  "currentPassword": "Password@123",
  "newPassword": "NewPassword@123"
}
```

Rule validate:

- `currentPassword`: bắt buộc
- `newPassword`: cùng rule mạnh như password đăng ký

### Response

Thành công: `200 OK`

Response hiện tại là plain text:

```text
Password changed successfully
```

Lỗi `400 Bad Request`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "New password must be different from current password",
  "path": "/api/auth/change-password"
}
```

Lỗi `401 Unauthorized`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Current password is incorrect",
  "path": "/api/auth/change-password"
}
```

### Lưu ý tích hợp

Tương tự `/logout`, endpoint này cũng có thể gặp vấn đề `401` do principal trong `Authentication` không chứa username. Nếu backend chưa sửa, frontend không nên phụ thuộc endpoint này cho production flow.

## 3.6 Forgot Password

### Method + URL

- `POST /api/auth/forgot-password`

### Mô tả

Nhận email và gửi link reset password nếu account tồn tại.

API luôn trả về thông báo chung để tránh lộ email có tồn tại hay không.

### Request

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "email": "john@example.com"
}
```

### Response

Thành công: `200 OK`

Response hiện tại là plain text:

```text
If an account with that email exists, a password reset link has been sent.
```

Lỗi `400 Bad Request`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "email Invalid email format",
  "path": "/api/auth/forgot-password"
}
```

### Lưu ý tích hợp

Email reset sử dụng frontend URL cấu hình sẵn:

```text
http://localhost:3000/reset-password?token=<resetToken>
```

Frontend cần có trang nhận `token` từ query string.

## 3.7 Reset Password

### Method + URL

- `POST /api/auth/reset-password`

### Mô tả

Đặt lại mật khẩu bằng reset token đã nhận từ email.

### Request

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "token": "b2f0e8b9-1e11-4c43-a7aa-ef3fd0e08811",
  "newPassword": "NewPassword@123"
}
```

### Response

Thành công: `200 OK`

Response hiện tại là plain text:

```text
Password has been reset successfully.
```

Lỗi `400 Bad Request`:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid or expired password reset token",
  "path": "/api/auth/reset-password"
}
```

Hoặc:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Password reset token has expired",
  "path": "/api/auth/reset-password"
}
```

---

## 4. Data Model

## LoginRequest

```json
{
  "username": "johndoe",
  "password": "Password@123"
}
```

Field:

- `username`: string, bắt buộc
- `password`: string, bắt buộc

## RegisterRequest

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "Password@123",
  "fullName": "John Doe"
}
```

Field:

- `username`: string, bắt buộc, min 6 ký tự
- `email`: string, bắt buộc, đúng format email
- `password`: string, bắt buộc, password mạnh
- `fullName`: string, bắt buộc

## RefreshTokenRequest

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh-token"
}
```

Field:

- `refreshToken`: string, bắt buộc

## ChangePasswordRequest

```json
{
  "currentPassword": "Password@123",
  "newPassword": "NewPassword@123"
}
```

Field:

- `currentPassword`: string, bắt buộc
- `newPassword`: string, bắt buộc, password mạnh

## ForgotPasswordRequest

```json
{
  "email": "john@example.com"
}
```

Field:

- `email`: string, bắt buộc, đúng format email

## ResetPasswordRequest

```json
{
  "token": "b2f0e8b9-1e11-4c43-a7aa-ef3fd0e08811",
  "newPassword": "NewPassword@123"
}
```

Field:

- `token`: string, bắt buộc
- `newPassword`: string, bắt buộc, password mạnh

## AuthResponse

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.access-token",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

Field:

- `accessToken`: JWT access token
- `refreshToken`: JWT refresh token
- `id`: user id
- `username`: username
- `email`: email
- `fullName`: tên đầy đủ
- `role`: role hiện tại của user

## ApiError

Format lỗi thống nhất cho các exception JSON:

```json
{
  "timestamp": "2026-04-06T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "password Password is required",
  "path": "/api/auth/login"
}
```

Field:

- `timestamp`: thời điểm lỗi
- `status`: HTTP status code
- `error`: HTTP status text
- `message`: thông báo lỗi để hiển thị hoặc log
- `path`: endpoint gây lỗi

---

## 5. Frontend Usage

## Flow chuẩn

### Login hoặc Register

`/register` và `/login` đều trả `AuthResponse`, nên flow frontend có thể giống nhau:

1. Gọi API login hoặc register
2. Lưu `accessToken`, `refreshToken`, và user info
3. Chuyển user vào trạng thái authenticated
4. Từ các request sau, tự động gắn `Authorization: Bearer <accessToken>`

### Attach token vào header

Ví dụ với `fetch`:

```js
const accessToken = authStore.accessToken;

await fetch('http://localhost:8080/api/some-protected-endpoint', {
  method: 'GET',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken}`
  }
});
```

### Khi nào gọi refresh token

Nên gọi refresh trong các trường hợp sau:

1. Khi API trả `401` và frontend xác định user đang có `refreshToken`
2. Khi frontend decode `exp` của access token và thấy sắp hết hạn

Flow refresh đề xuất:

1. Request API bị `401`
2. Gọi `POST /api/auth/refresh-token` với `refreshToken`
3. Nếu thành công, ghi đè cặp token mới
4. Retry request cũ với access token mới
5. Nếu refresh cũng `401`, clear auth state và redirect về login

### Pseudo-code interceptor

```js
async function authorizedFetch(url, options = {}) {
  const headers = {
    ...(options.headers || {}),
    Authorization: `Bearer ${authStore.accessToken}`
  };

  let response = await fetch(url, { ...options, headers });

  if (response.status !== 401 || !authStore.refreshToken) {
    return response;
  }

  const refreshResponse = await fetch('http://localhost:8080/api/auth/refresh-token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      refreshToken: authStore.refreshToken
    })
  });

  if (!refreshResponse.ok) {
    clearAuthState();
    redirectToLogin();
    return response;
  }

  const newAuth = await refreshResponse.json();
  saveAuthState(newAuth);

  return fetch(url, {
    ...options,
    headers: {
      ...(options.headers || {}),
      Authorization: `Bearer ${newAuth.accessToken}`
    }
  });
}
```

## Lưu ý quan trọng cho frontend

- `register` trả token ngay, không cần gọi `login` thêm sau khi đăng ký thành công
- `refresh-token` trả về cả access token mới và refresh token mới, nên luôn cập nhật cả hai
- `forgot-password` luôn trả thông báo thành công dạng chung, không dùng response để suy ra email có tồn tại hay không
- Trang frontend reset password phải đọc `token` từ query string `/reset-password?token=...`
- `logout` và `change-password` hiện có rủi ro `401` do cách service gắn `Authentication`; frontend nên có fallback xử lý local state
