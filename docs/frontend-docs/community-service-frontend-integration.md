# Community Frontend Integration

## 1. Tổng quan

Community hiện nằm trong `social-media-social-service`, không phải service riêng.

Frontend nên hiểu community là một module con của social domain, dùng chung:

- gateway auth hiện tại
- `Authorization: Bearer <accessToken>`
- media upload flow qua media-service
- profile enrichment từ user-service ở response backend

Hiện backend đã support:

- tạo community
- xem community detail
- join public / gửi request vào private community
- leave community
- delete community
- update cover
- update privacy
- xem danh sách member
- xem pending requests
- approve / reject pending request
- lấy danh sách community của tôi
- discover community
- tạo community post
- lấy feed bài viết của community

Hiện backend chưa support:

- update name / description community riêng
- transfer ownership
- moderator flow đầy đủ
- notification realtime cho community
- endpoint chi tiết riêng cho community post ngoài `GET /api/social/posts/{postId}`

## Base URL

Khuyến nghị gọi qua gateway:

- `http://localhost:8080/api/social/communities`

Nếu gọi trực tiếp vào service:

- `http://localhost:8083/api/social/communities`

---

## 2. Authentication và header

Frontend nên gửi:

```http
Authorization: Bearer <accessToken>
```

Gateway sẽ validate JWT và inject xuống social-service:

- `X-User-Id`
- `X-Role`

Frontend không cần tự gửi `X-User-Id` nếu gọi qua gateway.

---

## 3. Media flow cho cover và community post

Community không upload file nhị phân trực tiếp qua social-service.

Flow đúng là:

1. upload file sang media-service
2. lấy `publicId`, `mediaUrl`, `mediaType`, `provider`, `width`, `height`, `bytes`
3. dùng `coverUrl` cho community cover hoặc truyền `media` vào community post

Lưu ý:

- API update cover hiện chỉ nhận `coverUrl`
- API tạo community post nhận cùng shape `media` như post thường

---

## 4. Community APIs

## 4.1. Tạo community

### Method + URL

- `POST /api/social/communities`

### Request

```json
{
  "name": "Java Spring Vietnam",
  "description": "Noi chia se ve Spring Boot va microservices",
  "coverUrl": "https://cdn.example.com/community-cover.jpg",
  "privacy": "PUBLIC"
}
```

### Validation

- `name` bắt buộc
- `name` tối đa 150 ký tự
- `description` tối đa 2000 ký tự
- `privacy` bắt buộc, chỉ nhận `PUBLIC` hoặc `PRIVATE`

### Response

```json
{
  "id": 12,
  "name": "Java Spring Vietnam",
  "description": "Noi chia se ve Spring Boot va microservices",
  "coverUrl": "https://cdn.example.com/community-cover.jpg",
  "privacy": "PUBLIC",
  "memberCount": 1,
  "createdBy": "userA",
  "createdByUsername": "nguyenvana",
  "createdByFullName": "Nguyen Van A",
  "createdByAvatarUrl": "https://cdn.example.com/a.jpg",
  "myRole": "ADMIN",
  "myStatus": "APPROVED",
  "member": true,
  "canManage": true,
  "createdAt": "2026-04-07T08:00:00.000+00:00",
  "updatedAt": "2026-04-07T08:00:00.000+00:00"
}
```

Lưu ý:

- response Java field là `isMember`, nhưng JSON từ Jackson thường ra `member`
- frontend nên tolerant với cả `isMember` và `member` nếu cần an toàn khi map

## 4.2. Community detail

### Method + URL

- `GET /api/social/communities/{communityId}`

Response cùng shape `CommunityResponse`.

Field FE nên dùng trực tiếp:

- `myRole`
- `myStatus`
- `canManage`
- `privacy`
- `memberCount`

## 4.3. Join community

### Method + URL

- `POST /api/social/communities/{communityId}/join`

### Behavior

- community `PUBLIC` -> trả community detail với `myStatus = APPROVED`
- community `PRIVATE` -> trả community detail với `myStatus = PENDING`
- nếu user đã `APPROVED` hoặc `PENDING` trước đó -> backend trả `400`

## 4.4. Leave community

### Method + URL

- `POST /api/social/communities/{communityId}/leave`

### Response

- `204 No Content`

Behavior:

- member thường có thể leave
- `ADMIN` hiện không được leave

## 4.5. Delete community

### Method + URL

- `DELETE /api/social/communities/{communityId}`

### Response

- `204 No Content`

Behavior:

- chỉ `ADMIN` approved được delete
- khi delete, community post cũng bị soft delete để không còn hiện ở community feed

## 4.6. Update cover

### Method + URL

- `PUT /api/social/communities/{communityId}/cover`

Body:

```json
{
  "coverUrl": "https://cdn.example.com/new-community-cover.jpg"
}
```

Response: `CommunityResponse`

## 4.7. Update privacy

### Method + URL

- `PUT /api/social/communities/{communityId}/privacy`

Body:

```json
{
  "privacy": "PRIVATE"
}
```

Response: `CommunityResponse`

---

## 5. List APIs

## 5.1. My communities

### Method + URL

- `GET /api/social/communities/mine`

### Response

```json
{
  "owned": [
    {
      "id": 12,
      "name": "Java Spring Vietnam",
      "coverUrl": "https://cdn.example.com/community-cover.jpg",
      "privacy": "PUBLIC",
      "memberCount": 128,
      "myRole": "ADMIN",
      "myStatus": "APPROVED"
    }
  ],
  "joined": [
    {
      "id": 13,
      "name": "Spring Cloud SEA",
      "coverUrl": "https://cdn.example.com/community-cover-2.jpg",
      "privacy": "PRIVATE",
      "memberCount": 45,
      "myRole": "MEMBER",
      "myStatus": "APPROVED"
    }
  ]
}
```

## 5.2. Discover communities

### Method + URL

- `GET /api/social/communities/discover?page=0&size=10`

### Response

`Spring Page<CommunityOverviewResponse>`

Behavior:

- loại community user đang `APPROVED` hoặc `PENDING`
- sort ưu tiên theo `memberCount DESC`

---

## 6. Member và request APIs

## 6.1. Lấy member list

### Method + URL

- `GET /api/social/communities/{communityId}/members?page=0&size=20`

### Response

`Spring Page<CommunityMemberResponse>`

Ví dụ `content` item:

```json
{
  "communityId": 12,
  "userId": "userB",
  "role": "MEMBER",
  "status": "APPROVED",
  "joinedAt": "2026-04-07T09:00:00.000+00:00",
  "username": "tranthib",
  "fullName": "Tran Thi B",
  "avatarUrl": "https://cdn.example.com/b.jpg"
}
```

Behavior:

- community `PUBLIC`: ai đã auth cũng xem được member list
- community `PRIVATE`: chỉ member `APPROVED` mới xem được

## 6.2. Lấy pending requests

### Method + URL

- `GET /api/social/communities/{communityId}/requests?page=0&size=20`

### Response

`Spring Page<CommunityJoinRequestResponse>`

Ví dụ `content` item:

```json
{
  "communityId": 12,
  "userId": "userC",
  "status": "PENDING",
  "requestedAt": "2026-04-07T10:00:00.000+00:00",
  "updatedAt": "2026-04-07T10:00:00.000+00:00",
  "username": "levanc",
  "fullName": "Le Van C",
  "avatarUrl": "https://cdn.example.com/c.jpg"
}
```

Behavior:

- chỉ `ADMIN` approved mới xem được

## 6.3. Approve request

### Method + URL

- `PUT /api/social/communities/{communityId}/requests/{targetUserId}/approve`

### Response

```json
{
  "communityId": 12,
  "userId": "userC",
  "status": "APPROVED",
  "message": "Community request approved successfully"
}
```

## 6.4. Reject request

### Method + URL

- `PUT /api/social/communities/{communityId}/requests/{targetUserId}/reject`

### Response

```json
{
  "communityId": 12,
  "userId": "userC",
  "status": "REJECTED",
  "message": "Community request rejected successfully"
}
```

---

## 7. Community post APIs

## 7.1. Tạo community post

### Method + URL

- `POST /api/social/communities/{communityId}/posts`

### Request

Body cùng shape `PostCreateRequest` hiện tại:

```json
{
  "content": "Chao mung moi nguoi den voi community",
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
  ],
  "mediaUrls": []
}
```

Lưu ý rất quan trọng:

- backend hiện vẫn reuse `PostCreateRequest`, nên `content` và `visibility` vẫn đang là field bắt buộc
- dù design mong muốn visibility phụ thuộc community, implementation hiện tại vẫn nhận `visibility` từ request
- frontend nên gửi `visibility = PUBLIC` để tương thích implementation hiện tại cho đến khi backend chuẩn hóa rule nội bộ

Behavior:

- chỉ member `APPROVED` được tạo post
- media flow vẫn qua media-service trước rồi mới gửi metadata vào đây

## 7.2. Lấy community posts

### Method + URL

- `GET /api/social/communities/{communityId}/posts?page=0&size=10`

### Response

`Spring Page<PostResponse>`

Behavior:

- community `PUBLIC`: user đã auth xem được
- community `PRIVATE`: chỉ member `APPROVED` xem được
- chỉ trả post có `postContext = COMMUNITY`
- community post không còn lọt vào `GET /api/social/feed` hoặc `GET /api/social/user/posts`

## 7.3. Comment và reaction cho community post

Frontend vẫn dùng endpoint post cũ:

- `GET /api/social/posts/{postId}/comments`
- `POST /api/social/posts/{postId}/comments`
- `PUT /api/social/posts/{postId}/comments/{commentId}`
- `DELETE /api/social/posts/{postId}/comments/{commentId}`
- `POST /api/social/posts/{postId}/react`
- `DELETE /api/social/posts/{postId}/react`
- `GET /api/social/posts/{postId}/reaction-summary`

Nhưng hiện backend đã thêm permission check cho community post:

- private community: user không phải member `APPROVED` sẽ bị chặn

---

## 8. Error format

Community hiện vẫn dùng error style chung của social-service:

## Validation error

```json
{
  "message": "Validation failed",
  "errors": {
    "name": "Name is required"
  }
}
```

## Business error

```json
{
  "message": "Only ADMIN can manage this community"
}
```

## Not found

```json
{
  "message": "Community not found with id: 12"
}
```

Frontend nên đọc tối thiểu field `message`.

---

## 9. Điều frontend cần biết ngay

1. `CommunityResponse` có thể serialize field boolean thành `member` thay vì `isMember` tùy Jackson naming, nên FE nên map tolerant.
2. `discover` hiện loại community user đang `APPROVED` hoặc `PENDING`, nhưng chưa loại các trạng thái lịch sử như `LEFT` hay `REJECTED`.
3. `create community post` hiện vẫn reuse `PostCreateRequest`, nên `content` và `visibility` còn là required.
4. Community chưa có endpoint update `name/description` riêng.
5. Chưa có notification hoặc realtime event cho community.
6. Private community đã chặn member list, comment, reaction, và community feed nếu user chưa `APPROVED`.

---

## 10. Contract tối thiểu FE nên dùng

Nếu cần tích hợp nhanh, frontend có thể đi theo flow này:

1. `POST /api/social/communities`
2. `GET /api/social/communities/mine`
3. `GET /api/social/communities/discover?page=0&size=10`
4. `GET /api/social/communities/{communityId}`
5. `POST /api/social/communities/{communityId}/join`
6. `GET /api/social/communities/{communityId}/members?page=0&size=20`
7. `GET /api/social/communities/{communityId}/requests?page=0&size=20`
8. `PUT /api/social/communities/{communityId}/requests/{targetUserId}/approve`
9. `PUT /api/social/communities/{communityId}/requests/{targetUserId}/reject`
10. `GET /api/social/communities/{communityId}/posts?page=0&size=10`
11. `POST /api/social/communities/{communityId}/posts`