# Media Service Frontend Integration

## 1. Tổng quan

`social-media-media-service` chịu trách nhiệm:

- upload ảnh và video lên Cloudinary
- trả metadata để các service khác lưu tham chiếu
- xóa media theo `publicId`
- xóa nhiều media cùng lúc

Service này không lưu binary file trong database. Frontend upload file lên media-service trước, sau đó dùng metadata trả về để gửi tiếp sang social-service hoặc chat-service.

## Base URL

Khuyến nghị frontend gọi qua gateway:

- `http://localhost:8080/api/media`

Nếu gọi trực tiếp vào media-service:

- `http://localhost:8084/api/media`

## Authentication

Khi gọi qua gateway, frontend nên gửi:

```http
Authorization: Bearer <accessToken>
```

Gateway sẽ inject `X-User-Id` xuống media-service.

Nếu gọi trực tiếp vào media-service để test local, service hiện chấp nhận một trong hai cách để xác định user upload:

- header `X-User-Id`
- form field/query param `userId`

---

## 2. Danh sách endpoint

| Method | URL | Mô tả |
| --- | --- | --- |
| POST | `/api/media/upload` | Upload một hoặc nhiều file |
| GET | `/api/media/my-uploads` | Lấy toàn bộ media post current user đã upload |
| DELETE | `/api/media?publicId=...` | Xóa một media theo query param |
| DELETE | `/api/media/{publicId}` | Xóa một media theo path |
| POST | `/api/media/delete-batch` | Xóa nhiều media cùng lúc |

---

## 3. Upload media

## 3.1. Method + URL

- `POST /api/media/upload`

### Content-Type

```http
multipart/form-data
```

## 3.2. Request qua gateway

Khi gọi qua gateway, frontend chỉ cần gửi file và access token.

Ví dụ với `fetch`:

```js
const formData = new FormData();
files.forEach((file) => formData.append('files', file));

const response = await fetch('http://localhost:8080/api/media/upload', {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${accessToken}`
  },
  body: formData
});
```

Lý do không cần tự gửi `userId`:

- gateway sẽ inject `X-User-Id`
- controller ưu tiên `X-User-Id` hơn `userId`

## 3.3. Request gọi trực tiếp service

Nếu test trực tiếp vào `http://localhost:8084`, có thể gửi thêm `userId` như form field hoặc query param.

Ví dụ:

```js
const formData = new FormData();
formData.append('userId', 'userA');
files.forEach((file) => formData.append('files', file));

await fetch('http://localhost:8084/api/media/upload', {
  method: 'POST',
  body: formData
});
```

## 3.4. Response upload

`201 Created`

```json
{
  "items": [
    {
      "publicId": "social-media/posts/userA/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/post.jpg",
      "mediaType": "IMAGE",
      "format": "jpg",
      "width": 1080,
      "height": 1080,
      "bytes": 348123
    }
  ]
}
```

### Field dùng để gửi sang service khác

Frontend thường cần giữ lại:

- `publicId`
- `mediaUrl`
- `mediaType`
- `width`
- `height`
- `bytes`

Ví dụ gửi tiếp sang social-service:

```json
{
  "content": "Hello social media",
  "visibility": "PUBLIC",
  "media": [
    {
      "publicId": "social-media/posts/userA/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/post.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1080,
      "bytes": 348123
    }
  ]
}
```

---

## 4. Upload rules và giới hạn file

Theo code hiện tại, media-service validate như sau.

## 4.1. Format hỗ trợ

Image:

- `jpg`
- `jpeg`
- `png`
- `webp`

Video:

- `mp4`
- `mov`

Nếu file không khớp content type hoặc extension hợp lệ, backend trả lỗi `400`.

Ví dụ lỗi:

```json
{
  "message": "Unsupported file type for 'file.exe'. Allowed formats: jpg, jpeg, png, webp, mp4, mov."
}
```

## 4.2. Số lượng file

- tổng số file tối đa: `5`
- nếu chỉ upload ảnh: tối đa `5` ảnh
- nếu chỉ upload video: tối đa `2` video
- nếu upload mixed: tối đa `3` ảnh và `1` video

## 4.3. Kích thước file

- ảnh tối đa: `5MB`
- video tối đa: `50MB`

Ví dụ lỗi:

```json
{
  "message": "Image 'photo.jpg' exceeds 5MB limit."
}
```

Hoặc:

```json
{
  "message": "Mixed uploads allow up to 3 images and 1 video."
}
```

## 4.4. Bắt buộc userId khi upload

Nếu thiếu cả `X-User-Id` và `userId`, backend trả:

```json
{
  "message": "userId is required to upload media."
}
```

---

## 5. Delete media

## 5.0. Lấy toàn bộ media post đã upload của current user

### Method + URL

- `GET /api/media/my-uploads`

Ví dụ qua gateway:

```http
GET http://localhost:8080/api/media/my-uploads
Authorization: Bearer <accessToken>
```

Nếu gọi trực tiếp local service, có thể truyền `X-User-Id` hoặc `userId`.

### Response

```json
{
  "items": [
    {
      "publicId": "social-media/posts/userA/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/post.jpg",
      "mediaType": "IMAGE",
      "format": "jpg",
      "width": 1080,
      "height": 1080,
      "bytes": 348123
    },
    {
      "publicId": "social-media/posts/userA/video001",
      "mediaUrl": "https://res.cloudinary.com/demo/video/upload/v1/post.mp4",
      "mediaType": "VIDEO",
      "format": "mp4",
      "width": 1080,
      "height": 1920,
      "bytes": 5423481
    }
  ]
}
```

### Phạm vi dữ liệu

Endpoint này hiện chỉ trả media nằm trong Cloudinary folder post của user:

- `social-media/posts/{userId}`

Nó không lấy media chat hay resource type khác.

## 5.1. Xóa một media bằng query param

### Method + URL

- `DELETE /api/media?publicId={publicId}`

Ví dụ:

```http
DELETE http://localhost:8080/api/media?publicId=social-media/posts/userA/abc123
Authorization: Bearer <accessToken>
```

Response:

```json
{
  "publicId": "social-media/posts/userA/abc123",
  "deleted": true,
  "message": "Media deleted successfully"
}
```

## 5.2. Xóa một media bằng path

### Method + URL

- `DELETE /api/media/{publicId}`

Ví dụ:

```http
DELETE http://localhost:8080/api/media/social-media/posts/userA/abc123
Authorization: Bearer <accessToken>
```

Response thành công tương tự.

Nếu không tìm thấy hoặc đã xóa rồi:

```json
{
  "publicId": "social-media/posts/userA/abc123",
  "deleted": false,
  "message": "Media not found or already deleted"
}
```

## 5.3. Xóa batch

### Method + URL

- `POST /api/media/delete-batch`

Headers:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Body:

```json
{
  "publicIds": [
    "social-media/posts/userA/abc123",
    "social-media/posts/userA/def456"
  ]
}
```

Response:

```json
{
  "requestedCount": 2,
  "deleted": [
    "social-media/posts/userA/abc123"
  ],
  "failed": [
    "social-media/posts/userA/def456"
  ]
}
```

## Validation lỗi cho batch delete

```json
{
  "message": "Validation failed",
  "errors": {
    "publicIds": "publicIds must not be empty"
  }
}
```

---

## 6. Error format

Media-service hiện trả lỗi khá đơn giản.

## Validation error

```json
{
  "message": "Validation failed",
  "errors": {
    "publicIds": "publicIds must not be empty"
  }
}
```

## Business error `400`

```json
{
  "message": "At least one file is required."
}
```

## Runtime error `500`

```json
{
  "message": "Failed to upload media to Cloudinary."
}
```

Frontend nên đọc tối thiểu field `message`.

---

## 7. Frontend usage

## 7.1. Flow upload cho post

1. User chọn ảnh/video.
2. Frontend gọi `POST /api/media/upload`.
3. Lấy `items` từ response.
4. Gửi metadata đó vào `POST /api/social/posts/create` hoặc `PUT /api/social/posts/update/{postId}`.

## 7.2. Flow upload cho chat attachment

1. User chọn file.
2. Frontend upload file sang media-service.
3. Lấy `publicId`, `mediaUrl`, `mediaType`.
4. Gửi tiếp vào STOMP `/app/chat.send` hoặc REST chat payload attachment.

Ví dụ mapping attachment cho chat:

```json
{
  "conversationId": "67ef2b4d9ab123456789abcd",
  "clientMessageId": "fe-msg-001",
  "messageType": "IMAGE",
  "content": "",
  "attachments": [
    {
      "publicId": "social-media/posts/userA/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/chat.jpg",
      "mediaType": "IMAGE"
    }
  ]
}
```

## 7.3. Ví dụ code upload nhiều file

```js
async function uploadMedia(files, accessToken) {
  const formData = new FormData();

  for (const file of files) {
    formData.append('files', file);
  }

  const response = await fetch('http://localhost:8080/api/media/upload', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`
    },
    body: formData
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Upload failed');
  }

  return response.json();
}
```

## 7.4. Ví dụ code delete batch

```js
async function deleteMediaBatch(publicIds, accessToken) {
  const response = await fetch('http://localhost:8080/api/media/delete-batch', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ publicIds })
  });

  return response.json();
}
```

---

## 8. Những điểm FE cần biết trước

1. Upload hiện đang hard-code Cloudinary folder theo post: `social-media/posts/{userId}`.
2. Upload hiện cũng hard-code tag theo post: `post,social-service,{userId}`.
3. Điều này có nghĩa là media-service hiện chưa tách riêng resource type cho story/chat/post.
4. Nếu sau này cần story hoặc chat folder riêng, backend nên mở rộng contract upload thêm `resourceType`.
5. Delete endpoint hiện không kiểm tra owner ở chính media-service; khi gọi qua gateway vẫn có auth, nhưng service bản thân chưa verify `publicId` có thuộc user hiện tại hay không.

---

## 9. Contract FE nên dùng ngay

Nếu chỉ lấy phần tối thiểu để tích hợp nhanh:

### Upload

- `POST /api/media/upload`

Request:

- `multipart/form-data`
- field `files` lặp lại cho từng file
- qua gateway: gửi `Authorization: Bearer <accessToken>`

### Delete single

- `DELETE /api/media?publicId={publicId}`

### Delete batch

- `POST /api/media/delete-batch`

Body:

```json
{
  "publicIds": ["social-media/posts/userA/abc123"]
}
```
