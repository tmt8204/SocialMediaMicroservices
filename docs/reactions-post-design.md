1) Hiện trạng sau khi đọc code
social-service
ReactionService hiện đã có 3 nghiệp vụ chính:
- `reactToPost(String userId, Long postId)`
- `removeReaction(String userId, Long postId)`
- `getReactionSummary(String userId, Long postId)`

Đang kiểm tra:
- post có tồn tại hay không
- post có bị xoá mềm hay không
- user hiện tại đã react vào post chưa

ReactionsEntity hiện lưu:
userId
type
post

ReactionsRepository hiện có:
- `findByUserIdAndPostId(String userId, Long postId)`
- `existsByUserIdAndPostId(String userId, Long postId)`
- `deleteByUserIdAndPostId(String userId, Long postId)`
- `countByPostId(Long postId)`

=> Nghĩa là phần reactions đã đủ flow cơ bản cho frontend: react, bỏ react và lấy summary.

2) Mục tiêu
Chỉ cần lưu và trả về:
- tổng số react của post
- user hiện tại đã react hay chưa

Không lưu và không trả thống kê riêng theo từng loại như:
- LIKE bao nhiêu
- LOVE bao nhiêu
- HAHA bao nhiêu

Mỗi user chỉ được react 1 lần trên 1 post.
Nếu bỏ react thì tổng số react giảm đi 1.

3) Kiến trúc đề xuất
Phương án khuyến nghị

Đơn giản hóa nghiệp vụ theo kiểu `react / unreact`, không làm reaction breakdown theo loại.

Thiết kế nên gồm 2 phần:
- bảng `reactions`: lưu quan hệ user nào đã react vào post nào
- bảng `posts`: lưu sẵn `total_reacts` để đọc feed nhanh

Flow đề xuất
Client bấm react ở frontend.
social-service kiểm tra user đã có reaction trên post đó chưa.

- nếu chưa có:
    tạo record trong `reactions`
    tăng `posts.total_reacts` lên 1

- nếu đã có:
    xóa record khỏi `reactions`
    giảm `posts.total_reacts` đi 1

API chỉ cần trả về:
postId
totalReacts
reactedByCurrentUser

Cách này phù hợp hơn với nhu cầu hiện tại vì:
- đơn giản cho backend
- dễ render feed
- không phải group theo từng loại reaction
- ít query nặng hơn

4) Thay đổi cần làm ở social-service
4.1. PostEntity
Nên thêm field:

```java
@Column(name = "total_reacts", columnDefinition = "bigint default 0")
private long totalReacts = 0;
```

Mục đích:
- lấy feed nhanh hơn
- không cần count lại từ bảng `reactions` mỗi lần load post list

4.2. ReactionsEntity
Với yêu cầu hiện tại, có 2 hướng:

Hướng clean nhất:
- bỏ logic phân loại `LIKE/LOVE/...`
- mỗi record chỉ thể hiện user đã react vào post

Hướng ít sửa code hơn:
- vẫn giữ field `type` trong entity hiện tại
- nhưng business không dùng để thống kê hay response ra frontend

Bắt buộc nên thêm unique constraint:
- 1 user chỉ có 1 reaction trên 1 post

Ví dụ:

```java
@UniqueConstraint(name = "uk_reaction_user_post", columnNames = {"user_id", "post_id"})
```

4.3. ReactionsRepository
Đề xuất thêm:

```java
boolean existsByUserIdAndPostId(String userId, Long postId);
void deleteByUserIdAndPostId(String userId, Long postId);
long countByPostId(Long postId);
```

Lưu ý:
- `countByPostId` chỉ dùng để đồng bộ hoặc backfill nếu cần
- luồng chính nên đọc từ `posts.total_reacts`

4.4. ReactionService
Nên tách thành các nghiệp vụ rõ ràng:

```java
reactToPost(String userId, Long postId)
removeReaction(String userId, Long postId)
getReactionSummary(String userId, Long postId)
```

Mọi thao tác tăng/giảm `total_reacts` phải chạy trong `@Transactional`.

5) API nên có
React post

POST /api/social/posts/{postId}/react

Response:

```json
{
  "postId": 15,
  "totalReacts": 12,
  "reactedByCurrentUser": true
}
```

Bỏ react

DELETE /api/social/posts/{postId}/react

Response:

```json
{
  "postId": 15,
  "totalReacts": 11,
  "reactedByCurrentUser": false
}
```

Lấy summary

GET /api/social/posts/{postId}/reaction-summary

Response:

```json
{
  "postId": 15,
  "totalReacts": 12,
  "reactedByCurrentUser": true
}
```

6) Tích hợp với PostResponse
Nên bổ sung vào `PostResponse`:

```java
private long totalReacts;
private boolean reactedByCurrentUser;
```

Không cần thêm:
- `reactionCounts`
- `Map<ReactionType, Long>`
- thống kê riêng từng loại reaction

Như vậy feed chỉ cần một response là đủ để render:
- tổng số react
- nút react đang active hay chưa

7) Rule nghiệp vụ nên chốt
- Mỗi user chỉ react 1 lần trên 1 post.
- Không cho react vào post đã bị `isDeleted = true`.
- `total_reacts` không bao giờ được âm.
- Khi user unreact thì phải giảm `total_reacts` tương ứng.
- Nếu có lỗi lệch dữ liệu, có thể dùng `countByPostId` để rebuild lại `total_reacts`.

8) Kế hoạch triển khai đề xuất
Phase 1 — Hoàn thiện model
- thêm `total_reacts` vào bảng `posts`
- thêm unique constraint cho `reactions`

Phase 2 — Mở API
- `POST /api/social/posts/{postId}/react`
- `DELETE /api/social/posts/{postId}/react`
- `GET /api/social/posts/{postId}/reaction-summary`

Phase 3 — Tích hợp feed/post detail
- trả thêm `totalReacts`
- trả thêm `reactedByCurrentUser`

Phase 4 — Hardening
- xử lý race condition với `@Transactional`
- test react/unreact nhiều lần liên tiếp
- có job đồng bộ lại `total_reacts` nếu cần

9) Kết luận
Với yêu cầu hiện tại, phần reactions cho post nên đi theo hướng đơn giản:

- chỉ lưu quan hệ user đã react hay chưa
- chỉ lưu sẵn `total_reacts` ở post
- không lưu và không trả thống kê riêng cho từng loại reaction

Ưu điểm:
- code gọn hơn
- query feed nhẹ hơn
- dễ triển khai nhanh
- vẫn đủ cho UI hiển thị số react tổng và trạng thái user đã react hay chưa

Nếu sau này cần emoji reaction nâng cao, có thể mở rộng tiếp, nhưng version hiện tại chưa cần breakdown theo từng loại.

10) Flow hoạt động thực tế
Flow hiện tại đang chạy theo kiểu rất đơn giản:

Bước 1 — Tạo post
Frontend hoặc Postman gọi API tạo bài viết trước để lấy `postId`.

Bước 2 — React post
Client gọi:
`POST /api/social/posts/{postId}/react`
kèm header `X-User-Id`.

Backend xử lý như sau:
- tìm post theo `postId`
- nếu không có post -> trả lỗi not found
- nếu post đã bị xoá mềm -> trả lỗi
- kiểm tra user đã react chưa bằng `existsByUserIdAndPostId`
- nếu chưa react:
  - tạo 1 record mới trong bảng `reactions`
  - tăng `reactions_count` của post lên 1
- nếu đã react rồi:
  - không tạo thêm bản ghi mới
  - trả về summary hiện tại

Bước 3 — Lấy reaction summary
Client gọi:
`GET /api/social/posts/{postId}/reaction-summary`
để biết:
- tổng số react hiện tại
- user hiện tại đã react hay chưa

Bước 4 — Bỏ react
Client gọi:
`DELETE /api/social/posts/{postId}/react`
kèm header `X-User-Id`.

Backend sẽ:
- xoá record reaction của user với post đó
- giảm `reactions_count` đi 1
- chặn không cho giảm xuống số âm bằng `Math.max(0, ...)`

11) Test Postman
11.1. Tạo post để lấy `postId`
Method: `POST`
URL:
`http://localhost:8085/api/social/posts/create`

Headers:
```http
Content-Type: application/json
X-User-Id: user_001
```

Request body đầy đủ:
```json
{
  "content": "Bài viết dùng để test reactions bằng Postman",
  "visibility": "PUBLIC",
  "media": [
    {
      "publicId": "sample-post-001",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/sample.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 800,
      "height": 600,
      "bytes": 120345
    }
  ],
  "mediaUrls": []
}
```

Response mong đợi:
```json
{
  "id": 1,
  "userId": "user_001",
  "content": "Bài viết dùng để test reactions bằng Postman",
  "visibility": "PUBLIC",
  "media": [
    {
      "publicId": "sample-post-001",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/sample.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 800,
      "height": 600,
      "bytes": 120345
    }
  ],
  "mediaUrls": [
    "https://res.cloudinary.com/demo/image/upload/sample.jpg"
  ],
  "totalReacts": 0,
  "reactedByCurrentUser": false
}
```

> Lấy giá trị `id` từ response để dùng làm `postId` cho các request bên dưới.

11.2. React vào post
Method: `POST`
URL:
`http://localhost:8085/api/social/posts/1/react`

Headers:
```http
X-User-Id: user_002
```

Request body:
```json
{}
```

Lưu ý:
- endpoint này hiện **không cần request body**;
- có thể gửi body rỗng `{}` hoặc không gửi body đều được.

Response mong đợi:
```json
{
  "postId": 1,
  "totalReacts": 1,
  "reactedByCurrentUser": true
}
```

11.3. Kiểm tra summary
Method: `GET`
URL:
`http://localhost:8085/api/social/posts/1/reaction-summary`

Headers:
```http
X-User-Id: user_002
```

Request body: không có

Response mong đợi:
```json
{
  "postId": 1,
  "totalReacts": 1,
  "reactedByCurrentUser": true
}
```

11.4. Bỏ react
Method: `DELETE`
URL:
`http://localhost:8085/api/social/posts/1/react`

Headers:
```http
X-User-Id: user_002
```

Request body:
```json
{}
```

Response mong đợi:
```json
{
  "postId": 1,
  "totalReacts": 0,
  "reactedByCurrentUser": false
}
```

11.5. Test nhiều user
Có thể test nhanh như sau:
- `user_002` react -> `totalReacts = 1`
- `user_003` react -> `totalReacts = 2`
- `user_002` unreact -> `totalReacts = 1`

12) Ghi chú khi test
- nhớ truyền đúng header `X-User-Id`
- endpoint react hiện không dùng request body
- nếu `postId` không tồn tại sẽ báo lỗi not found
- nếu post đã bị hide/delete mềm thì sẽ không cho react
