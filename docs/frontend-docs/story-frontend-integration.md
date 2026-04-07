# Story Frontend Integration

## 1. Tong quan

Tai lieu nay mo ta cach frontend tich hop tinh nang Story 24h voi codebase hien tai.

Story hien tai duoc trien khai bang 2 service chinh:

- `social-media-social-service`: luu story, feed, viewed state, viewer list, delete, cleanup
- `social-media-media-service`: upload file media truoc khi gui metadata vao social-service

Frontend nen di qua gateway thay vi goi truc tiep tung service.

## Base URL

Khuyen nghi frontend goi qua gateway:

- `http://localhost:8080`

Base path lien quan den story:

- `http://localhost:8080/api/social/stories`
- `http://localhost:8080/api/media`

---

## 2. Authentication va header

Frontend can gui:

```http
Authorization: Bearer <accessToken>
```

Gateway se validate JWT va inject xuong backend:

- `X-User-Id`
- `X-Role`

Frontend khong can tu gui `X-User-Id` neu goi qua gateway.

---

## 3. Flow dung cho frontend

Flow dung voi story tren frontend la:

1. user chon anh/video cho story
2. frontend upload file sang media-service voi `resourceType=story`
3. frontend lay metadata `publicId`, `mediaUrl`, `mediaType`, `width`, `height`, `bytes`
4. frontend gui metadata do vao `POST /api/social/stories`
5. frontend goi `GET /api/social/stories/feed` de render story ring
6. khi user mo story, frontend co the goi `GET /api/social/stories/{storyId}` neu can payload day du
7. sau khi story duoc hien thi du dieu kien, frontend goi `POST /api/social/stories/{storyId}/view`
8. neu owner mo danh sach viewer, frontend goi `GET /api/social/stories/{storyId}/viewers`

---

## 4. Danh sach endpoint FE can dung

| Method | URL | Muc dich |
| --- | --- | --- |
| POST | `/api/media/upload?resourceType=story` | Upload file cho story |
| GET | `/api/media/my-uploads?resourceType=story` | Lay lai media story da upload |
| POST | `/api/social/stories` | Tao story moi |
| GET | `/api/social/stories/feed` | Lay story ring cua current user |
| GET | `/api/social/stories/users/{userId}` | Lay story active cua mot owner |
| GET | `/api/social/stories/{storyId}` | Lay chi tiet mot story |
| POST | `/api/social/stories/{storyId}/view` | Danh dau da xem story |
| GET | `/api/social/stories/{storyId}/viewers?page=0&size=20` | Lay danh sach viewer cua story |
| DELETE | `/api/social/stories/{storyId}` | Xoa story cua chinh minh |

---

## 5. Upload media cho story

## 5.1. Method + URL

- `POST /api/media/upload?resourceType=story`

## 5.2. Request

`Content-Type: multipart/form-data`

Vi du voi `fetch`:

```js
const formData = new FormData();
formData.append('files', file);

const response = await fetch('http://localhost:8080/api/media/upload?resourceType=story', {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${accessToken}`
  },
  body: formData
});

const data = await response.json();
```

## 5.3. Response

```json
{
  "items": [
    {
      "publicId": "social-media/stories/userA/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
      "mediaType": "IMAGE",
      "format": "jpg",
      "width": 1080,
      "height": 1920,
      "bytes": 348123
    }
  ]
}
```

## 5.4. Rule FE can nho

- phase hien tai backend cho toi da 1 media item moi story
- frontend nen chan UX theo huong 1 anh hoac 1 video
- `provider` khong duoc media-service tra ve, frontend nen tu set `CLOUDINARY` khi gui sang social-service
- story nen dung `resourceType=story`, khong dung chung media post

## 5.5. Mapping metadata de gui sang social-service

```js
const firstItem = uploadResult.items[0];

const storyPayload = {
  caption,
  visibility,
  media: firstItem
    ? [
        {
          publicId: firstItem.publicId,
          mediaUrl: firstItem.mediaUrl,
          mediaType: firstItem.mediaType,
          provider: 'CLOUDINARY',
          width: firstItem.width,
          height: firstItem.height,
          bytes: firstItem.bytes
        }
      ]
    : []
};
```

---

## 6. Tao story

## 6.1. Method + URL

- `POST /api/social/stories`

## 6.2. Request

```json
{
  "caption": "Di choi cuoi tuan",
  "visibility": "FRIEND",
  "media": [
    {
      "publicId": "social-media/stories/userA/abc123",
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

## 6.3. Rule thuc te

- `visibility` bat buoc va chi nhan `PUBLIC`, `FRIEND`, `PRIVATE`
- phai co it nhat mot trong hai: `caption` hoac `media`
- phase hien tai toi da 1 media item
- caption qua dai hoac media sai kieu se tra `400`

## 6.4. Response

`201 Created`

```json
{
  "id": 101,
  "userId": "userA",
  "username": "nguyenvana",
  "fullName": "Nguyen Van A",
  "avatarUrl": "https://cdn.example.com/avatar-a.jpg",
  "caption": "Di choi cuoi tuan",
  "visibility": "FRIEND",
  "media": [
    {
      "publicId": "social-media/stories/userA/abc123",
      "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
      "mediaType": "IMAGE",
      "provider": "CLOUDINARY",
      "width": 1080,
      "height": 1920,
      "bytes": 348123
    }
  ],
  "viewCount": 0,
  "viewedByCurrentUser": true,
  "createdAt": "2026-04-07T10:00:00.000+00:00",
  "expiresAt": "2026-04-08T10:00:00.000+00:00"
}
```

## 6.5. Cach FE nen xu ly sau khi tao

- co the chen story moi vao dau cum story cua current user
- neu ring current user chua ton tai thi tao group moi o vi tri dau
- `viewedByCurrentUser` cua owner co the xem nhu `true`

---

## 7. Lay story ring

## 7.1. Method + URL

- `GET /api/social/stories/feed`

## 7.2. Response shape

```json
[
  {
    "userId": "userA",
    "username": "me",
    "fullName": "My Account",
    "avatarUrl": "https://cdn.example.com/me.jpg",
    "hasUnseen": false,
    "latestStoryAt": "2026-04-07T10:00:00.000+00:00",
    "stories": [
      {
        "id": 101,
        "userId": "userA",
        "username": "me",
        "fullName": "My Account",
        "avatarUrl": "https://cdn.example.com/me.jpg",
        "caption": "Tin cua toi",
        "visibility": "FRIEND",
        "media": [
          {
            "publicId": "social-media/stories/userA/abc123",
            "mediaUrl": "https://res.cloudinary.com/demo/image/upload/v1/story.jpg",
            "mediaType": "IMAGE",
            "provider": "CLOUDINARY",
            "width": 1080,
            "height": 1920,
            "bytes": 348123
          }
        ],
        "viewCount": 0,
        "viewedByCurrentUser": true,
        "createdAt": "2026-04-07T10:00:00.000+00:00",
        "expiresAt": "2026-04-08T10:00:00.000+00:00"
      }
    ]
  },
  {
    "userId": "userB",
    "username": "john",
    "fullName": "John Doe",
    "avatarUrl": "https://cdn.example.com/john.jpg",
    "hasUnseen": true,
    "latestStoryAt": "2026-04-07T09:30:00.000+00:00",
    "stories": []
  }
]
```

## 7.3. Rule FE can dua vao

- moi phan tu la mot ring theo owner
- `stories` da duoc sort moi nhat truoc
- backend da enrich san `username`, `fullName`, `avatarUrl`
- `hasUnseen = true` nghia la trong cum con story chua xem
- current user co the duoc dua len dau ring

## 7.4. UI mapping goi y

Ring item can dung:

- `avatarUrl` de render avatar
- `username` hoac `fullName` de hien thi ten
- `hasUnseen` de doi mau border
- `latestStoryAt` de sort bo sung neu FE can local update

---

## 8. Lay story cua mot owner

## 8.1. Method + URL

- `GET /api/social/stories/users/{userId}`

## 8.2. Use case

- bam vao ring cua mot user
- profile page can hien thi story dang active cua owner do

## 8.3. Response

Response la mang `StoryResponse[]`.

Frontend co the dung endpoint nay khi:

- can refresh lai cum story cua mot owner sau khi da mo ring
- can load truc tiep tu profile page

---

## 9. Lay chi tiet mot story

## 9.1. Method + URL

- `GET /api/social/stories/{storyId}`

## 9.2. Khi nao can goi

Frontend khong bat buoc luc nao cung phai goi endpoint nay neu da co du lieu trong feed.

Nen goi khi:

- nguoi dung vao truc tiep mot deep link story
- FE can refresh lai mot story cu the
- FE chi co `storyId` ma chua co payload day du

---

## 10. Danh dau da xem story

## 10.1. Method + URL

- `POST /api/social/stories/{storyId}/view`

Body khong can gui.

## 10.2. Rule FE nen ap dung

- chi goi khi story da hien thi du thoi gian de duoc tinh la mot luot xem hop le
- co the goi khi slide story da hien thi, khong nen goi ngay khi preload
- owner mo story cua minh khong can FE xu ly dac biet, backend da tu bo qua record view
- endpoint nay idempotent, co the retry neu loi mang ngan han

## 10.3. Vi du request

```js
await fetch(`http://localhost:8080/api/social/stories/${storyId}/view`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${accessToken}`
  }
});
```

## 10.4. Vi du response

```json
{
  "storyId": 101,
  "viewerId": "userB",
  "username": null,
  "fullName": null,
  "avatarUrl": null,
  "viewedAt": "2026-04-07T10:15:00.000+00:00"
}
```

Luu y:

- response cua endpoint `view` hien tai khong enrich profile viewer
- neu FE can profile day du thi viewer list moi la endpoint dung de hien thi owner analytics

---

## 11. Viewer list cho owner

## 11.1. Method + URL

- `GET /api/social/stories/{storyId}/viewers?page=0&size=20`

## 11.2. Rule

- chi owner moi xem duoc endpoint nay
- backend da enrich profile viewer
- response co pagination kieu Spring `Page`

## 11.3. Response mau

```json
{
  "content": [
    {
      "storyId": 101,
      "viewerId": "userB",
      "username": "john",
      "fullName": "John Doe",
      "avatarUrl": "https://cdn.example.com/john.jpg",
      "viewedAt": "2026-04-07T10:15:00.000+00:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 1,
  "empty": false
}
```

## 11.4. Cach FE nen dung

- modal hoac bottom sheet viewer list cho owner
- load page dau khi owner bam vao view count
- neu `last = false` thi cho phep load more

---

## 12. Xoa story

## 12.1. Method + URL

- `DELETE /api/social/stories/{storyId}`

## 12.2. Rule FE

- chi show nut xoa voi owner
- sau khi xoa thanh cong, FE nen remove story khoi group local ngay
- neu group cua owner khong con story nao, remove ring owner khoi danh sach

Vi du:

```js
await fetch(`http://localhost:8080/api/social/stories/${storyId}`, {
  method: 'DELETE',
  headers: {
    Authorization: `Bearer ${accessToken}`
  }
});
```

Backend tra `204 No Content`.

---

## 13. Error format FE can xu ly

Story di qua `GlobalExceptionHandler` cua social-service, nen FE chu yeu gap 3 dang.

## Validation error

```json
{
  "message": "Validation failed",
  "errors": {
    "visibility": "Visibility is required"
  }
}
```

## Business error `400`

```json
{
  "message": "Phase 1 only supports one media item per story"
}
```

Hoac:

```json
{
  "message": "Story must contain caption or at least one media item"
}
```

## Not found / forbidden dang `404`

```json
{
  "message": "Story not found with id: 101"
}
```

Luu y:

- backend hien tai khong tach ro `403` va `404` cho mot so rule privacy
- FE nen hien thi thong diep chung kieu: `Story khong ton tai hoac ban khong co quyen xem`

---

## 14. Kieu du lieu goi y cho FE

```ts
export type StoryMedia = {
  publicId: string | null;
  mediaUrl: string;
  mediaType: 'IMAGE' | 'VIDEO' | string;
  provider: string | null;
  width: number | null;
  height: number | null;
  bytes: number | null;
};

export type Story = {
  id: number;
  userId: string;
  username: string | null;
  fullName: string | null;
  avatarUrl: string | null;
  caption: string | null;
  visibility: 'PUBLIC' | 'FRIEND' | 'PRIVATE' | string;
  media: StoryMedia[];
  viewCount: number;
  viewedByCurrentUser: boolean;
  createdAt: string;
  expiresAt: string;
};

export type StoryFeedGroup = {
  userId: string;
  username: string | null;
  fullName: string | null;
  avatarUrl: string | null;
  hasUnseen: boolean;
  latestStoryAt: string | null;
  stories: Story[];
};

export type StoryViewer = {
  storyId: number;
  viewerId: string;
  username: string | null;
  fullName: string | null;
  avatarUrl: string | null;
  viewedAt: string;
};
```

---

## 15. Cach to chuc state tren frontend

State goi y:

- `storyFeedGroups: StoryFeedGroup[]`
- `activeStoryGroupIndex: number`
- `activeStoryIndex: number`
- `viewerPageByStoryId: Record<number, StoryViewer[]>`

Xu ly local goi y:

- khi tao story moi, upsert vao group cua current user
- khi goi `/view` thanh cong, update `viewedByCurrentUser = true` cho story local
- recalculate `hasUnseen` cua group tu danh sach story local
- khi xoa story, remove item khoi `stories`; neu group rong thi remove ca group

---

## 16. Goi y UX va ky thuat

- khong goi `/view` ngay luc preload story
- preload media tiep theo, nhung chi mark viewed khi user da mo that
- voi video story, nen doi player bat dau phat hoac qua mot threshold ngan roi moi goi `/view`
- khi current user tao story moi, co the update optimistic UI sau khi `POST /api/social/stories` thanh cong
- viewer list chi nen fetch khi owner mo analytics, khong can preload trong feed

---

## 17. Toi thieu FE can implement de chay duoc

1. uploader goi `POST /api/media/upload?resourceType=story`
2. form tao story goi `POST /api/social/stories`
3. man hinh home goi `GET /api/social/stories/feed`
4. story viewer goi `POST /api/social/stories/{storyId}/view`
5. owner co nut xoa goi `DELETE /api/social/stories/{storyId}`
6. owner co modal viewer list goi `GET /api/social/stories/{storyId}/viewers`

Neu can toi gian cho MVP, FE van co the bat dau voi 5 muc dau, viewer list la phan tang them cho owner.