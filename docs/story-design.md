# Thiết kế nghiệp vụ Story (đăng tin 24h giống Facebook)

## 1. Mục tiêu tài liệu

Tài liệu này phân tích codebase hiện tại và đề xuất thiết kế cho nghiệp vụ Story 24h theo hướng:

- bám sát kiến trúc microservice đang có
- tận dụng tối đa social-service, media-service, api-gateway, notification-service hiện tại
- tránh tạo thêm service mới nếu chưa thực sự cần
- đủ chi tiết để backend có thể implement theo phase

Story ở đây được hiểu là:

- user đăng ảnh hoặc video ngắn
- story tồn tại trong 24 giờ
- người khác xem được theo quyền riêng tư
- hệ thống ghi nhận lượt xem
- frontend có thể hiển thị danh sách story theo nhóm user

Tài liệu này không giả định project đã có story trong code. Hiện tại repo chưa có story entity, repository, service hay controller riêng.

---

## 2. Hiện trạng code sau khi đọc dự án

### 2.1. `social-media-social-service`

Hiện đã có:

- post CRUD
- post visibility: `PUBLIC`, `FRIEND`, `PRIVATE`
- friend graph hai chiều qua `FriendRepository.findAllAcceptedFriendIds(userId)`
- event producer Kafka cho post, comment, reaction, friend request
- gateway inject `X-User-Id` nên social-service không tự parse JWT

Điểm liên quan trực tiếp tới story:

- post hiện lưu trong SQL Server qua JPA
- `PostEntity` dùng `Long id`, `createdAt`, `updatedAt`
- `PostService` đã có logic visibility và danh sách friend accepted
- story có thể reuse cùng pattern service/controller/repository trong social-service

### 2.2. `social-media-media-service`

Hiện đã có:

- upload ảnh/video lên Cloudinary
- trả về `publicId`, `mediaUrl`, `mediaType`, `width`, `height`, `bytes`
- delete single và delete batch theo `publicId`

Constraint quan trọng:

- folder upload đang hard-code là `social-media/posts/{userId}`
- tag upload đang hard-code theo post

Điều này có nghĩa là để hỗ trợ story sạch sẽ, media-service nên được generalize thêm theo `resourceType=story` hoặc `folder=stories` thay vì reuse y nguyên folder của post.

### 2.3. `social-media-api-gateway`

Gateway hiện:

- validate access token
- đọc `sub` làm `X-User-Id`
- forward xuống các service private

Story nên reuse cơ chế này giống post và friend hiện tại.

### 2.4. `social-media-user-service`

User-service hiện cung cấp:

- `/api/users/me`
- `/api/users/{username}`
- `/api/users/search`

DTO profile có các field frontend cần cho story ring và viewer list như:

- `username`
- `fullName`
- `avatarUrl`

### 2.5. `social-media-notification-service`

Notification-service đã consume social event qua Kafka và phát SSE realtime.

Tuy nhiên, với story phase đầu, không nên mặc định fanout notification cho mỗi story mới, vì:

- story có tần suất cao hơn post
- fanout tới toàn bộ friend sẽ tốn event hơn nhiều
- UX kiểu Facebook thường không cần push notification cho mọi story mới

Kết luận: phase đầu của story nên chạy độc lập với notification-service, chỉ cân nhắc event khi có nhu cầu product rõ ràng.

---

## 3. Kết luận kiến trúc

## Khuyến nghị chính

Nghiệp vụ Story nên được đặt trong `social-media-social-service`.

### Lý do

1. Story phụ thuộc trực tiếp vào social graph hiện có.
2. Visibility của story gần với visibility của post.
3. Feed story cần danh sách accepted friends mà social-service đã có sẵn.
4. Không cần tạo thêm microservice mới chỉ để lưu một domain còn khá gần với post.
5. Reuse được pattern controller/service/repository/helper hiện tại.

### Không khuyến nghị ở phase hiện tại

- tách riêng `story-service`
- gọi sync từ social-service sang user-service cho mọi request story
- bắn notification cho mỗi story mới
- reuse `PostEntity` hoặc `PostCreateRequest` trực tiếp cho story

Lý do không reuse post trực tiếp:

- post hiện bắt buộc `content`
- story cần lifecycle 24h riêng
- story cần tracking `viewers`
- story feed được group theo owner, không phải theo post timeline thông thường

---

## 4. Phạm vi nghiệp vụ nên làm ở phase 1

Phase 1 nên chốt các nghiệp vụ sau:

- tạo story mới
- lấy story feed của user hiện tại
- lấy danh sách story đang active của một user
- xem chi tiết story
- đánh dấu đã xem story
- xoá story của chính mình
- tự động hết hạn sau 24h
- xem danh sách viewer của story nếu là chủ story

Các nghiệp vụ chưa nên đưa vào phase 1:

- reply story
- reaction story
- mention trong story
- music/sticker/filter
- close friends
- highlight lưu lâu hơn 24h
- public discovery story ngoài social graph

---

## 5. Thiết kế domain đề xuất

## 5.1. StoryEntity

Đề xuất thêm bảng `stories` trong SQL Server.

```java
@Entity
@Table(name = "stories")
public class StoryEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "visibility", nullable = false)
    private String visibility;

    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

    @Column(name = "is_deleted", columnDefinition = "bit default 0")
    private boolean isDeleted = false;

    @Column(name = "view_count", columnDefinition = "int default 0")
    private int viewCount = 0;
}
```

### Ghi chú thiết kế

- dùng `Long id` để đồng nhất với `BaseEntity`
- `caption` là optional vì story có thể chỉ có media
- `visibility` nên giữ cùng enum logic với post: `PUBLIC`, `FRIEND`, `PRIVATE`
- `expiresAt` là field bắt buộc để query active story
- `isDeleted` hỗ trợ owner xóa tay trước khi story hết hạn
- `viewCount` giúp feed/story ring không phải count realtime quá nhiều

## 5.2. StoryMedia

Đề xuất tách media thành bảng con giống `PostMedia`.

```java
@Entity
@Table(name = "story_media")
public class StoryMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private StoryEntity story;

    @Column(name = "public_id")
    private String publicId;

    @Column(name = "media_url", nullable = false)
    private String mediaUrl;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "provider")
    private String provider;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "bytes")
    private Long bytes;
}
```

### Rule đề xuất

- phase 1 cho phép tối đa 1 media item mỗi story để đơn giản UX giống Facebook story cơ bản
- nếu muốn hỗ trợ text-only story thì cho phép danh sách media rỗng
- nếu muốn hỗ trợ nhiều item trong một story session, nên tách khái niệm `StoryBucket` hoặc `StoryGroup`, chưa cần làm ở phase 1

## 5.3. StoryViewEntity

Đề xuất thêm bảng `story_views` để tracking viewer.

```java
@Entity
@Table(name = "story_views", uniqueConstraints = {
    @UniqueConstraint(name = "uk_story_view_story_viewer", columnNames = {"story_id", "viewer_id"})
})
public class StoryViewEntity extends BaseEntity {

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "viewer_id", nullable = false)
    private String viewerId;

    @Column(name = "viewed_at", nullable = false)
    private Date viewedAt;
}
```

### Lý do cần bảng riêng

- owner cần xem ai đã xem story
- frontend cần biết story đã xem hay chưa
- một user có thể xem lại nhiều lần nhưng chỉ nên count viewer duy nhất một lần

---

## 6. Repository và query cần có

## 6.1. StoryRepository

Các query lõi nên có:

```java
Page<StoryEntity> findByUserIdAndIsDeletedFalseAndExpiresAtAfterOrderByCreatedAtDesc(String userId, Date now, Pageable pageable);

Optional<StoryEntity> findByIdAndIsDeletedFalse(Long storyId);

@Query("""
    SELECT s FROM StoryEntity s
    WHERE s.isDeleted = false
      AND s.expiresAt > :now
      AND (
            s.userId = :viewerId
         OR s.visibility = 'PUBLIC'
         OR (s.visibility = 'FRIEND' AND s.userId IN :friendIds)
      )
    ORDER BY s.userId ASC, s.createdAt DESC
""")
List<StoryEntity> findFeedStories(String viewerId, List<String> friendIds, Date now);
```

### Lưu ý

- feed story nên chỉ trả story active: `isDeleted = false` và `expiresAt > now`
- do SQL Server không có TTL tự nhiên, việc loại story hết hạn phải được enforce bằng query và cleanup job

## 6.2. StoryViewRepository

Các query cần có:

```java
boolean existsByStoryIdAndViewerId(Long storyId, String viewerId);

Page<StoryViewEntity> findByStoryIdOrderByViewedAtDesc(Long storyId, Pageable pageable);

long countByStoryId(Long storyId);

List<Long> findViewedStoryIds(String viewerId, List<Long> storyIds);
```

---

## 7. DTO đề xuất

## 7.1. StoryCreateRequest

```json
{
  "caption": "Đi chơi cuối tuần",
  "visibility": "FRIEND",
  "media": [
    {
      "publicId": "social-media/stories/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1920,
      "bytes": 348123
    }
  ]
}
```

Validation đề xuất:

- `caption`: optional, tối đa 500 ký tự
- `visibility`: bắt buộc, chỉ nhận `PUBLIC`, `FRIEND`, `PRIVATE`
- `media`: optional nhưng phase 1 nên yêu cầu ít nhất 1 item nếu product muốn story media-first
- tối đa 1 video hoặc 1 ảnh mỗi story ở phase đầu

## 7.2. StoryResponse

```json
{
  "id": 101,
  "userId": "67f25f9e63d6d40d672f98d2",
  "caption": "Đi chơi cuối tuần",
  "visibility": "FRIEND",
  "media": [
    {
      "publicId": "social-media/stories/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1920,
      "bytes": 348123
    }
  ],
  "viewCount": 12,
  "viewedByCurrentUser": true,
  "createdAt": "2026-04-06T09:00:00Z",
  "expiresAt": "2026-04-07T09:00:00Z"
}
```

## 7.3. StoryFeedGroupResponse

Frontend story ring nên lấy theo nhóm user, không phải flat list từng story.

```json
{
  "userId": "userB",
  "username": "john",
  "fullName": "John Doe",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "hasUnseen": true,
  "latestStoryAt": "2026-04-06T09:00:00Z",
  "stories": [
    {
      "id": 101,
      "caption": "Đi chơi cuối tuần",
      "media": [
        {
          "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
          "mediaType": "IMAGE"
        }
      ],
      "viewedByCurrentUser": false,
      "createdAt": "2026-04-06T09:00:00Z",
      "expiresAt": "2026-04-07T09:00:00Z"
    }
  ]
}
```

## 7.4. StoryViewResponse

```json
{
  "storyId": 101,
  "viewerId": "userC",
  "viewedAt": "2026-04-06T10:15:00Z"
}
```

---

## 8. API đề xuất

Base URL qua gateway:

- `http://localhost:8080/api/social/stories`

## 8.1. Tạo story

`POST /api/social/stories`

Headers:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Gateway sẽ forward `X-User-Id` xuống social-service.

Body:

```json
{
  "caption": "Đi chơi cuối tuần",
  "visibility": "FRIEND",
  "media": [
    {
      "publicId": "social-media/stories/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY"
    }
  ]
}
```

Response:

```json
{
  "id": 101,
  "userId": "user123",
  "caption": "Đi chơi cuối tuần",
  "visibility": "FRIEND",
  "media": [
    {
      "publicId": "social-media/stories/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY"
    }
  ],
  "viewCount": 0,
  "viewedByCurrentUser": false,
  "createdAt": "2026-04-06T09:00:00Z",
  "expiresAt": "2026-04-07T09:00:00Z"
}
```

## 8.2. Lấy story feed

`GET /api/social/stories/feed`

Mục đích:

- trả danh sách ring story mà user hiện tại được phép xem
- group theo owner
- ưu tiên owner hiện tại lên trước
- friend có unseen story lên trước seen story

Response mẫu:

```json
[
  {
    "userId": "user123",
    "username": "me",
    "fullName": "My Account",
    "avatarUrl": "https://cdn.example.com/me.jpg",
    "hasUnseen": false,
    "latestStoryAt": "2026-04-06T09:00:00Z",
    "stories": [
      {
        "id": 101,
        "caption": "Tin của tôi",
        "media": [
          {
            "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
            "mediaType": "IMAGE"
          }
        ],
        "viewedByCurrentUser": true,
        "createdAt": "2026-04-06T09:00:00Z",
        "expiresAt": "2026-04-07T09:00:00Z"
      }
    ]
  },
  {
    "userId": "user456",
    "username": "john",
    "fullName": "John Doe",
    "avatarUrl": "https://cdn.example.com/john.jpg",
    "hasUnseen": true,
    "latestStoryAt": "2026-04-06T08:30:00Z",
    "stories": [
      {
        "id": 120,
        "caption": "Good morning",
        "media": [
          {
            "mediaUrl": "https://res.cloudinary.com/demo/video/upload/v1/story.mp4",
            "mediaType": "VIDEO"
          }
        ],
        "viewedByCurrentUser": false,
        "createdAt": "2026-04-06T08:30:00Z",
        "expiresAt": "2026-04-07T08:30:00Z"
      }
    ]
  }
]
```

## 8.3. Lấy story active của một user

`GET /api/social/stories/users/{userId}`

Use case:

- mở cụm story của một user cụ thể
- profile page muốn hiển thị story đang active

## 8.4. Lấy chi tiết một story

`GET /api/social/stories/{storyId}`

Rule:

- owner xem được story của mình kể cả đã seen
- người khác chỉ xem được nếu story chưa hết hạn và đúng visibility

## 8.5. Đánh dấu đã xem story

`POST /api/social/stories/{storyId}/view`

Body: không cần body

Rule:

- nếu viewer là owner thì không tạo record view
- nếu đã xem rồi thì idempotent, trả `200 OK`
- chỉ tạo 1 record view duy nhất trên mỗi cặp `storyId + viewerId`

Response mẫu:

```json
{
  "storyId": 101,
  "viewerId": "user456",
  "viewedAt": "2026-04-06T10:15:00Z"
}
```

## 8.6. Xem viewer list của story

`GET /api/social/stories/{storyId}/viewers?page=0&size=20`

Rule:

- chỉ owner mới xem được viewer list
- trả danh sách viewer theo `viewedAt desc`
- có thể enrich bằng profile từ user-service

## 8.7. Xoá story

`DELETE /api/social/stories/{storyId}`

Rule:

- chỉ owner được xoá
- xoá mềm trước bằng `isDeleted = true`
- cleanup job sẽ dọn media sau

---

## 9. Quy tắc visibility và truy cập

Nên giữ quy tắc gần với post để dễ hiểu cho frontend và backend.

### `PRIVATE`

- chỉ owner xem được
- không xuất hiện trong feed của người khác

### `FRIEND`

- owner xem được
- accepted friends xem được
- người lạ không xem được

### `PUBLIC`

- phase 1 vẫn nên ưu tiên hiển thị cho owner và accepted friends trong story feed
- nếu cần public profile story thì có thể cho phép xem khi truy cập trực tiếp profile user
- không nên xây riêng public discovery feed cho story ở phase đầu

### Hàm kiểm tra quyền nên có

```java
boolean canViewStory(String viewerId, StoryEntity story, List<String> acceptedFriendIds)
```

Logic:

1. nếu `viewerId == story.userId` thì cho phép
2. nếu story hết hạn hoặc bị xoá thì từ chối
3. nếu `visibility = PRIVATE` thì từ chối với người khác
4. nếu `visibility = FRIEND` thì chỉ cho accepted friends
5. nếu `visibility = PUBLIC` thì cho phép

---

## 10. Flow nghiệp vụ chính

## 10.1. Flow tạo story

1. Frontend upload ảnh hoặc video lên media-service.
2. Media-service trả metadata Cloudinary.
3. Frontend gọi `POST /api/social/stories` với metadata đó.
4. Gateway xác thực JWT và inject `X-User-Id`.
5. Social-service validate payload.
6. Social-service lưu `StoryEntity` và `StoryMedia`.
7. Social-service set `expiresAt = createdAt + 24h`.
8. Trả `StoryResponse` cho frontend.

### Ghi chú quan trọng

Không nên upload file trực tiếp qua social-service vì media-service đã được tách đúng trách nhiệm.

## 10.2. Flow xem story

1. Frontend gọi `GET /api/social/stories/feed` để render ring.
2. Khi user mở một story cụ thể, frontend gọi `GET /api/social/stories/{storyId}` nếu cần payload chi tiết.
3. Sau khi story được hiển thị đủ điều kiện, frontend gọi `POST /api/social/stories/{storyId}/view`.
4. Social-service tạo `StoryViewEntity` nếu chưa tồn tại.
5. `viewCount` của story được tăng lên một lần cho viewer mới.

### Idempotency

`/view` phải idempotent để frontend có thể retry mà không làm sai `viewCount`.

## 10.3. Flow hết hạn story

Do SQL Server không có TTL document như Mongo, cần 2 lớp xử lý:

### Lớp 1: loại bỏ ở query time

Mọi API đọc phải lọc:

- `isDeleted = false`
- `expiresAt > now`

### Lớp 2: cleanup job nền

Đề xuất job chạy mỗi 10 hoặc 15 phút:

1. tìm story đã `expiresAt <= now` hoặc `isDeleted = true`
2. gom `publicId` của media
3. gọi media-service `delete-batch`
4. xoá `StoryMedia`
5. xoá `StoryViewEntity`
6. xoá hẳn `StoryEntity` hoặc lưu thêm retention ngắn nếu muốn audit

Khuyến nghị practical:

- phase 1 có thể giữ hard delete sau khi hết hạn 1 giờ để tránh race condition client đang xem

---

## 11. Tích hợp với media-service

## Vấn đề hiện tại

`CloudinaryMediaService` đang hard-code:

- folder: `social-media/posts/{userId}`
- tags: `post,social-service,{userId}`

Điều này chưa phù hợp cho story.

## Thiết kế đề xuất

Generalize endpoint upload để nhận thêm `resourceType`.

Ví dụ:

`POST /api/media/upload?resourceType=story`

Hoặc thêm param form-data:

```text
resourceType=story
```

Media-service khi đó map:

- `post` -> folder `social-media/posts/{userId}`
- `story` -> folder `social-media/stories/{userId}`

Tag Cloudinary đề xuất:

- `story`
- `social-service`
- `{userId}`

### Vì sao cần tách folder

1. dễ cleanup asset story theo lifecycle 24h
2. log và quản trị Cloudinary rõ ràng hơn
3. tránh lẫn media lâu dài của post với asset ngắn hạn của story

---

## 12. Tích hợp với user-service

Story feed cần hiển thị:

- avatar
- username
- fullName

Social-service hiện chỉ có `userId`, không có profile snapshot.

Có 2 hướng:

### Hướng phù hợp phase 1

Frontend nhận `userId` từ story feed và tự gọi user-service để enrich profile theo batch hoặc cache local.

Ưu điểm:

- social-service không bị coupling thêm ngay
- implement nhanh

Nhược điểm:

- frontend phải join dữ liệu

### Hướng phase 2 tốt hơn

Social-service gọi user-service hoặc giữ cache profile mỏng để trả luôn `username`, `fullName`, `avatarUrl` trong story feed.

Khuyến nghị thực tế:

- phase 1: social-service trả `userId`, frontend join
- phase 2: thêm enrichment ở backend nếu story feed trở thành màn hình trọng yếu

---

## 13. Có nên bắn notification cho story không

Khuyến nghị: không bắn notification cho `story created` ở phase 1.

### Lý do

1. rất dễ tạo spam notification nếu user đăng story thường xuyên
2. fanout tới tất cả friend tốn Kafka event và lưu notification không cần thiết
3. UX phổ biến là người dùng thấy story qua ring ở đầu màn hình, không phải qua notification center

### Nếu product bắt buộc cần thông báo

Chỉ nên thêm ở phase sau với event:

```json
{
  "eventType": "SOCIAL_STORY_CREATED",
  "storyId": 101,
  "actorId": "user123",
  "recipientId": "user456",
  "deeplink": "/stories/101"
}
```

Nhưng đây không nên là default cho phase đầu.

---

## 14. Các điểm khác với Post cần chú ý

| Chủ đề | Post hiện tại | Story đề xuất |
| --- | --- | --- |
| Thời gian sống | lâu dài | 24 giờ |
| Nội dung text | bắt buộc | optional |
| Media | nhiều item | phase 1 nên 1 item |
| Feed | timeline theo post | group theo owner |
| Reaction/comment | đã có | chưa nên làm phase 1 |
| Viewer tracking | không có | bắt buộc |
| Notification | đã có cho vài flow | không nên mặc định |

Story vì vậy nên là domain riêng, không gắn thêm cờ `isStory` vào `PostEntity`.

---

## 15. Các class/backend component nên thêm

Trong `social-media-social-service`, nên thêm:

- `entities/StoryEntity.java`
- `entities/StoryMedia.java`
- `entities/StoryViewEntity.java`
- `repositories/StoryRepository.java`
- `repositories/StoryViewRepository.java`
- `repositories/StoryMediaRepository.java` nếu cần riêng
- `dto/StoryDTO/StoryCreateRequest.java`
- `dto/StoryDTO/StoryResponse.java`
- `dto/StoryDTO/StoryFeedGroupResponse.java`
- `dto/StoryDTO/StoryViewResponse.java`
- `service/StoryService.java`
- `controllers/StoryController.java`
- `helpers/StoryHelper.java`
- `job/StoryCleanupJob.java`

Trong `social-media-media-service`, nên chỉnh:

- `MediaController`
- `CloudinaryMediaService`

để upload theo `resourceType` thay vì hard-code post.

---

## 16. API contract tối thiểu nên chốt với frontend

Frontend phase 1 chỉ cần 6 API:

1. `POST /api/media/upload?resourceType=story`
2. `POST /api/social/stories`
3. `GET /api/social/stories/feed`
4. `GET /api/social/stories/users/{userId}`
5. `POST /api/social/stories/{storyId}/view`
6. `DELETE /api/social/stories/{storyId}`

API viewer list có thể thêm ngay hoặc để phase 1.1:

7. `GET /api/social/stories/{storyId}/viewers`

---

## 17. Rủi ro và cách xử lý

## Rủi ro 1: SQL Server không có TTL tự dọn story

Giải pháp:

- luôn lọc `expiresAt > now` khi đọc
- thêm cleanup job nền

## Rủi ro 2: media rác trên Cloudinary nếu story bị xoá hoặc hết hạn

Giải pháp:

- story cleanup job phải gọi `delete-batch`
- tách folder `stories/{userId}` để dọn chính xác

## Rủi ro 3: frontend bắn `/view` nhiều lần

Giải pháp:

- unique constraint `story_id + viewer_id`
- service xử lý idempotent

## Rủi ro 4: feed story cần profile data để render ring đẹp

Giải pháp:

- phase 1 để frontend enrich từ user-service
- phase 2 mới backend enrich

## Rủi ro 5: notification story gây spam

Giải pháp:

- không phát event story created ở phase đầu

---

## 18. Lộ trình implement khuyến nghị

## Phase 1

- thêm story entity/repository/service/controller
- thêm story create/feed/view/delete
- thêm story view tracking
- thêm cleanup job
- generalize media upload cho `resourceType=story`

## Phase 1.1

- thêm viewer list
- thêm profile enrichment nhẹ cho feed response
- tối ưu sort unseen/seen story ring

## Phase 2

- reply story
- reaction story
- story notification nếu product thực sự cần
- highlight story

---

## 19. Kết luận

Hướng phù hợp nhất với codebase hiện tại là:

- đặt story trong `social-media-social-service`
- tái dùng `media-service` cho upload media nhưng phải generalize folder theo `resourceType=story`
- dùng SQL Server với `expiresAt` + cleanup job thay cho TTL tự động
- dùng `FriendRepository.findAllAcceptedFriendIds(userId)` để quyết định audience cho `FRIEND`
- chưa tích hợp notification-service cho story created ở phase đầu

Thiết kế này giữ được 3 mục tiêu quan trọng:

1. triển khai nhanh trên nền code hiện có
2. đúng với social graph và auth flow đang chạy trong dự án
3. đủ sạch để mở rộng reply/reaction/highlight ở phase sau
