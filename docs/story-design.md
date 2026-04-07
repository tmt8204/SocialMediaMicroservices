# Thiết kế nghiệp vụ Story 24h theo codebase hiện tại

## 1. Mục tiêu tài liệu

Tài liệu này viết lại thiết kế Story theo trạng thái codebase hiện tại của dự án, với các mục tiêu:

- bám sát kiến trúc microservice đang chạy
- tận dụng tối đa social-service, media-service, api-gateway, user-service và notification-service hiện có
- tránh tạo service mới khi domain vẫn còn nằm tự nhiên trong social-service
- xác định rõ phạm vi phase 1, phase 1.1 và phase 2
- đủ chi tiết để backend có thể implement theo từng phase mà không phải refactor lớn giữa chừng

Story trong tài liệu này được hiểu là:

- người dùng đăng ảnh hoặc video ngắn tồn tại 24 giờ
- story có quyền riêng tư tương tự post: `PUBLIC`, `FRIEND`, `PRIVATE`
- hệ thống ghi nhận người xem và trạng thái đã xem
- frontend hiển thị story theo cụm owner giống story ring

Repo hiện chưa có implementation story riêng. Chưa có entity, repository, service, controller hay cleanup job cho story.

---

## 2. Tóm tắt hiện trạng codebase

## 2.1. `social-media-social-service`

Hiện service này đã có:

- post CRUD
- feed post theo visibility và friend graph
- friend graph hai chiều qua `FriendRepository.findAllAcceptedFriendIds(userId)`
- community domain và community feed
- gọi sang user-service để enrich profile backend-side
- gọi sang media-service để cleanup media theo `publicId`
- phát social notification event qua Kafka cho một số flow hiện tại

Các đặc điểm quan trọng liên quan đến story:

- dữ liệu đang lưu trên SQL Server qua Spring Data JPA
- `BaseEntity` dùng `Long id`, `createdAt`, `updatedAt`
- `PostService` đã có pattern kiểm tra visibility, đọc friend graph, enrich profile và cleanup media
- `UserProfileClient` đã gọi nội bộ sang user-service qua `/api/users/internal/profiles`
- `MediaServiceClient` đã có sẵn batch delete sang media-service
- chưa có scheduling bật sẵn cho cleanup job

Kết luận:

- story nên reuse pattern service/controller/repository/helper của social-service
- không nên đẩy việc join profile cho frontend nếu backend đã có cơ chế enrich ổn định
- cleanup 24h cần được thiết kế thêm thay vì giả định service đã có scheduler

## 2.2. `social-media-media-service`

Hiện media-service đã có các API:

- `POST /api/media/upload`
- `GET /api/media/my-uploads`
- `DELETE /api/media`
- `POST /api/media/delete-batch`

Media upload lên Cloudinary và trả về metadata gồm:

- `publicId`
- `mediaUrl`
- `mediaType`
- `width`
- `height`
- `bytes`

Hạn chế hiện tại:

- folder upload đang hard-code thành `social-media/posts/{userId}`
- tag upload đang hard-code theo post
- API `my-uploads` cũng đang đọc theo prefix của post
- delete hiện theo `publicId`, chưa có ownership check rõ ràng ở media-service

Kết luận:

- story nên reuse media-service
- nhưng media-service phải được generalize theo `resourceType`
- thay đổi này không chỉ áp dụng cho upload mà nên áp dụng luôn cho `my-uploads`

## 2.3. `social-media-api-gateway`

Gateway hiện đã có route cho:

- `/api/social/**`
- `/api/media/**`
- `/api/users/**`
- `/api/notifications/**`

Gateway validate access token, kiểm tra revocation, sau đó inject:

- `X-User-Id`
- `X-Role`

Kết luận:

- story không cần xử lý JWT riêng ở social-service
- story chỉ cần follow đúng contract nội bộ hiện có của gateway

## 2.4. `social-media-user-service`

User-service hiện có cả public endpoint lẫn internal endpoint.

Public endpoint:

- `/api/users/me`
- `/api/users/{username}`
- `/api/users/search`
- `/api/users/update-profile`

Internal endpoint đã tồn tại:

- `/api/users/internal/profiles`
- `/api/users/internal/discover`

Field phù hợp để enrich story feed và viewer list đã có trong `UserProfileSummaryResponse`:

- `userId`
- `username`
- `fullName`
- `avatarUrl`

Lưu ý:

- `UserResponse` public không có `userId`
- nhưng internal summary response có `userId`, đủ dùng cho story backend enrichment

Kết luận:

- phase 1 nên enrich profile tại backend bằng internal endpoint
- không cần đẩy join profile sang frontend như một mặc định nữa

## 2.5. `social-media-notification-service`

Notification-service hiện không còn là skeleton. Service này đã có:

- Mongo persistence cho notification
- Kafka consumer cho social/chat event
- REST API cho notification list và unread count
- SSE stream realtime

Tuy nhiên, với story phase đầu, vẫn không nên phát notification mặc định cho mỗi story mới vì:

- story có tần suất cao hơn post
- fanout tới toàn bộ bạn bè dễ tạo spam
- UX story thường dựa vào ring ở đầu màn hình hơn là notification center

Kết luận:

- story phase 1 không tích hợp notification-service cho `story created`
- chỉ cân nhắc event ở phase sau nếu product thực sự yêu cầu

---

## 3. Kết luận kiến trúc

## Khuyến nghị chính

Nghiệp vụ Story nên đặt trong `social-media-social-service`.

### Lý do

1. Story phụ thuộc trực tiếp vào social graph hiện có.
2. Visibility của story gần với visibility của post.
3. Story feed phase đầu vẫn dựa vào owner và accepted friends.
4. Social-service đã có pattern enrich profile backend-side.
5. Social-service đã có pattern cleanup media sau khi xóa entity chính.
6. Chưa có lý do đủ mạnh để tách một `story-service` riêng ở giai đoạn này.

## Không khuyến nghị ở giai đoạn hiện tại

- tạo thêm microservice mới chỉ cho story
- nhét thêm cờ `isStory` vào `PostEntity`
- phát notification cho mọi story mới ngay từ phase 1
- để frontend tự join profile như hướng mặc định
- thiết kế public discovery feed cho story ngay ở phase đầu

## Kết luận triển khai

Story là domain riêng, nhưng nên nằm cùng social-service để reuse:

- friend graph
- visibility rules
- user enrichment
- media cleanup
- gateway auth flow

---

## 4. Phạm vi nghiệp vụ theo phase

## 4.1. Phase 1

Phase 1 nên chốt các nghiệp vụ lõi sau:

- tạo story mới
- lấy story feed của user hiện tại theo cụm owner
- lấy story active của một user cụ thể
- lấy chi tiết một story
- đánh dấu đã xem story
- xóa story của chính mình
- lọc story hết hạn ở query time
- cleanup story hết hạn hoặc đã xóa bằng background job
- generalize media upload cho `resourceType=story`

## 4.2. Phase 1.1

Phase 1.1 nên bổ sung các phần có giá trị cao nhưng không bắt buộc để go-live:

- xem viewer list của story
- tối ưu sort ring theo `hasUnseen`
- enrich profile ổn định hơn cho viewer list
- tăng khả năng chịu lỗi nếu user-service tạm unavailable

## 4.3. Phase 2

Chỉ nên làm khi phase 1 đã ổn định:

- story reaction
- story reply
- close friends story
- highlight story
- optional notification cho story
- analytics sâu hơn cho lượt xem

---

## 5. Thiết kế domain đề xuất

## 5.1. `StoryEntity`

Đề xuất thêm bảng `stories` trong SQL Server.

```java
@Entity
@Table(name = "stories")
public class StoryEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "visibility", nullable = false, length = 50)
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

- kế thừa `BaseEntity` để đồng nhất với phần còn lại của social-service
- `caption` là optional
- `visibility` giữ cùng rule với post: `PUBLIC`, `FRIEND`, `PRIVATE`
- `expiresAt` là field bắt buộc để query active story
- `isDeleted` dùng cho soft delete trước khi cleanup thực sự
- `viewCount` là counter denormalized để feed không phải count lại toàn bộ viewer mỗi lần

## 5.2. `StoryMedia`

Đề xuất tách media thành bảng riêng giống `PostMedia`.

```java
@Entity
@Table(name = "story_media")
public class StoryMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private StoryEntity story;

    @Column(name = "public_id", columnDefinition = "nvarchar(255)")
    private String publicId;

    @Column(name = "media_url", columnDefinition = "nvarchar(1000)")
    private String mediaUrl;

    @Column(name = "media_type", columnDefinition = "nvarchar(50)")
    private String mediaType = "IMAGE";

    @Column(name = "provider", columnDefinition = "nvarchar(50)")
    private String provider = "CLOUDINARY";

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "bytes")
    private Long bytes;
}
```

### Rule phase 1

- phase 1 chỉ cho tối đa 1 media item mỗi story
- có thể cho phép text-only story nếu product muốn
- nếu muốn nhiều item trong cùng một session thì đó là bài toán phase sau, không nên nhồi vào phase 1

## 5.3. `StoryViewEntity`

Đề xuất thêm bảng `story_views` để tracking viewer duy nhất.

```java
@Entity
@Table(
    name = "story_views",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_story_view_story_viewer",
            columnNames = {"story_id", "viewer_id"}
        )
    }
)
public class StoryViewEntity extends BaseEntity {

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "viewer_id", nullable = false)
    private String viewerId;
}
```

### Vì sao không cần `ownerId` ở phase 1

- owner có thể suy ra từ `StoryEntity`
- tránh dư thừa dữ liệu và giảm rủi ro lệch dữ liệu khi owner logic thay đổi
- nếu về sau cần query tối ưu theo owner thì mới cân nhắc denormalize thêm

### Vì sao không cần `viewedAt` riêng ở phase 1

- có thể dùng `createdAt` của `BaseEntity` làm thời điểm first view
- response viewer list vẫn map `createdAt` ra `viewedAt` cho frontend
- giữ consistency với entity pattern hiện có của service

---

## 6. Quy tắc truy cập và visibility

Story cần tách rõ 2 khái niệm:

- quyền xem trực tiếp một story cụ thể
- quyền xuất hiện trong story ring feed

## 6.1. Quyền xem trực tiếp một story

### `PRIVATE`

- chỉ owner xem được

### `FRIEND`

- owner xem được
- accepted friends xem được

### `PUBLIC`

- owner xem được
- accepted friends xem được
- người lạ có thể xem nếu truy cập trực tiếp vào story hoặc profile owner ở phase sau

## 6.2. Quy tắc cho story ring feed phase 1

Story ring phase 1 chỉ nên lấy:

- story của chính owner
- story của accepted friends

Ngay cả khi story là `PUBLIC`, phase 1 vẫn không nên có public discovery ring cho người lạ.

Điểm này cần chốt rõ để tránh backend và frontend hiểu khác nhau.

## 6.3. Hàm kiểm tra quyền nên có

```java
boolean canViewStoryDirectly(String viewerId, StoryEntity story, List<String> acceptedFriendIds)
```

Logic:

1. nếu `viewerId` là owner thì cho phép
2. nếu story đã hết hạn hoặc bị xóa thì từ chối
3. nếu `visibility = PRIVATE` thì từ chối với người khác
4. nếu `visibility = FRIEND` thì chỉ accepted friends được xem
5. nếu `visibility = PUBLIC` thì cho phép

Feed ring không dùng thẳng rule này, mà có rule chặt hơn theo phase 1.

---

## 7. Repository và query lõi

## 7.1. `StoryRepository`

Các query chính nên có:

```java
Page<StoryEntity> findByUserIdAndIsDeletedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
    String userId,
    Date now,
    Pageable pageable
);

Optional<StoryEntity> findByIdAndIsDeletedFalse(Long storyId);

@Query("""
    SELECT s FROM StoryEntity s
    WHERE s.isDeleted = false
      AND s.expiresAt > :now
      AND (
            s.userId = :viewerId
         OR s.userId IN :friendIds
      )
    ORDER BY s.createdAt DESC
""")
List<StoryEntity> findFeedCandidates(String viewerId, List<String> friendIds, Date now);
```

### Lưu ý

- feed ring phase 1 nên query owner + accepted friends, không query toàn bộ `PUBLIC`
- nhóm theo owner nên xử lý ở service layer
- sort ring theo `latestStoryAt` và `hasUnseen` cũng nên xử lý ở service layer

## 7.2. `StoryViewRepository`

Các query chính:

```java
boolean existsByStoryIdAndViewerId(Long storyId, String viewerId);

Optional<StoryViewEntity> findByStoryIdAndViewerId(Long storyId, String viewerId);

Page<StoryViewEntity> findByStoryIdOrderByCreatedAtDesc(Long storyId, Pageable pageable);

long countByStoryId(Long storyId);

List<StoryViewEntity> findByViewerIdAndStoryIdIn(String viewerId, List<Long> storyIds);
```

### Lưu ý concurrency

- không nên implement theo kiểu `exists` rồi mới `insert` rồi mới `increment`
- nên dựa vào unique constraint để đảm bảo idempotent thật sự
- chỉ tăng `viewCount` khi insert viewer record thành công lần đầu

## 7.3. `StoryMediaRepository`

Nên có repository riêng để:

- load media theo story
- batch delete theo story ids
- gom `publicId` trước khi cleanup Cloudinary

---

## 8. DTO đề xuất

## 8.1. `StoryCreateRequest`

```json
{
  "caption": "Di choi cuoi tuan",
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

Validation nên có:

- `caption`: optional, tối đa 500 ký tự
- `visibility`: bắt buộc, chỉ nhận `PUBLIC`, `FRIEND`, `PRIVATE`
- `media`: phase 1 nên tối đa 1 item
- ít nhất một trong hai phải có: `caption` hoặc `media`

## 8.2. `StoryResponse`

```json
{
  "id": 101,
  "userId": "67f25f9e63d6d40d672f98d2",
  "username": "john",
  "fullName": "John Doe",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "caption": "Di choi cuoi tuan",
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

### Khuyến nghị quan trọng

`StoryResponse` phase 1 nên trả luôn:

- `username`
- `fullName`
- `avatarUrl`

vì backend hiện đã có pattern enrich profile bằng `UserProfileClient`.

## 8.3. `StoryFeedGroupResponse`

Frontend story ring cần dữ liệu theo owner, không phải flat list từng story.

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
      "caption": "Di choi cuoi tuan",
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

## 8.4. `StoryViewResponse`

```json
{
  "storyId": 101,
  "viewerId": "userC",
  "username": "anna",
  "fullName": "Anna Smith",
  "avatarUrl": "https://cdn.example.com/anna.jpg",
  "viewedAt": "2026-04-06T10:15:00Z"
}
```

---

## 9. API đề xuất

Base URL qua gateway:

- `http://localhost:8080/api/social/stories`

## 9.1. Upload story media

Story vẫn upload file qua media-service trước.

### Đề xuất contract mới

`POST /api/media/upload?resourceType=story`

Hoặc form-data có field:

```text
resourceType=story
```

### Bổ sung nên có

`GET /api/media/my-uploads?resourceType=story`

để đọc lại đúng prefix story thay vì prefix post.

## 9.2. Tạo story

`POST /api/social/stories`

Headers:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Gateway sẽ inject `X-User-Id` xuống social-service.

## 9.3. Lấy story feed

`GET /api/social/stories/feed`

Mục đích:

- trả story ring cho user hiện tại
- group theo owner
- owner hiện tại có thể ưu tiên lên đầu
- unseen ring ưu tiên trước seen ring

### Rule phase 1

- chỉ trả story của owner và accepted friends
- không trả public discovery feed cho người lạ

## 9.4. Lấy story active của một user

`GET /api/social/stories/users/{userId}`

Use case:

- mở cụm story của một owner cụ thể
- profile page cần hiển thị story active

## 9.5. Lấy chi tiết một story

`GET /api/social/stories/{storyId}`

Rule:

- owner xem được story của mình nếu story còn active
- accepted friend xem được story `FRIEND`
- người lạ chỉ xem được story `PUBLIC` khi truy cập trực tiếp

## 9.6. Đánh dấu đã xem story

`POST /api/social/stories/{storyId}/view`

Rule:

- nếu viewer là owner thì không tạo record view
- nếu viewer đã xem rồi thì idempotent, trả `200 OK`
- chỉ tạo một record duy nhất trên mỗi cặp `storyId + viewerId`
- chỉ tăng `viewCount` khi tạo record lần đầu thành công

## 9.7. Xem viewer list của story

`GET /api/social/stories/{storyId}/viewers?page=0&size=20`

Khuyến nghị triển khai ở phase 1.1.

Rule:

- chỉ owner mới xem được viewer list
- trả danh sách viewer theo `createdAt desc` của `StoryViewEntity`
- enrich profile tại backend bằng user-service internal profiles

## 9.8. Xóa story

`DELETE /api/social/stories/{storyId}`

Rule:

- chỉ owner được xóa
- xóa mềm trước bằng `isDeleted = true`
- cleanup job sẽ dọn media và bản ghi phụ

---

## 10. Flow nghiệp vụ chính

## 10.1. Flow tạo story

1. Frontend upload ảnh hoặc video lên media-service với `resourceType=story`.
2. Media-service trả metadata Cloudinary.
3. Frontend gọi `POST /api/social/stories` với metadata đó.
4. Gateway xác thực JWT và inject `X-User-Id`.
5. Social-service validate payload.
6. Social-service lưu `StoryEntity` và `StoryMedia`.
7. Social-service set `expiresAt = createdAt + 24h`.
8. Social-service enrich profile owner trong response.
9. Trả `StoryResponse` cho frontend.

## 10.2. Flow lấy feed story

1. Frontend gọi `GET /api/social/stories/feed`.
2. Social-service lấy `friendIds` qua `FriendRepository.findAllAcceptedFriendIds(userId)`.
3. Social-service query tất cả story active của owner và accepted friends.
4. Social-service load viewed state của current user.
5. Social-service group theo owner.
6. Social-service enrich profile owner từ user-service internal profiles.
7. Trả danh sách `StoryFeedGroupResponse`.

## 10.3. Flow xem story

1. Frontend mở một story cụ thể.
2. Frontend có thể gọi `GET /api/social/stories/{storyId}` nếu cần payload chi tiết.
3. Khi story đã hiển thị đủ điều kiện, frontend gọi `POST /api/social/stories/{storyId}/view`.
4. Social-service insert `StoryViewEntity` nếu chưa tồn tại.
5. Nếu insert thành công lần đầu, tăng `viewCount`.

## 10.4. Flow cleanup story

Story cleanup nên có hai lớp:

### Lớp 1: loại ở query time

Tất cả API đọc phải luôn lọc:

- `isDeleted = false`
- `expiresAt > now`

### Lớp 2: cleanup job nền

Job chạy định kỳ mỗi 10 hoặc 15 phút:

1. tìm story đã hết hạn hoặc đã soft delete quá thời gian grace
2. gom `publicId` từ `StoryMedia`
3. gọi media-service `delete-batch`
4. xóa `StoryViewEntity`
5. xóa `StoryMedia`
6. xóa `StoryEntity`

### Ghi chú kỹ thuật quan trọng

Social-service hiện chưa bật scheduling mặc định, nên phase 1 phải bổ sung:

- `@EnableScheduling`
- `StoryCleanupJob`

Nếu chưa có job này, hệ thống mới chỉ ẩn story ở read path chứ chưa dọn được rác DB và Cloudinary.

---

## 11. Tích hợp với media-service

## 11.1. Vấn đề hiện tại

Hiện `CloudinaryMediaService` đang hard-code logic cho post:

- folder: `social-media/posts/{userId}`
- tags: `post,social-service,{userId}`
- `my-uploads` cũng đọc theo prefix post

## 11.2. Thiết kế nên làm

Generalize theo `resourceType`.

Ví dụ:

- `post` -> folder `social-media/posts/{userId}`
- `story` -> folder `social-media/stories/{userId}`

Tag gợi ý:

- `post`
- `story`
- `social-service`
- `{userId}`

## 11.3. API nên sửa

- `POST /api/media/upload?resourceType=story`
- `GET /api/media/my-uploads?resourceType=story`

## 11.4. Vì sao phải sửa cả `my-uploads`

Nếu chỉ sửa upload mà không sửa `my-uploads`:

- frontend sẽ không đọc lại được story asset đúng prefix
- admin/debug flow dễ nhìn nhầm asset story thành không tồn tại
- contract media-service bị nửa vời

---

## 12. Tích hợp với user-service

Story feed và viewer list đều cần profile để render tốt.

Do social-service hiện đã có `UserProfileClient`, hướng phù hợp nhất là:

- phase 1 enrich ngay tại backend bằng `/api/users/internal/profiles`
- nếu user-service lỗi thì degrade các field profile về `null`, nhưng core story data vẫn trả

### Không nên lấy hướng mặc định sau nữa

- social-service chỉ trả `userId`
- frontend tự join dữ liệu cho mọi story request

Lý do:

- social-service hiện đã theo pattern enrich backend-side cho post, comment, friend, community
- nếu story đi theo hướng khác, frontend contract sẽ không đồng đều với phần còn lại của hệ thống

---

## 13. Tích hợp với notification-service

Khuyến nghị phase 1:

- không phát event `story created`
- không fanout notification tới accepted friends

### Lý do

1. dễ spam notification
2. chi phí event và lưu notification tăng mạnh
3. UX story chủ yếu đi qua ring, không phải notification center

### Nếu product bắt buộc ở phase sau

Có thể thêm event kiểu:

```json
{
  "eventType": "SOCIAL_STORY_CREATED",
  "storyId": 101,
  "actorId": "user123",
  "recipientId": "user456",
  "deeplink": "/stories/101"
}
```

Nhưng đây không phải default của phase 1.

---

## 14. Điểm khác biệt giữa Post và Story

| Chủ đề | Post hiện tại | Story đề xuất |
| --- | --- | --- |
| Thời gian sống | lâu dài | 24 giờ |
| Feed | timeline | group theo owner |
| Visibility | `PUBLIC`, `FRIEND`, `PRIVATE` | giữ nguyên 3 mức này |
| Media | nhiều item | phase 1 tối đa 1 item |
| Reaction/comment | đã có | chưa làm phase 1 |
| Viewer tracking | không có | bắt buộc |
| Cleanup | xóa theo flow nghiệp vụ | cần cleanup job định kỳ |
| Profile enrich | backend đã làm | backend nên tiếp tục làm |

Kết luận:

- không reuse `PostEntity`
- không reuse trực tiếp `PostCreateRequest`
- nên có story domain riêng nhưng theo coding pattern của post

---

## 15. Thành phần backend nên thêm

Trong `social-media-social-service`:

- `entities/StoryEntity.java`
- `entities/StoryMedia.java`
- `entities/StoryViewEntity.java`
- `repositories/StoryRepository.java`
- `repositories/StoryMediaRepository.java`
- `repositories/StoryViewRepository.java`
- `dto/StoryDTO/StoryCreateRequest.java`
- `dto/StoryDTO/StoryMediaRequest.java`
- `dto/StoryDTO/StoryResponse.java`
- `dto/StoryDTO/StoryFeedGroupResponse.java`
- `dto/StoryDTO/StoryViewResponse.java`
- `helpers/StoryHelper.java`
- `service/StoryService.java`
- `controllers/StoryController.java`
- `job/StoryCleanupJob.java`

Trong `social-media-media-service`:

- chỉnh `MediaController`
- chỉnh `CloudinaryMediaService`
- thêm enum hoặc resolver cho `resourceType`

Ngoài ra trong `social-media-social-service` nên bổ sung bật scheduling ở application config.

---

## 16. Kế hoạch thực hiện theo phase

## 16.1. Phase 1: MVP chạy được end-to-end

Mục tiêu:

- user tạo story
- user xem story ring
- user mở story của mình và bạn bè
- system ghi nhận đã xem
- story tự ẩn sau 24h và được cleanup nền

Việc cần làm:

1. thêm story entity, media entity, view entity trong social-service
2. thêm repository và helper cho story
3. thêm `POST /api/social/stories`
4. thêm `GET /api/social/stories/feed`
5. thêm `GET /api/social/stories/users/{userId}`
6. thêm `GET /api/social/stories/{storyId}`
7. thêm `POST /api/social/stories/{storyId}/view`
8. thêm `DELETE /api/social/stories/{storyId}`
9. thêm backend enrichment profile bằng `UserProfileClient`
10. generalize media upload và `my-uploads` cho `resourceType=story`
11. thêm cleanup job và bật scheduling

Kết quả mong đợi:

- frontend có thể build story ring và story viewer cơ bản
- data story không bị lẫn với media post
- story hết hạn không còn xuất hiện ở API đọc

## 16.2. Phase 1.1: Hoàn thiện trải nghiệm

Mục tiêu:

- chủ story xem được ai đã xem
- ring sort hợp lý hơn
- contract ổn định hơn khi dependency lỗi nhẹ

Việc cần làm:

1. thêm `GET /api/social/stories/{storyId}/viewers`
2. enrich viewer list bằng user-service internal profiles
3. tối ưu sort `hasUnseen` và `latestStoryAt`
4. bổ sung fallback profile null-safe nhất quán
5. bổ sung test cho idempotent view và cleanup

## 16.3. Phase 2: Mở rộng sản phẩm

Việc cân nhắc:

1. story reaction
2. story reply
3. close friends story
4. highlight story
5. optional notification story
6. public profile story ngoài friend graph

---

## 17. Rủi ro chính và cách xử lý

## Rủi ro 1: story hết hạn nhưng chưa được xóa vật lý

Giải pháp:

- luôn lọc `expiresAt > now` ở read path
- chạy cleanup job định kỳ

## Rủi ro 2: media rác trên Cloudinary

Giải pháp:

- gom `publicId` từ `StoryMedia`
- gọi `delete-batch`
- tách folder `stories/{userId}` để cleanup chính xác

## Rủi ro 3: `/view` bị gọi nhiều lần do retry hoặc nhiều tab

Giải pháp:

- unique constraint `story_id + viewer_id`
- chỉ tăng `viewCount` khi insert viewer thành công lần đầu

## Rủi ro 4: user-service lỗi tạm thời làm feed thiếu profile

Giải pháp:

- story core data vẫn trả về bình thường
- profile field có thể null tạm thời
- log warning giống pattern hiện có của `UserProfileClient`

## Rủi ro 5: contract media-service nửa vời nếu chỉ sửa upload

Giải pháp:

- sửa cả upload lẫn `my-uploads`
- chuẩn hóa resolver theo `resourceType`

## Rủi ro 6: hiểu sai rule `PUBLIC`

Giải pháp:

- chốt rõ từ đầu rằng direct access và feed ring là hai rule khác nhau

---

## 18. Kết luận

Hướng phù hợp nhất với codebase hiện tại là:

- đặt story trong `social-media-social-service`
- dùng domain riêng thay vì reuse `PostEntity`
- tiếp tục enrich profile ở backend bằng user-service internal endpoints
- generalize media-service theo `resourceType=story`
- dùng `expiresAt` cộng với cleanup job thay cho TTL tự động
- chưa tích hợp notification cho `story created` ở phase 1

Thiết kế này đạt các mục tiêu quan trọng:

1. bám sát codebase hiện tại thay vì giả định cũ
2. triển khai nhanh trên social graph và auth flow đang có
3. giữ contract đủ sạch để mở rộng ở phase 1.1 và phase 2
