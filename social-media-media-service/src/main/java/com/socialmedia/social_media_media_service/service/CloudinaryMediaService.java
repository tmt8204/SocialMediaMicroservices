package com.socialmedia.social_media_media_service.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.socialmedia.social_media_media_service.dto.UploadMediaResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudinaryMediaService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov");

    private final Cloudinary cloudinary;

    @Value("${app.media.max-total-files:5}")
    private int maxTotalFiles;

    @Value("${app.media.max-image-files:5}")
    private int maxImageFiles;

    @Value("${app.media.max-video-files:2}")
    private int maxVideoFiles;

    @Value("${app.media.max-mixed-image-files:3}")
    private int maxMixedImageFiles;

    @Value("${app.media.max-mixed-video-files:1}")
    private int maxMixedVideoFiles;

    @Value("${app.media.max-image-size-bytes:5242880}")
    private long maxImageSizeBytes;

    @Value("${app.media.max-video-size-bytes:52428800}")
    private long maxVideoSizeBytes;

    public UploadMediaResponse getUploadedMedia(String userId, String resourceType) {
        String normalizedUserId = normalizeUserId(userId);
        MediaResourceType mediaResourceType = MediaResourceType.from(resourceType);
        String folderPrefix = mediaResourceType.folderForUser(normalizedUserId);

        List<UploadMediaResponse.MediaItem> items = new ArrayList<>();
        String nextCursor = null;

        try {
            do {
                Map<String, Object> options = new LinkedHashMap<>();
                options.put("prefix", folderPrefix + "/");
                options.put("type", "upload");
                options.put("max_results", 500);
                if (StringUtils.hasText(nextCursor)) {
                    options.put("next_cursor", nextCursor);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> result = cloudinary.api().resources(options);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> resources = (List<Map<String, Object>>) result.getOrDefault("resources", List.of());

                for (Map<String, Object> resource : resources) {
                    items.add(mapCloudinaryResource(resource));
                }

                nextCursor = Objects.toString(result.get("next_cursor"), null);
            } while (StringUtils.hasText(nextCursor));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load uploaded media from Cloudinary.", ex);
        }

        return UploadMediaResponse.builder()
                .items(items)
                .build();
    }

    public UploadMediaResponse uploadMedia(List<MultipartFile> files, String userId, String resourceType) {
        validateFiles(files);
        String normalizedUserId = normalizeUserId(userId);
        MediaResourceType mediaResourceType = MediaResourceType.from(resourceType);
        String folder = mediaResourceType.folderForUser(normalizedUserId);

        List<UploadMediaResponse.MediaItem> uploadedItems = new ArrayList<>();
        for (MultipartFile file : files) {
            SupportedMediaType mediaType = detectMediaType(file);
            Map<String, Object> options = new HashMap<>();
            options.put("folder", folder);
            options.put("resource_type", mediaType.getCloudinaryResourceType());
            options.put("tags", String.join(",", List.of(mediaResourceType.tag(), "social-service", normalizedUserId)));
            options.put("unique_filename", true);
            options.put("overwrite", false);

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
                uploadedItems.add(mapUploadResult(uploadResult, mediaType));
                log.info("Uploaded media '{}' to Cloudinary with publicId='{}'", safeOriginalFilename(file), uploadResult.get("public_id"));
            } catch (IOException ex) {
                throw new RuntimeException("Failed to upload media to Cloudinary.", ex);
            }
        }

        return UploadMediaResponse.builder()
                .items(uploadedItems)
                .build();
    }

    public boolean deleteMedia(String publicId) {
        String normalizedPublicId = normalizePublicId(publicId);
        try {
            Map<?, ?> imageResult = cloudinary.uploader().destroy(
                    normalizedPublicId,
                    ObjectUtils.asMap("invalidate", true, "resource_type", "image"));
            if (isDeleteSuccessful(imageResult)) {
                return true;
            }

            Map<?, ?> videoResult = cloudinary.uploader().destroy(
                    normalizedPublicId,
                    ObjectUtils.asMap("invalidate", true, "resource_type", "video"));
            return isDeleteSuccessful(videoResult);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to delete media from Cloudinary.", ex);
        }
    }

    public Map<String, Object> deleteMediaBatch(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            throw new IllegalArgumentException("publicIds must not be empty.");
        }

        List<String> deleted = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String publicId : publicIds) {
            try {
                String normalizedPublicId = normalizePublicId(publicId);
                if (deleteMedia(normalizedPublicId)) {
                    deleted.add(normalizedPublicId);
                } else {
                    failed.add(normalizedPublicId);
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to delete media with publicId='{}': {}", publicId, ex.getMessage());
                failed.add(publicId);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestedCount", publicIds.size());
        response.put("deleted", deleted);
        response.put("failed", failed);
        return response;
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required.");
        }
        if (files.size() > maxTotalFiles) {
            throw new IllegalArgumentException("Total media count must not exceed " + maxTotalFiles + ".");
        }

        int imageCount = 0;
        int videoCount = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Uploaded files must not be empty.");
            }

            SupportedMediaType mediaType = detectMediaType(file);
            if (mediaType == SupportedMediaType.IMAGE) {
                imageCount++;
                if (file.getSize() > maxImageSizeBytes) {
                    throw new IllegalArgumentException("Image '" + safeOriginalFilename(file) + "' exceeds 5MB limit.");
                }
            } else {
                videoCount++;
                if (file.getSize() > maxVideoSizeBytes) {
                    throw new IllegalArgumentException("Video '" + safeOriginalFilename(file) + "' exceeds 50MB limit.");
                }
            }
        }

        if (videoCount == 0 && imageCount > maxImageFiles) {
            throw new IllegalArgumentException("Only image uploads allow up to " + maxImageFiles + " files.");
        }
        if (imageCount == 0 && videoCount > maxVideoFiles) {
            throw new IllegalArgumentException("Only video uploads allow up to " + maxVideoFiles + " files.");
        }
        if (imageCount > 0 && videoCount > 0
                && (imageCount > maxMixedImageFiles || videoCount > maxMixedVideoFiles)) {
            throw new IllegalArgumentException("Mixed uploads allow up to " + maxMixedImageFiles + " images and "
                    + maxMixedVideoFiles + " video.");
        }
    }

    private SupportedMediaType detectMediaType(MultipartFile file) {
        String extension = getExtension(file.getOriginalFilename());
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);

        if ((contentType.startsWith("image/") || IMAGE_EXTENSIONS.contains(extension))
                && (!StringUtils.hasText(extension) || IMAGE_EXTENSIONS.contains(extension))) {
            return SupportedMediaType.IMAGE;
        }

        if ((contentType.startsWith("video/") || VIDEO_EXTENSIONS.contains(extension))
                && (!StringUtils.hasText(extension) || VIDEO_EXTENSIONS.contains(extension))) {
            return SupportedMediaType.VIDEO;
        }

        throw new IllegalArgumentException(
                "Unsupported file type for '" + safeOriginalFilename(file)
                        + "'. Allowed formats: jpg, jpeg, png, webp, mp4, mov.");
    }

    private UploadMediaResponse.MediaItem mapUploadResult(Map<String, Object> uploadResult, SupportedMediaType mediaType) {
        return UploadMediaResponse.MediaItem.builder()
                .publicId(Objects.toString(uploadResult.get("public_id"), null))
                .mediaUrl(Objects.toString(uploadResult.get("secure_url"), Objects.toString(uploadResult.get("url"), null)))
                .mediaType(mediaType.name())
                .format(Objects.toString(uploadResult.get("format"), null))
                .width(toInteger(uploadResult.get("width")))
                .height(toInteger(uploadResult.get("height")))
                .bytes(toLong(uploadResult.get("bytes")))
                .build();
    }

    private UploadMediaResponse.MediaItem mapCloudinaryResource(Map<String, Object> resource) {
        String resourceType = Objects.toString(resource.get("resource_type"), "image").toUpperCase(Locale.ROOT);
        return UploadMediaResponse.MediaItem.builder()
                .publicId(Objects.toString(resource.get("public_id"), null))
                .mediaUrl(Objects.toString(resource.get("secure_url"), Objects.toString(resource.get("url"), null)))
                .mediaType("VIDEO".equals(resourceType) ? "VIDEO" : "IMAGE")
                .format(Objects.toString(resource.get("format"), null))
                .width(toInteger(resource.get("width")))
                .height(toInteger(resource.get("height")))
                .bytes(toLong(resource.get("bytes")))
                .build();
    }

    private boolean isDeleteSuccessful(Map<?, ?> result) {
        String status = Objects.toString(result.get("result"), "");
        return "ok".equalsIgnoreCase(status) || "deleted".equalsIgnoreCase(status);
    }

    private String normalizeUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required to upload media.");
        }
        return userId.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String normalizePublicId(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new IllegalArgumentException("publicId must not be blank.");
        }
        return publicId.trim().replaceFirst("^/+", "");
    }

    private String getExtension(String fileName) {
        String extension = StringUtils.getFilenameExtension(fileName);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }

    private String safeOriginalFilename(MultipartFile file) {
        return Objects.toString(file.getOriginalFilename(), "file");
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private enum SupportedMediaType {
        IMAGE("image"),
        VIDEO("video");

        private final String cloudinaryResourceType;

        SupportedMediaType(String cloudinaryResourceType) {
            this.cloudinaryResourceType = cloudinaryResourceType;
        }

        public String getCloudinaryResourceType() {
            return cloudinaryResourceType;
        }
    }
}
