1) Hiện trạng sau khi đọc code
`social-service`

Phần `friends` hiện mới có skeleton dữ liệu, chưa có flow nghiệp vụ hoàn chỉnh:

- `FriendEntity` đang có 3 field chính:
  - `userId`
  - `friendTo`
  - `status`
- `FriendRepository` hiện mới có 2 query:
  - `findAcceptedFriendsIds(String userId)`
  - `findUserIdsWhoAddedAsFollowers(String userId)`
- Chưa thấy:
  - `FriendController`
  - `FriendService`
  - DTO request/response cho friend request
  - API gửi lời mời / accept / reject / unfriend / list friends

Điểm đang dùng thật trong code:

- `PostService.getFeed()` đang gọi `friendRepository.findAcceptedFriendsIds(userId)` để lấy danh sách bạn bè phục vụ lọc feed.

Vấn đề hiện tại:

- query này mới lấy 1 chiều: `f.userId = :userId -> f.friendTo`
- nếu quan hệ bạn bè được lưu theo 1 record duy nhất và user hiện tại là người nhận lời mời (`friendTo`), thì feed có thể bị thiếu bài viết `FRIEND` từ phía còn lại

=> Kết luận: phần model `friends` đã có nền, nhưng method nghiệp vụ vẫn chưa hoàn thiện.

2) Mục tiêu
Hoàn thiện đầy đủ lifecycle cho quan hệ bạn bè:

- gửi lời mời kết bạn
- chấp nhận lời mời
- từ chối lời mời
- huỷ lời mời đã gửi
- huỷ kết bạn
- lấy danh sách bạn bè
- lấy danh sách lời mời đến / đã gửi
- kiểm tra trạng thái quan hệ giữa 2 user

Đồng thời đảm bảo:

- feed dùng đúng danh sách `ACCEPTED` theo cả 2 chiều
- không tạo request trùng
- không cho tự kết bạn với chính mình
- frontend dễ render nút `Add Friend / Cancel / Accept / Friends`

3) Kiến trúc đề xuất
Phương án khuyến nghị

Giữ nghiệp vụ `friends` trong `social-service`.

Lý do:

- quan hệ bạn bè đang được dùng trực tiếp cho newsfeed trong `PostService`
- cùng domain với `visibility = FRIEND`
- tránh phải query chéo service nhiều lần khi load feed

Cách lưu dữ liệu khuyến nghị

Dùng **1 record duy nhất cho mỗi cặp user**.

Quy ước:

- `userId`: người gửi lời mời
- `friendTo`: người nhận lời mời
- `status`: `PENDING`, `ACCEPTED`, `REJECTED`, `BLOCKED`, `CANCELLED` (tuỳ mức cần dùng)

Flow đề xuất

- A gửi lời mời cho B
  - tạo record `A -> B`, `status = PENDING`
- B chấp nhận
  - update record thành `ACCEPTED`
- B từ chối
  - update record thành `REJECTED`
- A huỷ lời mời khi còn pending
  - update `CANCELLED` hoặc xoá record
- A/B unfriend
  - xoá relation hoặc update `UNFRIENDED`

Khuyến nghị thêm:

- nếu B đã gửi request cho A trước đó mà A tiếp tục bấm `Add Friend`, có thể **auto-accept** luôn thay vì tạo record mới

Cách này sạch hơn vì:

- không bị duplicate 2 dòng cho cùng một quan hệ
- dễ kiểm soát trạng thái
- phù hợp cho `relationship status`
- dễ mở rộng block/report về sau

4) Thay đổi cần làm ở `social-service`
4.1. `FriendEntity`
Hiện tại đang có:

```java
private String userId;
private String friendTo;
private String status;
```

Nên mở rộng thêm:

```java
private Date requestedAt;
private Date respondedAt;
private String actionBy;
```

Hoặc ít nhất nên làm rõ semantics bằng enum:

```java
public enum FriendStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    BLOCKED
}
```

Bắt buộc nên có ràng buộc tránh duplicate relation.

Khuyến nghị:

- thêm unique logic cho mỗi cặp user
- nếu DB khó unique theo 2 chiều, có thể chuẩn hoá một `pairKey`, ví dụ:

```java
pairKey = min(userA, userB) + ":" + max(userA, userB)
```

và đặt unique constraint trên `pairKey`

4.2. `FriendRepository`
Nên bổ sung các method sau:

```java
Optional<FriendEntity> findRelationshipBetween(String userA, String userB);
Page<FriendEntity> findIncomingPendingRequests(String userId, Pageable pageable);
Page<FriendEntity> findOutgoingPendingRequests(String userId, Pageable pageable);
List<String> findAllAcceptedFriendIds(String userId);
boolean existsAcceptedFriendship(String userA, String userB);
```

Query quan trọng nhất là lấy bạn bè theo **2 chiều**:

```java
@Query("""
SELECT CASE
         WHEN f.userId = :userId THEN f.friendTo
         ELSE f.userId
       END
FROM FriendEntity f
WHERE (f.userId = :userId OR f.friendTo = :userId)
  AND f.status = 'ACCEPTED'
""")
List<String> findAllAcceptedFriendIds(@Param("userId") String userId);
```

Đây là method cần thiết để thay cho `findAcceptedFriendsIds(userId)` hiện tại khi build feed.

4.3. `FriendService`
Nên tách nghiệp vụ thành các method rõ ràng:

```java
sendFriendRequest(String userId, String targetUserId)
acceptFriendRequest(String userId, String requesterId)
rejectFriendRequest(String userId, String requesterId)
cancelFriendRequest(String userId, String targetUserId)
unfriend(String userId, String targetUserId)
getFriends(String userId, Pageable pageable)
getPendingRequests(String userId, String type, Pageable pageable)
getRelationshipStatus(String userId, String targetUserId)
```

Luồng xử lý đề xuất cho từng method

**`sendFriendRequest(userId, targetUserId)`**
- validate `userId != targetUserId`
- check target user có tồn tại hay không (có thể gọi `user-service` nếu cần)
- tìm relation hiện có giữa 2 user
- nếu chưa có -> tạo mới `PENDING`
- nếu đã `PENDING` cùng chiều -> trả về trạng thái đã gửi
- nếu đang có `PENDING` ngược chiều -> auto-accept
- nếu đã `ACCEPTED` -> trả về `already friends`
- nếu `BLOCKED` -> không cho gửi

**`acceptFriendRequest(userId, requesterId)`**
- tìm record `requesterId -> userId` với `status = PENDING`
- nếu không có -> báo `not found` hoặc `invalid state`
- update `status = ACCEPTED`
- set `respondedAt = now`

**`rejectFriendRequest(userId, requesterId)`**
- tìm record pending nhận vào
- update `status = REJECTED`
- set `respondedAt = now`

**`cancelFriendRequest(userId, targetUserId)`**
- chỉ người gửi mới được cancel
- chỉ cancel khi đang `PENDING`
- có thể delete record hoặc update `CANCELLED`

**`unfriend(userId, targetUserId)`**
- tìm relation theo cả 2 chiều
- chỉ xử lý nếu đang `ACCEPTED`
- xoá relation hoặc đổi trạng thái phù hợp

**`getFriends(userId, pageable)`**
- query tất cả relation `ACCEPTED` mà user xuất hiện ở một trong hai đầu
- map sang `otherUserId`
- nếu cần UI đẹp hơn, gọi thêm `user-service` để lấy `fullName`, `avatar`, `username`

**`getRelationshipStatus(userId, targetUserId)`**
- trả về các trạng thái phục vụ frontend:
  - `NOT_FRIEND`
  - `PENDING_SENT`
  - `PENDING_RECEIVED`
  - `FRIEND`
  - `BLOCKED`

4.4. `FriendController`
Đề xuất thêm controller mới:

```java
@RestController
@RequestMapping("/api/social/friends")
public class FriendController {
}
```

5) API nên có
Gửi lời mời kết bạn

`POST /api/social/friends/requests/{targetUserId}`

Response:

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "PENDING_SENT",
  "message": "Friend request sent successfully"
}
```

Chấp nhận lời mời

`PUT /api/social/friends/requests/{requesterId}/accept`

Response:

```json
{
  "userId": "userB",
  "targetUserId": "userA",
  "status": "FRIEND",
  "message": "Friend request accepted"
}
```

Từ chối lời mời

`PUT /api/social/friends/requests/{requesterId}/reject`

Huỷ lời mời đã gửi

`DELETE /api/social/friends/requests/{targetUserId}/cancel`

Huỷ kết bạn

`DELETE /api/social/friends/{targetUserId}`

Lấy danh sách bạn bè

`GET /api/social/friends?page=0&size=10`

Lấy danh sách lời mời

`GET /api/social/friends/requests?type=incoming&page=0&size=10`

hoặc

`GET /api/social/friends/requests?type=outgoing&page=0&size=10`

Kiểm tra trạng thái quan hệ

`GET /api/social/friends/relationship/{targetUserId}`

Response:

```json
{
  "userId": "userA",
  "targetUserId": "userB",
  "status": "PENDING_RECEIVED"
}
```

6) Rule nghiệp vụ nên chốt
- Không cho user tự kết bạn với chính mình.
- Mỗi cặp user chỉ có **1 active relationship** tại một thời điểm.
- Chỉ người nhận mới được `accept/reject` request.
- Chỉ người gửi mới được `cancel` request.
- `unfriend` chỉ áp dụng khi đang `ACCEPTED`.
- Feed chỉ coi là bạn khi `status = ACCEPTED`.
- Nếu có request ngược chiều đang `PENDING`, khuyến nghị auto-accept để tránh duplicate.
- Nếu đã `BLOCKED` thì không cho gửi lại request.

7) Tích hợp với `PostService` và feed
Đây là phần rất quan trọng.

Hiện tại `PostService.getFeed()` đang dùng:

```java
List<String> friendIds = friendRepository.findAcceptedFriendsIds(userId);
```

Nên đổi sang:

```java
List<String> friendIds = friendRepository.findAllAcceptedFriendIds(userId);
```

Mục tiêu:

- lấy đúng toàn bộ bạn bè đã `ACCEPTED` theo cả 2 chiều
- đảm bảo bài viết `visibility = FRIEND` hiển thị đúng
- tránh trường hợp user đã accept request nhưng feed vẫn thiếu bài từ bạn đó

Về sau có thể tái sử dụng logic này cho:

- kiểm tra quyền xem profile
- mutual friends
- friend suggestions
- count friends

8) Kế hoạch triển khai đề xuất
Phase 1 — Hoàn thiện model
- thêm enum/trạng thái chuẩn cho `FriendEntity`
- thêm `pairKey` hoặc unique logic chống duplicate
- bổ sung repository query theo 2 chiều

Phase 2 — Mở API cơ bản
- `sendFriendRequest`
- `acceptFriendRequest`
- `rejectFriendRequest`
- `cancelFriendRequest`
- `unfriend`

Phase 3 — Tích hợp read API
- `getFriends`
- `getPendingRequests`
- `getRelationshipStatus`
- enrich dữ liệu từ `user-service` nếu cần

Phase 4 — Tích hợp feed và hardening
- thay query friendIds trong `PostService`
- test các case gửi chéo, accept, reject, unfriend
- xử lý race condition bằng `@Transactional`
- thêm global error handling cho conflict/invalid state

9) Kết luận
Hiện tại phần `friends` trong codebase mới dừng ở mức **entity + repository cơ bản** và đang được dùng một phần cho feed.

Hướng phù hợp nhất là:

- giữ domain `friends` ở `social-service`
- hoàn thiện bằng `FriendService` + `FriendController`
- dùng **1 record cho mỗi cặp user**
- chuẩn hoá lifecycle `PENDING -> ACCEPTED / REJECTED / CANCELLED`
- sửa query feed để đọc friendship theo cả 2 chiều

Ưu điểm:

- đúng domain với social/feed
- ít coupling hơn
- frontend dễ dùng
- dễ mở rộng block, mutual friends, suggestions về sau

10) Luồng hoạt động hiện tại sau khi apply
Flow thực tế trong code hiện tại đang chạy theo hướng sau:

**Bước 1 — Gửi lời mời kết bạn**
Client gọi:

`POST /api/social/friends/requests/{targetUserId}`

Backend xử lý trong `FriendService.sendFriendRequest(...)`:
- validate `userId` và `targetUserId`
- chặn trường hợp tự gửi lời mời cho chính mình
- tạo `pairKey = min(userA, userB) + ":" + max(userA, userB)`
- tìm quan hệ hiện có bằng `friendRepository.findByPairKey(pairKey)`
- nếu chưa có quan hệ:
  - tạo `FriendEntity`
  - lưu `userId = người gửi`, `friendTo = người nhận`
  - set `status = PENDING`
- nếu đã có quan hệ `PENDING` theo chiều ngược lại:
  - auto-accept luôn
  - update `status = ACCEPTED`
- nếu đã `ACCEPTED`:
  - trả về trạng thái `FRIEND`

**Bước 2 — Chấp nhận lời mời**
Client gọi:

`PUT /api/social/friends/requests/{requesterId}/accept`

Backend xử lý trong `FriendService.acceptFriendRequest(...)`:
- tìm relation giữa 2 user theo cả 2 chiều bằng `findRelationshipBetween(...)`
- chỉ accept nếu record đang là `PENDING` và đúng chiều `requesterId -> currentUser`
- update `status = ACCEPTED`
- cập nhật `actionBy` và `updatedAt`

**Bước 3 — Từ chối lời mời**
Client gọi:

`PUT /api/social/friends/requests/{requesterId}/reject`

Backend xử lý tương tự accept, nhưng đổi trạng thái sang `REJECTED`.

**Bước 4 — Huỷ lời mời đã gửi**
Client gọi:

`DELETE /api/social/friends/requests/{targetUserId}/cancel`

Backend chỉ cho phép huỷ nếu:
- record đang là `PENDING`
- đúng chiều `currentUser -> targetUser`

Sau đó update `status = CANCELLED`.

**Bước 5 — Huỷ kết bạn**
Client gọi:

`DELETE /api/social/friends/{targetUserId}`

Backend:
- tìm relation giữa 2 user
- chỉ cho unfriend khi đang `ACCEPTED`
- update `status = UNFRIENDED`
- response trả về `NOT_FRIEND`

**Bước 6 — Lấy danh sách bạn bè**
Client gọi:

`GET /api/social/friends?page=0&size=10`

Backend:
- query tất cả relation có `status = ACCEPTED`
- hỗ trợ lấy theo cả 2 chiều `userId` / `friendTo`
- map về `otherUserId`, `requestedAt`, `respondedAt`

**Bước 7 — Lấy danh sách request chờ**
Client gọi:

- `GET /api/social/friends/requests?type=incoming`
- hoặc `GET /api/social/friends/requests?type=outgoing`

Backend:
- `incoming`: lấy các request có `friendTo = currentUser` và `status = PENDING`
- `outgoing`: lấy các request có `userId = currentUser` và `status = PENDING`

**Bước 8 — Kiểm tra relationship status**
Client gọi:

`GET /api/social/friends/relationship/{targetUserId}`

Backend trả một trong các trạng thái:
- `NOT_FRIEND`
- `PENDING_SENT`
- `PENDING_RECEIVED`
- `FRIEND`
- `BLOCKED`

**Bước 9 — Tích hợp với newsfeed**
Khi gọi:

`GET /api/social/feed`

`PostService.getFeed()` hiện xử lý như sau:
- gọi `friendRepository.findAllAcceptedFriendIds(userId)` để lấy friend list theo **2 chiều**
- nếu danh sách bạn bè rỗng -> chỉ lấy `PUBLIC posts`
- nếu có bạn bè -> lấy:
  - bài `FRIEND` và `PUBLIC` của bạn bè
  - bài `PUBLIC` của người khác

=> Đây là phần đã fix so với logic cũ chỉ đọc friendship một chiều.

11) Những thay đổi đã apply vào code
Hiện tại phần code đã được bổ sung như sau:

**Controller**
- thêm `FriendController`
- expose đầy đủ endpoint dưới prefix: ` /api/social/friends `

**Service**
- thêm `FriendService`
- triển khai các method:
  - `sendFriendRequest(...)`
  - `acceptFriendRequest(...)`
  - `rejectFriendRequest(...)`
  - `cancelFriendRequest(...)`
  - `unfriend(...)`
  - `getFriends(...)`
  - `getPendingRequests(...)`
  - `getRelationshipStatus(...)`

**Repository**
- thêm `findByPairKey(...)`
- thêm `findRelationshipBetween(...)`
- thêm `findAllAcceptedFriendIds(...)`
- thêm query phân trang cho `incoming/outgoing requests`

**Entity / Model**
- `FriendEntity.status` đã chuyển sang `enum FriendStatus`
- thêm `pairKey` để chống duplicate quan hệ giữa 2 user
- thêm `actionBy` để biết ai là người thao tác cuối cùng

**Feed integration**
- `PostService.getFeed()` đã đổi sang query friendship theo 2 chiều
- `PostRepository` đã có thêm `findPublicFeedPosts(...)` để fallback khi user chưa có bạn bè

**DTO response**
- thêm nhóm DTO cho friends:
  - `FriendActionResponse`
  - `FriendListItemResponse`
  - `FriendRequestResponse`
  - `RelationshipStatusResponse`

12) Ghi chú hiện trạng sau khi triển khai
- Luồng `friends` hiện đang trả về metadata cơ bản theo `userId`, chưa enrich thêm `username`, `fullName`, `avatar` từ `user-service`.
- Timestamp đang tận dụng `createdAt` / `updatedAt` từ `BaseEntity`, chưa tách riêng `requestedAt` / `respondedAt`.
- `BLOCKED` đã có trong enum và status mapping, nhưng hiện chưa có API block/unblock riêng.
- Đây là version phù hợp để frontend triển khai các nút cơ bản: `Add Friend`, `Cancel`, `Accept`, `Friends`.

13) Kết luận sau khi apply
Phần `friends` hiện đã đi từ mức skeleton sang mức có thể dùng được cho flow cơ bản:

- gửi lời mời
- accept / reject
- cancel request
- unfriend
- list bạn bè
- list pending requests
- check relationship status
- feed đọc đúng bạn bè theo 2 chiều

Nếu cần mở rộng tiếp ở phase sau, nên làm thêm:
- enrich profile từ `user-service`
- block/unblock
- mutual friends
- friend suggestions
- notification khi có request mới
