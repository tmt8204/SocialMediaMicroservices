1) Muc tieu cua tai lieu

Tai lieu nay viet lai thiet ke `social-media-social-service` theo huong phai match duoc voi `notification-service` hien co, khong mo ta vuot qua cac nghiep vu ma code chua support.

Muc tieu chinh:
- giu design social-service sat voi code hien tai
- chot contract event de `notification-service` consume duoc ngay
- chi dua vao phase hien tai nhung nghiep vu social da ton tai that trong code

2) Hien trang code thuc te cua social-service

`social-media-social-service` hien dang co:

- post CRUD va feed logic
- react post
- bo react post
- lay reaction summary cua post
- create comment cho post
- update comment cua chinh minh
- delete comment cua chinh minh

Nhung diem quan trong can chot theo code:

- `ReactionService` chi xu ly reaction cho post, khong co reaction cho comment
- `CommentService` chi co create/update/delete comment, chua co reply comment, mention, hay reaction comment
- `PostEntity` dang luu `reactionsCount` va `commentCount`
- `ReactionService.reactToPost(...)` chi tao reaction moi khi user chua react truoc do
- `removeReaction(...)` chi giam `reactionsCount`, khong phat event nao
- `CommentService.createComment(...)` tao `CommentEntity` moi va tra `CommentResponse`
- `social-service` hien chua co Kafka producer cho `social-notification-events`

=> Nghia la: notification tu social phase hien tai co 2 nghiep vu da co san va 1 nghiep vu nen bo sung ngay de hoan chinh luong social:
- tao reaction moi cho post
- tao comment moi cho post
- tao post moi

3) Hien trang code thuc te cua notification-service lien quan den social

`notification-service` da co consumer cho `social-notification-events` va `NotificationMapper.fromSocialEvent(...)` dang map 3 event type sau:

- `SOCIAL_POST_REACTION_CREATED`
- `SOCIAL_COMMENT_CREATED`
- `SOCIAL_COMMENT_REACTION_CREATED`

Tuy nhien, de match voi social-service hien tai va bo sung luong dang bai, can tach ro 2 muc:

- 2 event dang match duoc ngay voi code va mapper hien tai:
  - `SOCIAL_POST_REACTION_CREATED`
  - `SOCIAL_COMMENT_CREATED`
- 1 event moi nen them de ho tro notification cho dang bai:
  - `SOCIAL_POST_CREATED`

`SOCIAL_COMMENT_REACTION_CREATED` chua nen coi la contract phase hien tai, vi:
- social-service chua co entity/comment reaction flow
- chua co endpoint reaction cho comment
- chua co service xu ly owner comment notification theo nghiep vu nay

4) Nguyen tac thiet ke de match voi notification-service

De social-service phat event dung va notification-service consume on dinh, can giu 5 nguyen tac:

- chi phat event cho nghiep vu da commit DB thanh cong
- khong goi sync truc tiep sang `notification-service`
- dung Kafka topic chung `social-notification-events`
- dung `recipientId` lam partition key
- khong phat event neu `actorId == recipientId`

Nguyen tac nay giup:
- social-service khong bi coupling voi notification-service
- notification-service co the dedupe theo `sourceEventId`
- frontend nhan duoc in-app notification va SSE theo luong da co san

5) Nghiệp vu social nen phat notification trong phase hien tai

Phase hien tai nen chot 3 nguon social notification:
- reaction moi vao post
- comment moi vao post
- post moi duoc dang

5.1. React post

Endpoint hien co:
- `POST /api/social/posts/{postId}/react`

Flow code hien tai:
- tim post theo `postId`
- chan neu post khong ton tai hoac da bi delete
- check `existsByUserIdAndPostId(userId, postId)`
- chi tao `ReactionsEntity` moi neu user chua react
- tang `post.reactionsCount`

Rule phat notification:
- chi phat event khi reaction moi duoc tao that su
- khong phat event neu request chi la lan goi lap lai khi user da react truoc do
- khong phat event neu user react chinh post cua minh

Loai event:
- `SOCIAL_POST_REACTION_CREATED`

Nguoi nhan:
- `recipientId = post.userId`

5.2. Create comment

Endpoint hien co:
- `POST /api/social/posts/{postId}/comments`

Flow code hien tai:
- validate content khong rong va khong qua 1000 ky tu
- tim post theo `postId`
- chan neu post da bi delete
- tao `CommentEntity`
- save vao DB

Rule phat notification:
- chi phat event sau khi comment moi duoc luu thanh cong
- khong phat event neu user comment chinh post cua minh

Loai event:
- `SOCIAL_COMMENT_CREATED`

Nguoi nhan:
- `recipientId = post.userId`

5.3. Create post

Endpoint hien co:
- `POST /api/social/posts/create`

Flow code hien tai:
- tao `PostEntity` moi
- set `userId`, `content`, `visibility`
- map media neu co
- save post vao DB
- tra `PostResponse`

Rule phat notification de match voi notification-service:
- chi phat event sau khi post moi duoc luu thanh cong
- khong tao notification cho chinh author
- notification cho dang bai phai la fanout theo tung recipient, vi `notification-service` consume theo `recipientId`
- khong duoc broadcast cho toan bo user chi vi post co visibility `PUBLIC`

Tap recipient de xuat:
- neu `visibility = PRIVATE`: khong phat notification
- neu `visibility = FRIEND`: phat cho tat ca accepted friends cua author
- neu `visibility = PUBLIC`: van chi phat cho accepted friends cua author; nguoi la khong nhan notification, ho chi thay bai qua feed/public discovery

Loai event de bo sung:
- `SOCIAL_POST_CREATED`

Nguoi nhan:
- moi accepted friend nhan 1 event rieng

Ly do phai fanout tung recipient:
- topic `social-notification-events` dang dung `recipientId` lam partition key
- `notification-service` dang luu 1 `NotificationDocument` cho 1 recipient
- notification dang bai khong phai 1 event chung cho nhieu user, ma la N event cung noi dung cho N recipient

6) Nhung nghiep vu khong nen dua vao contract notification phase hien tai

Khong nen coi cac nghiep vu sau la event contract bat buoc hien tai:

- `DELETE /api/social/posts/{postId}/react`
- `PUT /api/social/posts/{postId}/comments/{commentId}`
- `DELETE /api/social/posts/{postId}/comments/{commentId}`
- comment reaction
- reply comment
- mention notification

Ly do:
- code chua co yeu cau product ro rang cho notification o nhung flow nay
- notification-service co the mo rong sau, nhung social-service hien tai chua can publish cac event do

Luu y:
- `create post` khong nam trong danh sach loai tru tren; day la luong nen bo sung thiet ke ngay bay gio.

7) Contract event can chot de notification-service consume duoc ngay

7.1. Topic va partition key

Topic:
- `social-notification-events`

Partition key:
- `recipientId`

7.2. Event `SOCIAL_POST_REACTION_CREATED`

Payload toi thieu:

```json
{
  "eventId": "evt_social_react_001",
  "eventType": "SOCIAL_POST_REACTION_CREATED",
  "sourceService": "social-service",
  "postId": 15,
  "actorId": "user_002",
  "actorDisplayName": "user_002",
  "recipientId": "user_001",
  "contentPreview": "Da tha cam xuc bai viet cua ban",
  "deeplink": "/posts/15",
  "createdAt": "2026-04-05T12:00:00Z"
}
```

Field bat buoc de `notification-service` map on dinh:
- `eventId`
- `eventType`
- `recipientId`
- `postId`
- `actorId`

Field khuyen nghi:
- `actorDisplayName`
- `contentPreview`
- `deeplink`
- `createdAt`

7.3. Event `SOCIAL_COMMENT_CREATED`

Payload toi thieu:

```json
{
  "eventId": "evt_social_comment_001",
  "eventType": "SOCIAL_COMMENT_CREATED",
  "sourceService": "social-service",
  "postId": 15,
  "commentId": 99,
  "actorId": "user_002",
  "actorDisplayName": "user_002",
  "recipientId": "user_001",
  "contentPreview": "Da binh luan vao bai viet cua ban",
  "deeplink": "/posts/15",
  "createdAt": "2026-04-05T12:05:00Z"
}
```

Field bat buoc:
- `eventId`
- `eventType`
- `recipientId`
- `postId`
- `commentId`
- `actorId`

7.4. Event moi `SOCIAL_POST_CREATED`

Day la event can them de ho tro notification cho dang bai.

Payload toi thieu:

```json
{
  "eventId": "evt_social_post_created_001_userB",
  "eventType": "SOCIAL_POST_CREATED",
  "sourceService": "social-service",
  "postId": 15,
  "actorId": "user_001",
  "actorDisplayName": "user_001",
  "recipientId": "userB",
  "contentPreview": "Da dang bai viet moi",
  "deeplink": "/posts/15",
  "createdAt": "2026-04-05T12:10:00Z"
}
```

Field bat buoc:
- `eventId`
- `eventType`
- `recipientId`
- `postId`
- `actorId`

Field khuyen nghi:
- `actorDisplayName`
- `contentPreview`
- `deeplink`
- `createdAt`

7.5. Event khong chot trong phase hien tai

`notification-service` co support mapper cho:
- `SOCIAL_COMMENT_REACTION_CREATED`

Nhung social-service chua co feature nay. Vi vay social design phai xem day la phase sau, khong phai requirement hien tai.

8) Mapping nghiep vu social sang notification model

8.1. Reaction post -> notification

Social-service publish:
- `eventType = SOCIAL_POST_REACTION_CREATED`
- `postId = post.id`
- `recipientId = post.userId`
- `actorId = user da react`
- `deeplink = /posts/{postId}`

Notification-service se map thanh:
- `category = SOCIAL`
- `resourceType = POST`
- `resourceId = postId`
- `title = Co nguoi da tha cam xuc bai viet cua ban`

8.2. Create comment -> notification

Social-service publish:
- `eventType = SOCIAL_COMMENT_CREATED`
- `postId = post.id`
- `commentId = comment.id`
- `recipientId = post.userId`
- `actorId = user da comment`
- `deeplink = /posts/{postId}`

Notification-service se map thanh:
- `category = SOCIAL`
- `resourceType = COMMENT`
- `resourceId = commentId`
- `title = Co binh luan moi`

8.3. Create post -> notification

Social-service publish:
- `eventType = SOCIAL_POST_CREATED`
- `postId = post.id`
- `recipientId = tung accepted friend`
- `actorId = author userId`
- `deeplink = /posts/{postId}`

Notification-service can duoc bo sung mapper de map thanh:
- `category = SOCIAL`
- `resourceType = POST`
- `resourceId = postId`
- `title = Co bai viet moi`

Khuyen nghi UX:
- `contentPreview` chi nen la preview ngan, vi du 80-120 ky tu dau cua post
- neu post chi co media ma khong co content huu ich, co the dung preview mac dinh nhu `Da dang bai viet moi`

9) Kien truc de xuat cho social-service

De match duoc voi notification-service hien co, social-service nen co 3 phan ro rang:

- social domain layer:
  - `ReactionService`
  - `CommentService`
- persistence layer:
  - `PostRepository`
  - `ReactionsRepository`
  - `CommentRepository`
- async event layer:
  - `SocialNotificationEventProducer`

Social-service chi co trach nhiem:
- quyet dinh co phat notification hay khong
- build payload dung contract
- publish Kafka event

Notification-service co trach nhiem:
- consume event
- map sang `NotificationDocument`
- dedupe
- persist MongoDB
- day SSE cho user online

10) Thay doi can lam o social-service

10.1. Bo sung Kafka dependency va config

Can them:
- `spring-kafka`

Config toi thieu:

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
social.kafka.topics.notification-events=social-notification-events
```

10.2. Them producer rieng

Nen them mot service, vi du:
- `SocialNotificationEventProducer`

Trach nhiem:
- tao `eventId`
- publish vao topic `social-notification-events`
- dung `recipientId` lam key
- ho tro fanout N recipient cho luong `create post`

10.3. Hook vao nghiep vu hien tai

Trong `ReactionService.reactToPost(...)`:
- sau khi save reaction va update `reactionsCount`
- neu reaction vua duoc tao
- neu `userId` khac `post.userId`
- publish `SOCIAL_POST_REACTION_CREATED`

Trong `CommentService.createComment(...)`:
- sau khi save comment thanh cong
- neu `userId` khac `post.userId`
- publish `SOCIAL_COMMENT_CREATED`

Trong `PostService.createPost(...)`:
- sau khi save post thanh cong
- neu `visibility != PRIVATE`
- lay danh sach accepted friends cua author
- publish `SOCIAL_POST_CREATED` cho tung recipient

10.4. Nhung thay doi chua can lam ngay

Chua can dua vao phase nay:
- outbox pattern
- retry phuc tap phia producer
- actor profile enrichment tu user-service
- comment reaction

10.5. Thay doi can lam o notification-service de nhan notification dang bai

Can bo sung trong `NotificationMapper.fromSocialEvent(...)`:
- support them `SOCIAL_POST_CREATED`

Mapping de xuat:
- `title = Co bai viet moi`
- `resourceType = POST`
- `resourceId = postId`
- `deeplink = /posts/{postId}`

Neu khong bo sung mapper nay, `notification-service` se reject event vi `Unsupported social eventType`.

11) Rule nghiep vu can chot

- Moi user chi co 1 reaction tren 1 post
- `reactionsCount` khong duoc am
- Khong react/comment tren post da delete
- Khong phat notification cho actor tren tai nguyen cua chinh actor
- Event phai du unique bang `eventId`
- Payload event nen uu tien nho, chi mang `preview` va `deeplink`
- Notification dang bai phai fanout theo recipient, khong publish 1 event chung cho tat ca ban be
- Post `PRIVATE` khong duoc phat notification

12) Flow tong quat can dat duoc sau khi tich hop

B1 - user A react post cua user B

B2 - social-service save reaction va update `reactionsCount`

B3 - social-service publish `SOCIAL_POST_REACTION_CREATED` voi key = `userB`

B4 - notification-service consume event, map va save notification

B5 - neu user B dang online, `notification-service` push `notification.created` qua SSE

B6 - frontend cua user B goi:
- `GET /api/notifications`
- `GET /api/notifications/unread-count`
- `PUT /api/notifications/{notificationId}/read`

Flow comment moi cung tuong tu, chi khac `eventType` va `resourceType`.

Flow dang bai moi:

B1 - user A tao post moi

B2 - social-service save `PostEntity`

B3 - neu post co `visibility = FRIEND` hoac `PUBLIC`, social-service lay accepted friends cua user A

B4 - social-service publish N event `SOCIAL_POST_CREATED`, moi event cho 1 `recipientId`

B5 - notification-service consume, map, save va day SSE cho tung recipient online

13) Lo trinh de match tung buoc

Phase 1 - Match voi notification-service hien co
- them Kafka producer vao social-service
- phat `SOCIAL_POST_REACTION_CREATED`
- phat `SOCIAL_COMMENT_CREATED`
- them `SOCIAL_POST_CREATED`
- bo sung mapper `SOCIAL_POST_CREATED` trong notification-service
- test end-to-end voi notification-service

Phase 2 - Hardening
- bo sung logs cho publish event
- them test cho payload contract
- can nhac outbox pattern neu can do tin cay cao hon

Phase 3 - Mo rong nghiep vu social
- comment reaction
- reply comment
- mention
- khi do moi mo rong them contract event va mapper phia notification-service

14) Ket luan

De social-service match voi notification-service hien tai, design dung la:

- giu reaction post va create comment lam 2 nguon social notification chinh
- bo sung notification cho create post theo accepted friends
- publish async qua Kafka topic `social-notification-events`
- dung 3 event type cho phase hien tai mo rong:
  - `SOCIAL_POST_REACTION_CREATED`
  - `SOCIAL_COMMENT_CREATED`
  - `SOCIAL_POST_CREATED`
- khong coi `SOCIAL_COMMENT_REACTION_CREATED` la requirement hien tai vi code social-service chua co feature do

Voi cach chot nay, social-service se khop voi contract ma notification-service dang consume, khong bi over-design, va van de duong mo rong cho cac nghiep vu social nang cao o cac phase sau.

15) Checklist trien khai notification cho dang bai moi

De bien thiet ke tren thanh implementation thuc te, can chot luon danh sach viec can lam o tung service nhu sau.

15.1. Social-service can sua gi

Can sua trong `pom.xml`:
- them `spring-kafka`

Can them config moi trong `application.properties`:

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
social.kafka.topics.notification-events=social-notification-events
```

Can them class moi:
- `event/SocialNotificationEvent.java` hoac tach rieng `SocialPostCreatedEvent.java`
- `service/SocialNotificationEventProducer.java`

Can hook vao `PostService.createPost(...)`:
- save post xong moi publish event
- neu `visibility = PRIVATE` thi return luon, khong publish
- lay danh sach accepted friends cua author
- loop tung recipient va publish 1 event `SOCIAL_POST_CREATED`

Du lieu co san de build event ngay trong social-service:
- `actorId` = `userId` cua author
- `postId` = `savedPost.getId()`
- `recipientIds` = ket qua tu `FriendRepository.findAllAcceptedFriendIds(userId)`
- `deeplink` = `/posts/{postId}`
- `contentPreview` = preview rut gon tu `request.getContent()`

15.2. Notification-service can sua gi

Can sua `NotificationMapper.fromSocialEvent(...)` de support them:
- `SOCIAL_POST_CREATED`

Mapping can them:
- `title = Co bai viet moi`
- `category = SOCIAL`
- `resourceType = POST`
- `resourceId = postId`
- `deeplink = /posts/{postId}`

Neu khong sua mapper, event se bi loi:
- `Unsupported social eventType: SOCIAL_POST_CREATED`

15.3. Preview va UX nen chot the nao

De notification dang bai moi khong qua dai hoac kho doc, nen chot rule preview:
- neu `content` co text: cat 80-120 ky tu dau
- neu `content` rong nhung co media: dung `Da dang bai viet moi`
- neu can hien ten nguoi dang bai o client, uu tien dung `actorDisplayName`, fallback ve `actorId`

15.4. Rule recipient nen chot cuoi cung

De tranh spam notification, chot nhu sau:
- `PRIVATE`: khong notify ai
- `FRIEND`: notify accepted friends
- `PUBLIC`: van chi notify accepted friends

Ly do:
- notification la kenh chu dong day den user
- feed/public discovery la kenh bi dong de user tu xem noi dung cong khai
- neu notify ca nguoi la cho bai `PUBLIC` thi he thong se rat de bi spam

15.5. Test can co sau khi implement

Case 1:
- user A dang bai `PRIVATE`
- khong co event nao duoc publish

Case 2:
- user A dang bai `FRIEND`
- user B va C la accepted friends
- social-service publish 2 event `SOCIAL_POST_CREATED`
- notification-service tao 2 notification rieng cho B va C

Case 3:
- user A dang bai `PUBLIC`
- user B la friend, user X khong phai friend
- chi user B nhan notification
- user X khong co notification, nhung van co the thay bai qua feed/public endpoints neu du dieu kien

Case 4:
- cung 1 bai post duoc tao thanh cong
- moi recipient chi co 1 notification moi tu event rieng cua minh

15.6. Chot implementation order

Thu tu lam viec hop ly nhat:
1. Them event type `SOCIAL_POST_CREATED` va producer o social-service.
2. Hook producer vao `PostService.createPost(...)`.
3. Them mapper support `SOCIAL_POST_CREATED` o notification-service.
4. Test Kafka -> MongoDB -> SSE end-to-end.
5. Sau do moi tinh toi retry, outbox pattern, va enrichment display name.
