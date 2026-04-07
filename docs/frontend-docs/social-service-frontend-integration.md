# Social Service Frontend Integration

## 1. Tổng quan

`social-media-social-service` hiện xử lý 4 nhóm nghiệp vụ chính:

- Post
- Comment cho post
- Reaction cho post
- Friend request / friendship

Service này là REST-only, không có WebSocket riêng.

## Base URL

Khuyến nghị frontend gọi qua gateway:

- `http://localhost:8080/api/social`

Nếu gọi trực tiếp vào service:

- `http://localhost:8083/api/social`

## Authentication và header

Frontend nên gửi:

```http
Authorization: Bearer <accessToken>
```

Gateway sẽ validate JWT và inject xuống social-service:

- `X-User-Id`
- `X-Role`

Điều này có nghĩa là frontend không cần tự gửi `X-User-Id` khi gọi qua gateway. Nếu test trực tiếp vào service local thì mới cần tự set `X-User-Id`.

## Tích hợp media

Social-service không upload file nhị phân. Với post có ảnh/video, flow đúng là:

1. upload file sang media-service
2. lấy về `publicId`, `mediaUrl`, `mediaType`, `provider`, `width`, `height`, `bytes`
3. gửi metadata đó vào social-service khi tạo hoặc update post

---

## 2. Danh sách endpoint

| Method | URL | Mô tả |
| --- | --- | --- |
| POST | `/api/social/posts/create` | Tạo post mới |
| PUT | `/api/social/posts/update/{postId}` | Update post của chính mình |
| GET | `/api/social/posts/{postId}` | Lấy post theo id, hiện chỉ lấy được post của chính mình |
| GET | `/api/social/user/posts?page=0&size=10` | Lấy danh sách post của chính mình |
| GET | `/api/social/feed?page=0&size=10` | Lấy newsfeed |
| PUT | `/api/social/posts/{postId}/hide` | Ẩn post của chính mình |
| PUT | `/api/social/posts/{postId}/unhide` | Bỏ ẩn post của chính mình |
| DELETE | `/api/social/posts/{postId}` | Xóa hẳn post của chính mình |
| GET | `/api/social/posts/{postId}/comments` | Lấy danh sách comment của post |
| POST | `/api/social/posts/{postId}/comments` | Tạo comment cho post |
| PUT | `/api/social/posts/{postId}/comments/{commentId}` | Update comment của chính mình |
| DELETE | `/api/social/posts/{postId}/comments/{commentId}` | Xóa comment của chính mình |
| POST | `/api/social/posts/{postId}/react` | React vào post |
| DELETE | `/api/social/posts/{postId}/react` | Bỏ reaction khỏi post |
| GET | `/api/social/posts/{postId}/reaction-summary` | Lấy summary reaction của current user với post |
| POST | `/api/social/friends/requests/{targetUserId}` | Gửi lời mời kết bạn |
| PUT | `/api/social/friends/requests/{requesterId}/accept` | Chấp nhận lời mời |
| PUT | `/api/social/friends/requests/{requesterId}/reject` | Từ chối lời mời |
| DELETE | `/api/social/friends/requests/{targetUserId}/cancel` | Hủy lời mời đã gửi |
| DELETE | `/api/social/friends/{targetUserId}` | Hủy kết bạn |
| GET | `/api/social/friends?page=0&size=10` | Lấy danh sách bạn bè |
| GET | `/api/social/friends/requests?type=incoming&page=0&size=10` | Lấy danh sách request chờ xử lý |
| GET | `/api/social/friends/suggestions?page=0&size=10` | Lấy danh sách gợi ý kết bạn theo trang |
| GET | `/api/social/friends/relationship/{targetUserId}` | Kiểm tra trạng thái quan hệ |

---

## 3. Error format

Social-service hiện có 3 kiểu lỗi chính.

## Validation error

```json
{
  "message": "Validation failed",
  "errors": {
    "content": "Content is required",
    "visibility": "Visibility is required"
  }
}
```

## Business error dạng `400`

```json
{
  "message": "Invalid visibility. Allowed values: PUBLIC, FRIEND, PRIVATE"
}
```

## Not found dạng `404`

```json
{
  "message": "Post not found with id: 10"
}
```

Lưu ý:

- service hiện không trả cấu trúc lỗi thống nhất kiểu `status/error/timestamp`
- frontend nên đọc tối thiểu field `message`

---

## 4. Post APIs

## 4.1. Tạo post

### Method + URL

- `POST /api/social/posts/create`

### Request

Headers:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Body:

```json
{
  "content": "Hello social media",
  "visibility": "PUBLIC",
  "mood": "happy",
  "location": "Ha Noi",
  "media": [
    {
      "publicId": "social-media/posts/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/post.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1080,
      "bytes": 348123
    }
  ],
  "mediaUrls": []
}
```

### Rule thực tế

- `visibility`: bắt buộc
- post hợp lệ nếu có ít nhất một trong 2: `content` hoặc media
- `content` có thể để trống nếu có ảnh/video
- nếu `content` chỉ là khoảng trắng, backend sẽ chuẩn hóa thành `null`
- `visibility` chỉ nhận `PUBLIC`, `FRIEND`, `PRIVATE`
- `mood`: optional, tối đa 255 ký tự
- `location`: optional, tối đa 500 ký tự
- `media` là metadata sau khi upload qua media-service
- `mediaUrls` là field legacy, vẫn được backend support
- nếu `mediaType` để trống, backend sẽ tự suy luận từ `mediaUrl`

### Response

`201 Created`

```json
{
  "id": 101,
  "userId": "userA",
  "username": "nguyenvana",
  "fullName": "Nguyen Van A",
  "avatarUrl": "https://cdn.example.com/avatar-a.jpg",
  "content": "Hello social media",
  "visibility": "PUBLIC",
  "mood": "happy",
  "location": "Ha Noi",
  "postContext": "PROFILE",
  "communityId": null,
  "communityName": null,
  "media": [
    {
      "publicId": "social-media/posts/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/post.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1080,
      "bytes": 348123
    }
  ],
  "mediaUrls": [
    "https://res.cloudinary.com/demo/image/upload/v1/post.jpg"
  ],
  "totalReacts": 0,
  "commentCount": 0,
  "reactedByCurrentUser": false,
  "createdAt": "2026-04-06T10:00:00.000+00:00",
  "updatedAt": "2026-04-06T10:00:00.000+00:00"
}
```

Các API trả `PostResponse` hiện có thêm:

- `username`
- `fullName`
- `avatarUrl`
- `commentCount`
- `mood`
- `location`
- `postContext`: `PROFILE` | `COMMUNITY`
- `communityId`: có giá trị nếu là community post
- `communityName`: có giá trị nếu là community post

## 4.2. Update post

### Method + URL

- `PUT /api/social/posts/update/{postId}`

Body giống `create post`.

Behavior quan trọng:

- chỉ owner được update
- backend replace toàn bộ media list của post theo payload mới
- nếu media cũ bị remove khỏi payload mới, backend sẽ gọi media-service để xóa asset theo `publicId`
- nếu muốn giữ lại ảnh/video cũ, frontend phải gửi lại chúng trong request update
- backend hỗ trợ thay 1 ảnh bằng ảnh khác, nhưng theo kiểu gửi lại trạng thái media cuối cùng mong muốn

Ví dụ update đổi ảnh và cập nhật mood/location:

```json
{
  "content": "Da sua bai viet",
  "visibility": "PUBLIC",
  "mood": "focused",
  "location": "Da Nang",
  "media": [
    {
      "publicId": "social-media/posts/user123/new-image-1",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/new-image.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1080,
      "bytes": 298765
    }
  ],
  "mediaUrls": []
}
```

## 4.3. Get post by id

### Method + URL

- `GET /api/social/posts/{postId}`

### Rule thực tế

- owner luôn xem được post của mình nếu chưa bị delete
- post `PUBLIC` của người khác xem được
- post `FRIEND` xem được nếu current user là accepted friend
- community post xem được theo rule privacy của community

Frontend có thể dùng API này như API chi tiết post, không còn bị giới hạn owner-only như trước.

## 4.4. Lấy post của tôi

### Method + URL

- `GET /api/social/user/posts?page=0&size=10`

Response là `Spring Page<PostResponse>`:

```json
{
  "content": [
    {
      "id": 101,
      "userId": "userA",
      "username": "nguyenvana",
      "fullName": "Nguyen Van A",
      "avatarUrl": "https://cdn.example.com/avatar-a.jpg",
      "content": "Hello social media",
      "visibility": "PUBLIC",
      "mood": "happy",
      "location": "Ha Noi",
      "postContext": "PROFILE",
      "communityId": null,
      "communityName": null,
      "media": [],
      "mediaUrls": [],
      "totalReacts": 0,
      "commentCount": 2,
      "reactedByCurrentUser": false,
      "createdAt": "2026-04-06T10:00:00.000+00:00",
      "updatedAt": "2026-04-06T10:00:00.000+00:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "size": 10,
  "number": 0,
  "first": true,
  "numberOfElements": 1,
  "empty": false
}
```

## 4.5. Lấy feed

### Method + URL

- `GET /api/social/feed?page=0&size=10`

### Feed logic hiện tại

Feed lấy:

- toàn bộ post không bị delete của current user
- post `PUBLIC` của người khác
- post `FRIEND` và `PUBLIC` của accepted friends
- post từ các community mà current user đang `APPROVED`
- không trả post đã bị hide/delete

Nếu user không có friend accepted nào, feed chỉ trả post `PUBLIC` của người khác.

Nếu user không có friend accepted nào, feed vẫn trả:

- post của chính current user
- post `PUBLIC` của người khác

Nếu user đã tham gia community, feed còn có thể trả:

- post có `postContext = COMMUNITY`
- kèm `communityId` và `communityName` để frontend render card cộng đồng

## 4.6. Hide / Unhide / Delete post

### Hide post

- `PUT /api/social/posts/{postId}/hide`
- response: `204 No Content`

### Unhide post

- `PUT /api/social/posts/{postId}/unhide`
- response: `204 No Content`

### Delete post permanently

- `DELETE /api/social/posts/{postId}`
- response: `204 No Content`

Behavior:

- cả 3 API chỉ cho owner dùng
- `hide` là soft delete bằng `isDeleted = true`
- `delete` là hard delete DB và backend sẽ gọi media-service xóa asset theo `publicId`

---

## 5. Comment APIs

## 5.1. Lấy danh sách comment của post

### Method + URL

- `GET /api/social/posts/{postId}/comments`

### Response

```json
[
  {
    "id": 55,
    "userId": "userB",
    "username": "tranthib",
    "fullName": "Tran Thi B",
    "avatarUrl": "https://cdn.example.com/avatar-b.jpg",
    "content": "Bai viet hay qua",
    "postId": 101,
    "deleted": false,
    "createdAt": "2026-04-06T10:05:00.000+00:00",
    "updatedAt": "2026-04-06T10:05:00.000+00:00"
  },
  {
    "id": 56,
    "userId": "userC",
    "content": "Dong y",
    "postId": 101,
    "deleted": false,
    "createdAt": "2026-04-06T10:06:00.000+00:00",
    "updatedAt": "2026-04-06T10:06:00.000+00:00"
  }
]
```

Behavior:

- chỉ trả comment chưa bị xóa
- sort theo `createdAt ASC`
- nếu post không tồn tại, trả `404`
- nếu post đã bị xóa/hide, trả `400` với message `Post was banned`
- mỗi comment có thêm `username`, `fullName`, `avatarUrl` của người comment

## 5.2. Tạo comment

### Method + URL

- `POST /api/social/posts/{postId}/comments`

Body:

```json
{
  "content": "Bai viet hay qua"
}
```

Validation:

- `content` bắt buộc
- tối đa 1000 ký tự

Response:

```json
{
  "id": 55,
  "userId": "userB",
  "content": "Bai viet hay qua",
  "postId": 101,
  "deleted": false,
  "createdAt": "2026-04-06T10:05:00.000+00:00",
  "updatedAt": "2026-04-06T10:05:00.000+00:00"
}
```

## 5.3. Update comment

### Method + URL

- `PUT /api/social/posts/{postId}/comments/{commentId}`

Body:

```json
{
  "content": "Da sua comment"
}
```

Behavior:

- chỉ owner comment được update
- backend hiện không check `postId` trong service update, mà xác thực theo `commentId + userId`

## 5.4. Delete comment

### Method + URL

- `DELETE /api/social/posts/{postId}/comments/{commentId}`

Response:

```http
204 No Content
```

Behavior:

- chỉ owner comment được delete
- là hard delete bản ghi comment

---

## 6. Reaction APIs

## Reaction type hỗ trợ

- `LIKE`
- `LOVE`
- `HAHA`
- `WOW`
- `SAD`
- `ANGRY`

Nếu không gửi body hoặc không gửi `type`, backend mặc định dùng `LIKE`.

## 6.1. React to post

### Method + URL

- `POST /api/social/posts/{postId}/react`

Body:

```json
{
  "type": "LOVE"
}
```

Hoặc có thể gọi không có body để mặc định `LIKE`.

Response:

```json
{
  "postId": 101,
  "totalReacts": 5,
  "reactedByCurrentUser": true,
  "reactionType": "LOVE"
}
```

Behavior thực tế:

- nếu chưa react thì tạo reaction mới và tăng `totalReacts`
- nếu đã react cùng type rồi thì không tăng count
- nếu đã react nhưng đổi type, backend chỉ update type, không tăng count

Điều này làm cho API mang tính idempotent ở mức business.

## 6.2. Remove reaction

### Method + URL

- `DELETE /api/social/posts/{postId}/react`

Response:

```json
{
  "postId": 101,
  "totalReacts": 4,
  "reactedByCurrentUser": false,
  "reactionType": null
}
```

Behavior:

- nếu current user chưa react thì backend bỏ qua, không lỗi
- count không bị âm

## 6.3. Reaction summary

### Method + URL

- `GET /api/social/posts/{postId}/reaction-summary`

Response:

```json
{
  "postId": 101,
  "totalReacts": 4,
  "reactedByCurrentUser": true,
  "reactionType": "LIKE"
}
```

---

## 7. Friend APIs

Các API danh sách bạn bè và request hiện có thêm dữ liệu profile để render UI:

- danh sách bạn bè: `username`, `fullName`, `avatarUrl`
- pending request: `requesterUsername`, `requesterFullName`, `requesterAvatarUrl`, `targetUsername`, `targetFullName`, `targetAvatarUrl`

## 7.1. Gợi ý kết bạn

### Method + URL

- `GET /api/social/friends/suggestions?page=0&size=10`

### Cách dùng cho infinite scroll

- lần đầu gọi `page=0&size=10`
- khi người dùng lướt xuống, gọi tiếp `page=1&size=10`, rồi `page=2&size=10`...
- dừng gọi thêm khi response có `last = true`

### Response

```json
{
  "content": [
    {
      "userId": "userB",
      "username": "tranthib",
      "fullName": "Tran Thi B",
      "avatarUrl": "https://cdn.example.com/avatar-b.jpg",
      "relationshipStatus": "NOT_FRIEND"
    },
    {
      "userId": "userC",
      "username": "lec",
      "fullName": "Le C",
      "avatarUrl": "https://cdn.example.com/avatar-c.jpg",
      "relationshipStatus": "NOT_FRIEND"
    }
  ],
  "totalElements": 42,
  "totalPages": 5,
  "size": 10,
  "number": 0,
  "first": true,
  "last": false,
  "empty": false
}
```

Behavior:

- không trả current user
- không trả user đã là bạn
- không trả user đang có request `PENDING`
- không trả user đang ở trạng thái `BLOCKED`
- thứ tự hiện tại ổn định theo `username ASC` để tránh trùng/lệch nhiều khi load thêm

### Lưu ý

- sau khi gửi lời mời thành công, frontend nên remove user đó khỏi danh sách suggestion local để tránh hiện lại ngay

Social-service đang dùng model một relationship record cho mỗi cặp user với `pairKey`. Điều này làm cho frontend có thể dựa khá chắc vào trạng thái quan hệ trả về.

## Trạng thái FE nên dùng

- `NOT_FRIEND`
- `PENDING_SENT`
- `PENDING_RECEIVED`
- `FRIEND`
- `BLOCKED`
- `CANCELLED`
- `REJECTED`

Trong UI, thường chỉ cần map về 4 trạng thái chính:

- `NOT_FRIEND`
- `PENDING_SENT`
- `PENDING_RECEIVED`
- `FRIEND`

## 7.2. Gửi lời mời kết bạn

### Method + URL

- `POST /api/social/friends/requests/{targetUserId}`

Response:

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "PENDING_SENT",
  "message": "Friend request sent successfully"
}
```

Behavior rất quan trọng:

- nếu trước đó chưa có quan hệ, backend tạo `PENDING`
- nếu đã là bạn, backend trả `FRIEND`
- nếu current user đã gửi request rồi, backend vẫn trả `PENDING_SENT`
- nếu phía bên kia đã gửi request trước đó, backend sẽ auto-accept và trả `FRIEND`

Ví dụ auto-accept:

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "FRIEND",
  "message": "Friend request accepted"
}
```

## 7.3. Chấp nhận lời mời

### Method + URL

- `PUT /api/social/friends/requests/{requesterId}/accept`

Response:

```json
{
  "userId": "userB",
  "targetUserId": "userA",
  "status": "FRIEND",
  "message": "Friend request accepted"
}
```

## 7.3. Từ chối lời mời

### Method + URL

- `PUT /api/social/friends/requests/{requesterId}/reject`

Response:

```json
{
  "userId": "userB",
  "targetUserId": "userA",
  "status": "REJECTED",
  "message": "Friend request rejected"
}
```

## 7.4. Hủy lời mời đã gửi

### Method + URL

- `DELETE /api/social/friends/requests/{targetUserId}/cancel`

Response:

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "CANCELLED",
  "message": "Friend request cancelled successfully"
}
```

## 7.5. Unfriend

### Method + URL

- `DELETE /api/social/friends/{targetUserId}`

Response:

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "NOT_FRIEND",
  "message": "Unfriended successfully"
}
```

## 7.6. Lấy danh sách bạn bè

### Method + URL

- `GET /api/social/friends?page=0&size=10`

Response là `Page<FriendListItemResponse>`:

```json
{
  "content": [
    {
      "otherUserId": "userB",
      "status": "FRIEND",
      "requestedAt": "2026-04-06T08:00:00.000+00:00",
      "respondedAt": "2026-04-06T08:10:00.000+00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 10,
  "number": 0,
  "first": true,
  "last": true,
  "empty": false
}
```

## 7.8. Lấy request đang chờ

### Method + URL

- `GET /api/social/friends/requests?type=incoming&page=0&size=10`
- `GET /api/social/friends/requests?type=outgoing&page=0&size=10`

Response:

```json
{
  "content": [
    {
      "requesterId": "userA",
      "targetUserId": "userB",
      "status": "PENDING",
      "requestedAt": "2026-04-06T08:00:00.000+00:00",
      "updatedAt": "2026-04-06T08:00:00.000+00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 10,
  "number": 0,
  "first": true,
  "last": true,
  "empty": false
}
```

Lưu ý:

- field `status` trong response hiện lấy từ entity gốc nên thường là `PENDING`
- phân biệt incoming hay outgoing dựa vào query param `type`, không chỉ dựa vào field `status`

## 7.8. Lấy trạng thái quan hệ

### Method + URL

- `GET /api/social/friends/relationship/{targetUserId}`

Response:

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "PENDING_SENT"
}
```

Đây là API frontend nên gọi để quyết định render nút:

- `Add Friend`
- `Cancel Request`
- `Accept Request`
- `Friends`

---

## 8. DTO mẫu FE nên dùng

## PostCreateRequest / PostUpdateRequest

```json
{
  "content": "Hello social media",
  "visibility": "PUBLIC",
  "mood": "happy",
  "location": "Ha Noi",
  "media": [
    {
      "publicId": "social-media/posts/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/post.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1080,
      "bytes": 348123
    }
  ],
  "mediaUrls": []
}
```

## PostResponse

```json
{
  "id": 101,
  "userId": "userA",
  "username": "nguyenvana",
  "fullName": "Nguyen Van A",
  "avatarUrl": "https://cdn.example.com/avatar-a.jpg",
  "content": "Hello social media",
  "visibility": "PUBLIC",
  "mood": "happy",
  "location": "Ha Noi",
  "postContext": "PROFILE",
  "communityId": null,
  "communityName": null,
  "media": [
    {
      "publicId": "social-media/posts/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/post.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1080,
      "bytes": 348123
    }
  ],
  "mediaUrls": [
    "https://res.cloudinary.com/demo/image/upload/v1/post.jpg"
  ],
  "totalReacts": 0,
  "commentCount": 0,
  "reactedByCurrentUser": false,
  "createdAt": "2026-04-06T10:00:00.000+00:00",
  "updatedAt": "2026-04-06T10:00:00.000+00:00"
}
```

## CommentCreateRequest / CommentUpdateRequest

```json
{
  "content": "Bai viet hay qua"
}
```

## CommentResponse

```json
{
  "id": 55,
  "userId": "userB",
  "content": "Bai viet hay qua",
  "postId": 101,
  "deleted": false,
  "createdAt": "2026-04-06T10:05:00.000+00:00",
  "updatedAt": "2026-04-06T10:05:00.000+00:00"
}
```

## ReactionsRequest

```json
{
  "type": "LIKE"
}
```

## ReactionResponse

```json
{
  "postId": 101,
  "totalReacts": 4,
  "reactedByCurrentUser": true,
  "reactionType": "LIKE"
}
```

## FriendActionResponse

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "PENDING_SENT",
  "message": "Friend request sent successfully"
}
```

## RelationshipStatusResponse

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "FRIEND"
}
```

---

## 9. Frontend usage

## 9.1. Flow tạo post có media

1. Upload file sang media-service.
2. Nhận về metadata Cloudinary.
3. Gọi `POST /api/social/posts/create` với `content`, `visibility`, `media`.
4. Update UI bằng `PostResponse` trả về.

Lưu ý:

- có thể tạo post chỉ với media, không bắt buộc `content`
- nếu gửi cả `mood` và `location`, backend sẽ lưu và trả lại trong `PostResponse`

Ví dụ:

```js
const mediaItems = uploadResult.items;

const response = await fetch('http://localhost:8080/api/social/posts/create', {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    content: 'Hello social media',
    visibility: 'PUBLIC',
    mood: 'happy',
    location: 'Ha Noi',
    media: mediaItems,
    mediaUrls: []
  })
});
```

## 9.2. Flow update post và thay ảnh

1. Nếu user chọn ảnh/video mới, upload file mới sang media-service trước.
2. Giữ lại danh sách media cũ mà user vẫn muốn hiển thị.
3. Ghép danh sách media cuối cùng mong muốn.
4. Gọi `PUT /api/social/posts/update/{postId}` với toàn bộ danh sách media cuối cùng.

Rule rất quan trọng:

- backend không patch từng ảnh riêng lẻ
- backend replace toàn bộ media list của post
- ảnh/video nào không còn xuất hiện trong payload update sẽ bị coi là đã xóa

Ví dụ:

```js
await fetch(`http://localhost:8080/api/social/posts/update/${postId}`, {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    content: '',
    visibility: 'PUBLIC',
    mood: 'focused',
    location: 'Da Nang',
    media: finalMediaItems,
    mediaUrls: []
  })
});
```

## 9.3. Flow load feed

1. Gọi `GET /api/social/feed?page=0&size=10`
2. Render `content` của Spring Page
3. Khi scroll tiếp, tăng `page`

Lưu ý:

- feed hiện đã trả `username`, `fullName`, `avatarUrl`
- nếu item có `postContext = COMMUNITY`, frontend nên dùng thêm `communityName` để render nguồn bài viết

## 9.4. Flow reaction

Khuyến nghị optimistic UI:

1. user bấm react
2. update local `reactedByCurrentUser` và `totalReacts`
3. gọi `POST /posts/{postId}/react`
4. sync lại state từ `ReactionResponse`

Tương tự khi bỏ react với `DELETE /posts/{postId}/react`.

## 9.5. Flow friend button

Frontend nên dùng `GET /friends/relationship/{targetUserId}` để quyết định nút hiện ra:

- `NOT_FRIEND` -> hiện `Add Friend`
- `PENDING_SENT` -> hiện `Cancel Request`
- `PENDING_RECEIVED` -> hiện `Accept` và `Reject`
- `FRIEND` -> hiện `Friends` hoặc `Unfriend`

## 9.6. Những điểm FE cần biết trước

1. Post hiện hợp lệ nếu có ít nhất một trong `content` hoặc media.
2. `GET /posts/{postId}` có thể dùng như API chi tiết post theo rule visibility hiện tại.
3. Comment list hiện trả toàn bộ comment chưa xóa của post, chưa có pagination.
4. Reaction chỉ áp dụng cho post, chưa có reaction cho comment.
5. Feed hiện có bao gồm post của current user và post community đã tham gia.
6. Request kết bạn có thể auto-accept nếu phía bên kia đã gửi trước đó.
7. Nếu FE update media của post, phải gửi lại toàn bộ media list cuối cùng mong muốn.

---

## 10. Contract FE nên dùng ngay

Nếu chỉ lấy phần tối thiểu để tích hợp nhanh:

### Post

- `POST /api/social/posts/create`
- `PUT /api/social/posts/update/{postId}`
- `GET /api/social/user/posts?page=&size=`
- `GET /api/social/feed?page=&size=`
- `PUT /api/social/posts/{postId}/hide`
- `PUT /api/social/posts/{postId}/unhide`
- `DELETE /api/social/posts/{postId}`

### Comment

- `GET /api/social/posts/{postId}/comments`
- `POST /api/social/posts/{postId}/comments`
- `PUT /api/social/posts/{postId}/comments/{commentId}`
- `DELETE /api/social/posts/{postId}/comments/{commentId}`

### Reaction

- `POST /api/social/posts/{postId}/react`
- `DELETE /api/social/posts/{postId}/react`
- `GET /api/social/posts/{postId}/reaction-summary`

### Friend

- `POST /api/social/friends/requests/{targetUserId}`
- `PUT /api/social/friends/requests/{requesterId}/accept`
- `PUT /api/social/friends/requests/{requesterId}/reject`
- `DELETE /api/social/friends/requests/{targetUserId}/cancel`
- `DELETE /api/social/friends/{targetUserId}`
- `GET /api/social/friends`
- `GET /api/social/friends/requests?type=incoming|outgoing`
- `GET /api/social/friends/relationship/{targetUserId}`
