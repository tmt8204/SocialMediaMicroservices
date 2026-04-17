package com.socialmedia.social_media_media_service.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.socialmedia.social_media_media_service.dto.DeleteMediaRequest;
import com.socialmedia.social_media_media_service.dto.UploadMediaResponse;
import com.socialmedia.social_media_media_service.service.CloudinaryMediaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Validated
public class MediaController {

    private final CloudinaryMediaService cloudinaryMediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadMediaResponse> uploadMedia(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String requestUserId,
            @RequestParam(value = "resourceType", required = false, defaultValue = "post") String resourceType,
            @RequestParam("files") List<MultipartFile> files) {
        String userId = StringUtils.hasText(headerUserId) ? headerUserId : requestUserId;
        UploadMediaResponse response = cloudinaryMediaService.uploadMedia(files, userId, resourceType);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-uploads")
    public ResponseEntity<UploadMediaResponse> getMyUploadedPostMedia(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String requestUserId,
            @RequestParam(value = "resourceType", required = false, defaultValue = "post") String resourceType) {
        String userId = StringUtils.hasText(headerUserId) ? headerUserId : requestUserId;
        return ResponseEntity.ok(cloudinaryMediaService.getUploadedMedia(userId, resourceType));
    }

    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getUserQuota(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String requestUserId) {
        String userId = StringUtils.hasText(headerUserId) ? headerUserId : requestUserId;
        if (!StringUtils.hasText(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing UserId"));
        }
        return ResponseEntity.ok(cloudinaryMediaService.getUserStorage(userId));
    }

    @DeleteMapping({"/{*publicId}", ""})
    public ResponseEntity<Map<String, Object>> deleteMedia(
            @PathVariable(name = "publicId", required = false) String pathPublicId,
            @RequestParam(name = "publicId", required = false) String requestPublicId) {
        String publicId = StringUtils.hasText(pathPublicId) ? pathPublicId : requestPublicId;
        boolean deleted = cloudinaryMediaService.deleteMedia(publicId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("publicId", publicId == null ? null : publicId.replaceFirst("^/+", ""));
        response.put("deleted", deleted);
        response.put("message", deleted ? "Media deleted successfully" : "Media not found or already deleted");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/delete-batch")
    public ResponseEntity<Map<String, Object>> deleteMediaBatch(@Valid @RequestBody DeleteMediaRequest request) {
        return ResponseEntity.ok(cloudinaryMediaService.deleteMediaBatch(request.getPublicIds()));
    }
}
