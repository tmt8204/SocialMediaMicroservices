# Thiết kế nghiệp vụ Community

## 1. Mục tiêu tài liệu

Tài liệu này viết lại nghiệp vụ Community theo đúng hướng của codebase hiện tại:

- bám sát kiến trúc microservice Java/Spring đang có
- tận dụng `social-media-social-service`, `media-service`, `user-service`, `api-gateway`
- không mô tả như thể feature đã tồn tại sẵn trong code
- đủ rõ để backend có thể implement theo phase

Community ở đây được hiểu là:

- người dùng tạo một cộng đồng có tên, mô tả, ảnh cover
- cộng đồng có thể `PUBLIC` hoặc `PRIVATE`
- người dùng có thể join, chờ duyệt, rời nhóm
- community có danh sách thành viên và role quản trị
- community có feed bài viết riêng

Hiện tại repo chưa có entity, repository, service hay controller nào cho community. File cũ chỉ là một đoạn code Node.js tham khảo nghiệp vụ, không phản ánh implementation của hệ Java hiện tại.

---

## 2. Hiện trạng code sau khi đọc dự án

### 2.1. `social-media-social-service`

Hiện service này đã có:

- post CRUD
- comment cho post
- reaction cho post
- friendship lifecycle hoàn chỉnh
- user profile enrichment qua `UserProfileClient`
- social notification event producer qua Kafka

Điểm quan trọng để thiết kế community:

- persistence hiện dùng SQL Server + JPA, không dùng MongoDB
- `BaseEntity` đang chuẩn hóa `id`, `createdAt`, `updatedAt`
- `PostEntity` đã có media, comment, reaction, visibility và soft-delete
- `FriendService` và `FriendRepository` đã xử lý social graph 2 chiều khá đầy đủ

Điều này có nghĩa là community nên reuse cùng pattern controller/service/repository/dto như social-service hiện tại, thay vì bê nguyên thiết kế Node.js cũ.

### 2.2. `social-media-media-service`

Hiện media-service đã có:

- upload ảnh/video
- trả metadata `publicId`, `mediaUrl`, `mediaType`, `provider`, `width`, `height`, `bytes`
- delete single và delete batch

Community không nên upload file nhị phân trực tiếp qua social-service. Flow nên giữ nguyên:

1. frontend upload sang media-service
2. lấy metadata trả về
3. gửi metadata vào social-service khi tạo post community hoặc cập nhật cover

### 2.3. `social-media-user-service`

User-service hiện đã có internal profile lookup để enrich dữ liệu social. Community có thể reuse cho:

- creator profile
- member list
- author profile của community post

### 2.4. `social-media-api-gateway`

Gateway hiện validate JWT và inject:

- `X-User-Id`
- `X-Role`

Community nên dùng đúng cơ chế này giống friend/post hiện tại. Không cần parse JWT riêng trong social-service.

### 2.5. `social-media-notification-service`

Notification-service hiện phù hợp cho những event đích danh theo `recipientId`.

Với community phase đầu, không nên phát notification cho mọi bài đăng trong community vì:

- số recipient có thể lớn
- dễ spam hơn post cá nhân
- chưa có product rule rõ cho ai cần nhận

Kết luận: phase đầu của community nên ưu tiên CRUD + membership + feed riêng. Notification chỉ nên cân nhắc ở phase sau.

---

## 3. Kết luận kiến trúc

## Khuyến nghị chính

Nghiệp vụ Community nên được đặt trong `social-media-social-service`.

### Lý do

1. Community post tái sử dụng mạnh comment/reaction/media đang có.
2. Membership và feed cộng đồng gần domain với friendship và post.
3. Gateway, profile enrichment và notification producer đều đã nằm ở social-service.
4. Chưa có lý do đủ mạnh để tách thêm `community-service` riêng.

### Không khuyến nghị ở phase hiện tại

- tạo microservice mới cho community
- dùng MongoDB chỉ cho riêng community
- phát notification cho mọi community post
- tạo một hệ post riêng hoàn toàn tách khỏi `PostEntity` nếu chưa cần

---

## 4. Phạm vi phase 1 nên làm

Phase 1 nên chốt các nghiệp vụ sau:

- tạo community
- cập nhật metadata community
- lấy community detail
- lấy danh sách community của tôi
- lấy community suggestions hoặc discover list
- join community public hoặc gửi join request vào private community
- admin duyệt hoặc từ chối request
- rời community
- xoá mềm community
- lấy danh sách member
- tạo và đọc community post

Các nghiệp vụ chưa nên đưa vào phase 1:

- moderator management đầy đủ
- transfer ownership
- community invite
- pin announcement
- hashtag/search nâng cao trong community
- chat riêng cho community
- notification fanout cho mọi member
- recommendation feed riêng cho community

---

## 5. Thiết kế domain đề xuất

## 5.1. `CommunityEntity`

Đề xuất thêm bảng `communities` trong SQL Server.

```java
@Entity
@Table(name = "communities")
public class CommunityEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "privacy", nullable = false, columnDefinition = "nvarchar(50) default 'PUBLIC'")
    private String privacy;

    @Column(name = "member_count", columnDefinition = "int default 1")
    private int memberCount;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "is_deleted", columnDefinition = "bit default 0")
    private boolean isDeleted = false;
}
```

### Ghi chú

- `privacy` nên dùng enum `PUBLIC`, `PRIVATE`
- `memberCount` là denormalized field để list/discover nhanh
- `createdBy` nên là `userId` string để match style các entity social hiện tại
- `isDeleted` dùng cho soft delete giống pattern hiện có

## 5.2. `CommunityMemberEntity`

Đề xuất thêm bảng `community_members`.

```java
@Entity
@Table(
    name = "community_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "user_id"})
)
public class CommunityMemberEntity extends BaseEntity {

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "role", nullable = false, columnDefinition = "nvarchar(50) default 'MEMBER'")
    private String role;

    @Column(name = "status", nullable = false, columnDefinition = "nvarchar(50) default 'APPROVED'")
    private String status;

    @Column(name = "joined_at")
    private Date joinedAt;
}
```

### Enum đề xuất

```java
public enum CommunityRole {
    ADMIN,
    MODERATOR,
    MEMBER
}

public enum CommunityMemberStatus {
    PENDING,
    APPROVED,
    REJECTED,
    LEFT,
    REMOVED
}
```

### Ghi chú

- Phase 1 có thể chỉ cần quyền thực tế cho `ADMIN` và `MEMBER`
- `MODERATOR` có thể giữ sẵn trong enum để mở rộng sau
- không nên hard delete mọi membership cũ nếu muốn giữ lịch sử thao tác; tuy nhiên để implementation đơn giản phase đầu vẫn có thể xóa record `REJECTED/LEFT` nếu team muốn

## 5.3. Reuse `PostEntity` cho community post

Không nên tạo một entity post hoàn toàn riêng cho community ngay ở phase đầu.

Khuyến nghị mở rộng `PostEntity` hiện có:

```java
@Column(name = "community_id")
private Long communityId;

@Column(name = "post_context", columnDefinition = "nvarchar(50) default 'PROFILE'")
private String postContext;
```

Enum:

```java
public enum PostContextType {
    PROFILE,
    COMMUNITY
}
```

### Lý do reuse `PostEntity`

1. Comment và reaction hiện đã bám vào `postId`.
2. Media của post đang làm ổn.
3. Community post về bản chất vẫn là một post có owner và có feed riêng.
4. Giảm số lượng service/repository/controller phải nhân đôi.

### Rule quan trọng

- post cá nhân: `communityId = null`, `postContext = PROFILE`
- post cộng đồng: `communityId != null`, `postContext = COMMUNITY`
- query newsfeed hiện tại phải loại trừ `postContext = COMMUNITY`
- query community feed phải lọc theo `communityId`

---

## 6. Quy tắc nghiệp vụ đề xuất

## 6.1. Tạo community

Khi user tạo community:

- tạo `CommunityEntity`
- set `createdBy = currentUserId`
- set `memberCount = 1`
- tự tạo `CommunityMemberEntity` cho creator với:
  - `role = ADMIN`
  - `status = APPROVED`
  - `joinedAt = now`

Không cho tạo community nếu:

- tên trống
- tên vượt giới hạn độ dài
- community đã bị tạo trùng theo policy nếu team muốn enforce uniqueness theo name

## 6.2. Join community

Nếu community là `PUBLIC`:

- tạo member với `status = APPROVED`
- tăng `memberCount`

Nếu community là `PRIVATE`:

- tạo member với `status = PENDING`
- chưa tăng `memberCount`

Không cho join nếu:

- community không tồn tại hoặc đã soft delete
- user đã có membership `APPROVED` hoặc `PENDING`

## 6.3. Approve hoặc reject join request

Chỉ `ADMIN` mới được duyệt request ở phase 1.

Approve:

- chuyển `status = APPROVED`
- set `joinedAt = now` nếu cần
- tăng `memberCount`

Reject:

- có thể update `status = REJECTED`
- hoặc xóa record pending để đơn giản hóa dữ liệu

Khuyến nghị pragmatic:

- approve thì update status
- reject thì update status thay vì delete để còn audit

## 6.4. Xem community detail

Community detail nên trả:

- metadata community
- `myRole`
- `myStatus`
- `isMember`
- `canManage`

Cho phép đọc metadata nếu community chưa bị xóa.

Riêng danh sách post và danh sách request cần check quyền riêng.

## 6.5. Xem community posts

Nếu community là `PUBLIC`:

- mọi user authenticated đều có thể xem community feed

Nếu community là `PRIVATE`:

- chỉ member `APPROVED` mới xem được

Community post không nên tự động xuất hiện trong personal feed `GET /api/social/feed` ở phase đầu. Community feed là một luồng riêng.

## 6.6. Tạo community post

Chỉ member `APPROVED` mới được đăng bài trong community.

Khi tạo:

- tạo post với `postContext = COMMUNITY`
- set `communityId`
- vẫn reuse media/comment/reaction như post thường

Community post không nên dùng `visibility` tự do theo từng bài trong phase đầu. Quyền đọc nên phụ thuộc vào privacy của community.

Khuyến nghị:

- backend vẫn giữ field `visibility` để không phá DTO cũ
- với community post, set `visibility` theo community privacy hoặc chuẩn hóa về một giá trị nội bộ cố định

## 6.7. Leave community

Member thường có thể leave.

Admin không nên leave nếu đó là admin cuối cùng, vì sẽ làm community mất owner quản trị.

Phase 1 có thể chốt rule đơn giản:

- `ADMIN` không được leave, phải delete community hoặc transfer ownership ở phase sau

## 6.8. Delete community

Chỉ `ADMIN` được delete.

Khuyến nghị xử lý:

- set `community.isDeleted = true`
- bulk set `isDeleted = true` cho toàn bộ community post

Lý do bulk mark post deleted:

- tránh leak post nếu có query quên join với community
- đồng nhất behavior với file thiết kế cũ

---

## 7. API đề xuất

Base path nên đặt dưới social-service:

- `/api/social/communities`

## 7.1. Community endpoints

| Method | URL | Mô tả |
| --- | --- | --- |
| POST | `/api/social/communities` | Tạo community |
| GET | `/api/social/communities/mine` | Lấy community tôi tạo / đã tham gia |
| GET | `/api/social/communities/discover?page=0&size=10` | Gợi ý community |
| GET | `/api/social/communities/{communityId}` | Lấy detail community |
| POST | `/api/social/communities/{communityId}/join` | Join hoặc gửi request |
| POST | `/api/social/communities/{communityId}/leave` | Rời community |
| GET | `/api/social/communities/{communityId}/members?page=0&size=20` | Danh sách thành viên |
| GET | `/api/social/communities/{communityId}/requests?page=0&size=20` | Danh sách join request pending |
| PUT | `/api/social/communities/{communityId}/requests/{userId}/approve` | Duyệt request |
| PUT | `/api/social/communities/{communityId}/requests/{userId}/reject` | Từ chối request |
| PUT | `/api/social/communities/{communityId}/cover` | Cập nhật cover |
| PUT | `/api/social/communities/{communityId}/privacy` | Cập nhật privacy |
| DELETE | `/api/social/communities/{communityId}` | Soft delete community |

## 7.2. Community post endpoints

| Method | URL | Mô tả |
| --- | --- | --- |
| GET | `/api/social/communities/{communityId}/posts?page=0&size=10` | Lấy feed bài viết của community |
| POST | `/api/social/communities/{communityId}/posts` | Tạo bài viết trong community |

Comment và reaction có thể reuse endpoint post hiện có, miễn là `postId` thuộc community và user có quyền đọc/post tương ứng.

---

## 8. DTO đề xuất

## 8.1. `CreateCommunityRequest`

```json
{
  "name": "Java Spring Vietnam",
  "description": "Noi chia se ve Spring Boot va microservices",
  "coverUrl": "https://cdn.example.com/community-cover.jpg",
  "privacy": "PUBLIC"
}
```

## 8.2. `CommunityResponse`

```json
{
  "id": 12,
  "name": "Java Spring Vietnam",
  "description": "Noi chia se ve Spring Boot va microservices",
  "coverUrl": "https://cdn.example.com/community-cover.jpg",
  "privacy": "PUBLIC",
  "memberCount": 128,
  "createdBy": "userA",
  "createdByUsername": "nguyenvana",
  "createdByFullName": "Nguyen Van A",
  "createdByAvatarUrl": "https://cdn.example.com/a.jpg",
  "myRole": "ADMIN",
  "myStatus": "APPROVED",
  "isMember": true,
  "canManage": true,
  "createdAt": "2026-04-07T08:00:00.000+00:00",
  "updatedAt": "2026-04-07T08:00:00.000+00:00"
}
```

## 8.3. `CommunityMemberResponse`

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

## 8.4. `CommunityOverviewResponse`

Phù hợp cho `mine` hoặc `discover`:

```json
{
  "id": 12,
  "name": "Java Spring Vietnam",
  "coverUrl": "https://cdn.example.com/community-cover.jpg",
  "privacy": "PUBLIC",
  "memberCount": 128,
  "myRole": null,
  "myStatus": "NOT_JOINED"
}
```

## 8.5. `CreateCommunityPostRequest`

Có thể reuse gần như `PostCreateRequest` hiện tại:

```json
{
  "content": "Chao mung moi nguoi den voi community",
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

## 9. Repository và service cần thêm

## 9.1. Repository

Nên thêm:

- `CommunityRepository`
- `CommunityMemberRepository`

Một số query cần có:

```java
Optional<CommunityEntity> findByIdAndIsDeletedFalse(Long communityId);

Page<CommunityEntity> findByIsDeletedFalseOrderByMemberCountDesc(Pageable pageable);

Page<CommunityMemberEntity> findByUserIdAndStatusOrderByUpdatedAtDesc(String userId, CommunityMemberStatus status, Pageable pageable);

Optional<CommunityMemberEntity> findByCommunityIdAndUserId(Long communityId, String userId);

Page<CommunityMemberEntity> findByCommunityIdAndStatusOrderByJoinedAtAsc(Long communityId, CommunityMemberStatus status, Pageable pageable);
```

## 9.2. Service

Nên tách `CommunityService` với các method chính:

```java
createCommunity(String userId, CreateCommunityRequest request)
getMyCommunities(String userId)
discoverCommunities(String userId, Pageable pageable)
getCommunityDetail(String userId, Long communityId)
joinCommunity(String userId, Long communityId)
leaveCommunity(String userId, Long communityId)
approveJoinRequest(String adminId, Long communityId, String targetUserId)
rejectJoinRequest(String adminId, Long communityId, String targetUserId)
getMembers(String userId, Long communityId, Pageable pageable)
updateCover(String userId, Long communityId, String coverUrl)
updatePrivacy(String userId, Long communityId, String privacy)
deleteCommunity(String userId, Long communityId)
```

Community post có thể đặt ở `PostService` hoặc tách helper riêng, nhưng nên có entry method rõ ràng như:

```java
createCommunityPost(String userId, Long communityId, PostCreateRequest request)
getCommunityPosts(String userId, Long communityId, Pageable pageable)
```

---

## 10. Tác động đến code hiện tại

## 10.1. `PostEntity` và query feed

Nếu reuse `PostEntity`, cần sửa các query hiện tại để tránh community post lọt vào personal feed:

- `getOwnerPost(...)` chỉ lấy `postContext = PROFILE`
- `getFeed(...)` chỉ lấy `postContext = PROFILE`
- thêm query mới cho `communityId`

Đây là điểm quan trọng nhất về mặt root cause. Nếu bỏ sót, community post sẽ lẫn vào luồng post cá nhân.

## 10.2. `CommentService` và `ReactionService`

Hai service này có thể reuse gần như nguyên trạng, nhưng trước khi xử lý theo `postId`, cần đảm bảo:

- post không bị delete
- nếu là community post thì current user có quyền xem/post trong community đó

## 10.3. `UserProfileClient`

Có thể reuse để enrich:

- creator profile
- member profile
- author profile của community post

---

## 11. Notification phase 1

Khuyến nghị phase 1 chưa publish notification cho các luồng sau:

- community mới được tạo
- join request mới
- join request được approve
- community post mới

Lý do:

- scope backend đã đủ lớn với membership + feed + permission
- chưa có rule sản phẩm rõ về ai là recipient hợp lệ
- event community dễ fanout lớn hơn post cá nhân

Nếu cần phase 2, các event hợp lý hơn sẽ là:

- `COMMUNITY_JOIN_REQUEST_CREATED`
- `COMMUNITY_JOIN_REQUEST_APPROVED`
- `COMMUNITY_POST_CREATED_FOR_ADMIN_ONLY` hoặc announcement event có chủ đích

---

## 12. Thứ tự implement khuyến nghị

1. Tạo `CommunityEntity`, `CommunityMemberEntity`, enum và repository.
2. Implement `CommunityService` cho create/detail/join/approve/leave/delete.
3. Thêm controller `/api/social/communities`.
4. Mở rộng `PostEntity` và `PostRepository` để support community post.
5. Thêm endpoint community post list/create.
6. Bổ sung check quyền trong comment/reaction khi post thuộc community.
7. Cuối cùng mới cân nhắc notification hoặc moderator flow.

---

## 13. Hướng dẫn làm từng giai đoạn

Phần này chuyển tài liệu từ mức thiết kế sang mức triển khai thực tế. Mỗi giai đoạn nên đủ nhỏ để có thể code, test, và review độc lập.

## Giai đoạn 1: Dựng domain và persistence

Mục tiêu:

- tạo khung dữ liệu cho community
- chưa đụng vào community post
- chưa cần frontend tích hợp đầy đủ

Việc cần làm:

1. Tạo `CommunityEntity`.
2. Tạo `CommunityMemberEntity`.
3. Tạo enum `CommunityRole`, `CommunityMemberStatus`, và nếu cần thì `CommunityPrivacy`.
4. Tạo `CommunityRepository` và `CommunityMemberRepository`.
5. Viết migration SQL hoặc để Hibernate generate schema theo cách team đang dùng.

Checklist kỹ thuật:

- có unique constraint cho cặp `community_id + user_id`
- có index cho `created_by`, `privacy`, `member_count`, `community_id`, `user_id`, `status`
- `memberCount` và `isDeleted` có default value rõ ràng

Definition of done:

- app start được
- schema lên đúng
- repository query cơ bản chạy được

## Giai đoạn 2: CRUD community và membership cơ bản

Mục tiêu:

- có thể tạo community
- có thể xem detail
- có thể join public hoặc tạo pending request cho private
- có thể leave và delete community

Việc cần làm:

1. Tạo DTO: `CreateCommunityRequest`, `UpdateCommunityCoverRequest`, `UpdateCommunityPrivacyRequest`, `CommunityResponse`, `CommunityOverviewResponse`.
2. Implement `CommunityService` cho:
  - `createCommunity`
  - `getCommunityDetail`
  - `joinCommunity`
  - `leaveCommunity`
  - `deleteCommunity`
  - `updateCover`
  - `updatePrivacy`
3. Tạo `CommunityController` với base path `/api/social/communities`.
4. Reuse `UserProfileClient` để enrich creator profile.
5. Chuẩn hóa error message theo style hiện tại của social-service.

Rule nên khóa ngay từ giai đoạn này:

- không join community đã bị soft delete
- không join lại khi đang `APPROVED` hoặc `PENDING`
- `ADMIN` không được leave
- chỉ `ADMIN` được update cover, privacy, delete community

Definition of done:

- Postman test được luồng create/detail/join/leave/delete
- `memberCount` tăng giảm đúng
- community private trả `PENDING` đúng khi join

## Giai đoạn 3: Quản lý request và member list

Mục tiêu:

- admin quản lý join request
- có member list để frontend render màn hình community

Việc cần làm:

1. Thêm DTO: `CommunityMemberResponse`, `CommunityJoinRequestResponse`, `CommunityActionResponse`.
2. Implement service cho:
  - `getMembers`
  - `getPendingRequests`
  - `approveJoinRequest`
  - `rejectJoinRequest`
  - `getMyCommunities`
  - `discoverCommunities`
3. Enrich profile member qua `UserProfileClient`.
4. Bổ sung paging cho members, requests, discover.

Rule nên chốt:

- chỉ `ADMIN` xem được request pending
- private community chỉ member `APPROVED` mới xem được members nếu team muốn strict privacy
- `approve` mới tăng `memberCount`
- `reject` không tăng `memberCount`

Definition of done:

- frontend có đủ data để render tab `About`, `Members`, `Requests`
- admin duyệt request không tạo membership duplicate

## Giai đoạn 4: Community post

Mục tiêu:

- có feed riêng cho community
- member approved có thể đăng bài
- comment/reaction reuse được trên community post

Việc cần làm:

1. Mở rộng `PostEntity` với `communityId` và `postContext`.
2. Cập nhật `PostRepository` để tách query:
  - post cá nhân
  - newsfeed cá nhân
  - community posts
3. Thêm service entry point:
  - `createCommunityPost`
  - `getCommunityPosts`
4. Tạo endpoint:
  - `POST /api/social/communities/{communityId}/posts`
  - `GET /api/social/communities/{communityId}/posts`
5. Chặn community post lọt vào `GET /api/social/feed` và `GET /api/social/user/posts`.
6. Thêm permission check vào `CommentService` và `ReactionService` khi post thuộc community.

Rule bắt buộc:

- chỉ member `APPROVED` được tạo post
- private community chỉ member `APPROVED` được xem post
- delete community phải làm community post invisible ngay

Definition of done:

- community post không lẫn vào personal feed
- comment/reaction trên community post hoạt động đúng quyền
- media upload flow vẫn giữ nguyên qua media-service

## Giai đoạn 5: Hoàn thiện cho frontend và hardening

Mục tiêu:

- đủ ổn định để frontend tích hợp thật
- xử lý các góc cạnh của paging, validation, permission

Việc cần làm:

1. Viết thêm tài liệu frontend integration cho community.
2. Bổ sung test cho:
  - join public/private
  - approve/reject
  - leave/delete
  - community post visibility
3. Kiểm tra concurrency cho `memberCount`.
4. Tối ưu query discover và member list.
5. Rà soát các nơi đang query post để không quên filter `postContext`.

Nên test tối thiểu các case sau:

- user A tạo community private
- user B join và thành `PENDING`
- user A approve B
- user B xem được feed và tạo post
- user C không phải member thì bị chặn khỏi community private
- delete community xong thì post không còn hiện ở community feed

Definition of done:

- có tài liệu đủ cho FE dùng
- test luồng chính pass
- không có regression ở post/feed/comment/reaction hiện tại

## Gợi ý chia PR

Để review dễ hơn, nên tách tối thiểu như sau:

1. PR 1: entity + repository + migration.
2. PR 2: community CRUD + join/leave/delete.
3. PR 3: members + requests + discover/mine.
4. PR 4: community post + feed query changes.
5. PR 5: hardening, test, docs frontend.

## 14. Kết luận

Community nên được thiết kế như một phần mở rộng tự nhiên của `social-media-social-service`, không phải một service mới. Hướng khả thi nhất ở codebase hiện tại là:

- thêm `CommunityEntity` và `CommunityMemberEntity`
- reuse `PostEntity` cho community post bằng `communityId` + `postContext`
- tách feed community khỏi feed cá nhân
- giữ notification ngoài phase đầu để scope vừa đủ

Thiết kế này bám sát hệ Java/Spring hiện tại hơn nhiều so với file Node.js cũ, đồng thời vẫn giữ được những flow cốt lõi của community: tạo nhóm, join/duyệt, member management, và post trong cộng đồng.
