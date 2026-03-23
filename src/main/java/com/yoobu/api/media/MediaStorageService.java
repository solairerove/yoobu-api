package com.yoobu.api.media;

import com.yoobu.api.config.MediaProperties;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStorageService {

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024L; // 2 MB
    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] MAGIC_WEBP = {0x57, 0x45, 0x42, 0x50};

    private final S3Client s3Client;
    private final MediaProperties properties;

    public String uploadServiceImage(Long tenantId, Long serviceId, MultipartFile file) {
        byte[] bytes = readAndValidate(file);
        String contentType = detectContentType(bytes);
        String key = tenantId + "/services/" + serviceId + "-" + System.currentTimeMillis() + "." + extensionFor(contentType);
        return upload(key, bytes, contentType);
    }

    public String uploadPaymentQr(Long tenantId, MultipartFile file) {
        byte[] bytes = readAndValidate(file);
        String contentType = detectContentType(bytes);
        String key = tenantId + "/payment/qr-" + System.currentTimeMillis() + "." + extensionFor(contentType);
        return upload(key, bytes, contentType);
    }

    public void deleteByUrl(String cdnUrl) {
        String prefix = properties.getCdnBaseUrl() + "/" + properties.getR2Bucket() + "/";
        if (cdnUrl == null || !cdnUrl.startsWith(prefix)) {
            return;
        }
        deleteByKey(cdnUrl.substring(prefix.length()));
    }

    private String upload(String key, byte[] bytes, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getR2Bucket())
                .key(key)
                .contentType(contentType)
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        return properties.getCdnBaseUrl() + "/" + properties.getR2Bucket() + "/" + key;
    }

    private void deleteByKey(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getR2Bucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete R2 object: key={}", key, e);
        }
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds maximum size of 2 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file");
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3 && startsWith(bytes, MAGIC_JPEG)) return "image/jpeg";
        if (bytes.length >= 8 && startsWith(bytes, MAGIC_PNG)) return "image/png";
        if (bytes.length >= 12
                && startsWith(bytes, MAGIC_RIFF)
                && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), MAGIC_WEBP)) return "image/webp";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type. Allowed: JPEG, PNG, WebP");
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalStateException("Unexpected content type: " + contentType);
        };
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }
}
