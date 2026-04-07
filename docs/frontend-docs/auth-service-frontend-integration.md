# Auth Service Frontend Integration

## 1. Tổng quan

Auth service chịu trách nhiệm:

- đăng ký tài khoản
- đăng nhập
- cấp access token
- xoay refresh token
- logout và revoke token
- đổi mật khẩu, quên mật khẩu, reset mật khẩu

### Base URL

Frontend nên gọi qua gateway:

- `http://localhost:8080/api/auth`

Có thể gọi trực tiếp auth-service để debug:

- `http://localhost:8081/api/auth`

---

## 2. Mô hình token hiện tại

Backend đã chuyển sang mô hình:

- `accessToken`: trả trong JSON, frontend chỉ giữ trong memory state
- `refreshToken`: không trả trong body, backend set vào `HttpOnly cookie`

### Thời gian sống hiện tại

- `accessToken`: 10 phút
- `refreshToken`: 3 ngày

### JWT claims chính

- `sub`: user id
- `role`: role của user
- `token_type`: `ACCESS` hoặc `REFRESH`
- `jti`: token id
- `iat`: thời điểm phát hành
- `exp`: thời điểm hết hạn

---

## 3. Quy tắc frontend phải làm

### 3.1 Access token

- lưu trong memory store, ví dụ React context, Zustand, Redux hoặc module state
- không đọc/ghi `refreshToken` trong JavaScript app state
- khi reload trang, access token có thể mất và frontend phải gọi refresh để phục hồi phiên

### 3.2 Refresh token cookie

- backend tự set cookie khi `register`, `login`, `refresh-token`
- cookie là `HttpOnly`, frontend không thể đọc bằng JavaScript
- với `register`, `login`, `refresh-token`, `logout`, frontend phải gửi `credentials: 'include'`

Ví dụ:

```ts
await fetch('http://localhost:8080/api/auth/refresh-token', {
  method: 'POST',
  credentials: 'include'
});
```

### 3.3 Gọi API private

Với API private qua gateway, frontend vẫn gửi:

```http
Authorization: Bearer <accessToken>
```

---

## 4. Endpoints chính

| Method | URL | Auth | Mô tả |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Không | Tạo tài khoản, trả access token và set refresh cookie |
| POST | `/api/auth/login` | Không | Đăng nhập, trả access token và set refresh cookie |
| POST | `/api/auth/refresh-token` | Không | Đọc refresh token từ cookie, trả access token mới và xoay refresh cookie |
| POST | `/api/auth/logout` | Access token hoặc refresh cookie | Revoke token và xóa refresh cookie |
| POST | `/api/auth/change-password` | Có Bearer token | Đổi mật khẩu |
| POST | `/api/auth/forgot-password` | Không | Gửi email reset password |
| POST | `/api/auth/reset-password` | Không | Đặt lại mật khẩu |

---

## 5. Register

### Request

`POST /api/auth/register`

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "Password@123",
  "fullName": "John Doe"
}
```

### Response

Status: `201 Created`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.access-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

Kèm theo `Set-Cookie: refresh_token=...; HttpOnly; Path=/api/auth; SameSite=Lax`

---

## 6. Login

### Request

`POST /api/auth/login`

```json
{
  "username": "johndoe",
  "password": "Password@123"
}
```

Frontend nhớ gọi với `credentials: 'include'` để browser nhận refresh cookie.

### Response

Status: `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.access-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

Nếu login sai 5 lần liên tiếp thì tài khoản bị khóa 24 giờ.

---

## 7. Refresh token

### Request

`POST /api/auth/refresh-token`

Request body có thể để trống. Backend ưu tiên đọc refresh token từ cookie.

```ts
const response = await fetch('http://localhost:8080/api/auth/refresh-token', {
  method: 'POST',
  credentials: 'include'
});
```

### Response

Status: `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.new-access-token",
  "id": "67f25f9e63d6d40d672f98d2",
  "username": "johndoe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "USER"
}
```

Kèm theo `Set-Cookie` với refresh token mới.

Nếu refresh fail:

- clear auth state trong frontend
- điều hướng user về login

---

## 8. Logout

### Request

`POST /api/auth/logout`

Khuyến nghị gửi cả:

- `Authorization: Bearer <accessToken>` nếu frontend còn access token
- `credentials: 'include'` để refresh cookie cũng được gửi lên

```ts
await fetch('http://localhost:8080/api/auth/logout', {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${accessToken}`
  },
  credentials: 'include'
});
```

### Response

- `200 OK`
- backend trả `Set-Cookie` để xóa refresh cookie

Frontend xử lý:

- xóa access token khỏi memory
- reset current user state

---

## 9. Suggested FE flow

### App bootstrap

1. app load
2. access token trong memory đang rỗng
3. gọi `POST /api/auth/refresh-token` với `credentials: 'include'`
4. nếu thành công, lưu access token mới vào memory và tiếp tục vào app
5. nếu thất bại, coi như anonymous session

### Request wrapper

1. gửi access token qua `Authorization`
2. nếu gặp `401`, gọi `POST /api/auth/refresh-token` với `credentials: 'include'`
3. nếu refresh thành công, cập nhật access token trong memory và retry request cũ
4. nếu refresh cũng fail, clear auth state và điều hướng login

---

## 10. TypeScript shape gợi ý

```ts
export type AuthResponse = {
  accessToken: string;
  id: string;
  username: string;
  email: string;
  fullName: string;
  role: string;
};
```

Không còn `refreshToken` trong response body.
