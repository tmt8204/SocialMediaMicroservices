1) Hiện trạng sau khi đọc code
social-service
PostController đang tạo/cập nhật bài viết qua JSON:
POST /api/social/posts/create, PUT /api/social/posts/update/{postId}.
PostCreateRequest và PostUpdateRequest hiện chỉ nhận List<String> mediaUrls.
PostService chỉ lưu URL ảnh vào bảng post_media, chưa có bước upload file thật.
Entity PostMedia hiện có:
mediaUrl
mediaType
media-service
Hiện mới có skeleton cơ bản:
MediaServiceApplication
pom.xml
application.properties
Chưa có controller/service xử lý upload ảnh.
Gateway / hạ tầng
api-gateway hiện đã route auth-service, user-service, social-service.
Chưa thấy route rõ ràng cho media-service như /api/media/**.
2) Mục tiêu
Ảnh được upload lên Cloudinary bởi media-service.
social-service chỉ lưu metadata/url, không giữ file nhị phân.
Hỗ trợ:
upload 1 hoặc nhiều ảnh
cập nhật ảnh khi sửa post
xóa ảnh khỏi Cloudinary khi post bị xóa vĩnh viễn hoặc media bị thay thế
3) Kiến trúc đề xuất
Phương án khuyến nghị

Tách trách nhiệm rõ ràng:

media-service: nhận file, upload Cloudinary, trả metadata ảnh
social-service: nhận metadata từ client và lưu vào DB cùng bài viết
Flow đề xuất
Client gọi media-service để upload ảnh trước.
media-service upload lên Cloudinary.
media-service trả về:
publicId
secureUrl
resourceType
format
width
height
bytes
Client gọi social-service để tạo post, truyền danh sách media metadata.
social-service lưu thông tin này vào posts và post_media.

Cách này phù hợp microservice hơn vì social-service không phải xử lý file upload trực tiếp.

Phương án thay thế

Cho social-service nhận multipart file rồi gọi nội bộ sang media-service để upload.
Cách này dùng được, nhưng coupling cao hơn và flow phức tạp hơn.

4) Thay đổi cần làm ở media-service
4.1. Bổ sung dependency

Trong media-service/pom.xml thêm:

Cloudinary Java SDK
validation starter
(tuỳ chọn) actuator / openfeign nếu cần
4.2. Bổ sung config

Trong application.properties hoặc env:

server.port=8084
cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME}
cloudinary.api-key=${CLOUDINARY_API_KEY}
cloudinary.api-secret=${CLOUDINARY_API_SECRET}
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=30MB
4.3. Tạo các class chính

Đề xuất thêm:

config/CloudinaryConfig.java
controller/MediaController.java
service/CloudinaryMediaService.java
dto/UploadMediaResponse.java
dto/DeleteMediaRequest.java
4.4. API nên có
Upload 1 hoặc nhiều ảnh

POST /api/media/upload

Content-Type: multipart/form-data
Input: files[]

Output:

{
  "items": [
    {
      "publicId": "social/posts/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/.../image/upload/...jpg",
      "mediaType": "IMAGE",
      "format": "jpg",
      "width": 1080,
      "height": 1080,
      "bytes": 245678
    }
  ]
}
Xóa ảnh

DELETE /api/media/{publicId}

(Tuỳ chọn) batch delete

POST /api/media/delete-batch

4.5. Rule upload
Số lượng media
- Chỉ ảnh:
    ≤ 5 ảnh

- Chỉ video:
    ≤ 2 video

- Mix ảnh + video:
    ≤ 3 ảnh + ≤ 1 video
Định dạng hỗ trợ
Image: jpg, jpeg, png, webp
Video: mp4, mov
Giới hạn dung lượng
- Image ≤ 5MB
- Video ≤ 50MB
Quy tắc tổng
- Tổng số media trong mọi trường hợp ≤ 5
4.6. Quy ước lưu trữ
Folder Cloudinary:
social-media/posts/{userId}/
Tag:
post
social-service
{userId}
5) Thay đổi cần làm ở social-service
5.1. DTO request/response

Hiện đang dùng List<String> mediaUrls.
Nên chuyển sang object rõ nghĩa hơn:

public class PostMediaRequest {
    private String publicId;
    private String mediaUrl;
    private String mediaType;
}
5.2. Entity PostMedia

Nên mở rộng thêm các field:

publicId
mediaUrl
mediaType
provider (ví dụ: CLOUDINARY)

Có thể thêm:

width
height
bytes
5.3. PostService

Trong createPost() và updatePost():

không upload file tại đây
chỉ map metadata do media-service trả về vào PostMedia
lưu DB như hiện tại
5.4. Xử lý xóa ảnh
hidePost() / soft delete: không xóa ảnh Cloudinary
deletePostPermanently():
gọi media-service xóa theo publicId
Update post:
so sánh media cũ và mới
xóa media không còn dùng
6) Gateway và cấu hình môi trường
api-gateway
spring.cloud.gateway.server.webflux.routes[n].id=media-service
spring.cloud.gateway.server.webflux.routes[n].uri=${services.media-service-url:http://localhost:8084}
spring.cloud.gateway.server.webflux.routes[n].predicates[0]=Path=/api/media/**
docker-compose / .env

Thêm biến môi trường:

CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET
7) Kế hoạch triển khai đề xuất
Phase 1 — Dựng media-service
Thêm SDK Cloudinary và config env
Viết API upload ảnh
Test bằng Postman
Phase 2 — Nối với social-service
Đổi DTO sang metadata object
Mở rộng entity PostMedia
Lưu publicId + mediaUrl + mediaType
Phase 3 — Xử lý xóa/replace ảnh
Detect media bị remove
Gọi media-service xóa
Cleanup khi delete post
Phase 4 — Hardening
Validate file
Logging + error handling
Viết test
8) Thiết kế API cho frontend
Upload media

POST /api/media/upload

Tạo post

POST /api/social/posts/create

{
  "content": "Hello Cloudinary",
  "visibility": "PUBLIC",
  "media": [
    {
      "publicId": "social-media/posts/user123/abc123",
      "mediaUrl": "https://res.cloudinary.com/.../image/upload/...jpg",
      "mediaType": "IMAGE"
    }
  ]
}
9) Kết luận

media-service chịu trách nhiệm upload Cloudinary, social-service chỉ lưu metadata.

Ưu điểm:

clean kiến trúc
đúng microservice
dễ scale
dễ mở rộng (video, avatar, story, cleanup job)