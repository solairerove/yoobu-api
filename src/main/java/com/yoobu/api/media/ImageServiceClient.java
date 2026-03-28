package com.yoobu.api.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.config.ImageServiceProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
public class ImageServiceClient {

    private static final int[] RETRY_DELAYS_SECONDS = {2, 4, 8, 16};

    private final ImageServiceProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ImageServiceClient(ImageServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    public String upload(Long tenantId, String uploadPath, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        log.info("Uploading image to image-service: url={}, tenantId={}, uploadPath={}, filename={}, size={}bytes",
                properties.url() + "/upload", tenantId, uploadPath, filename, bytes.length);

        return withRetry(() -> {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
            ImageUploadResponse response = restClient.post()
                    .uri("/upload")
                    .header("X-Tenant-Id", tenantId.toString())
                    .header("X-Upload-Path", uploadPath)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ImageUploadResponse.class);
            if (response == null || response.url() == null) {
                throw new RestClientException("Empty response from image service");
            }
            log.info("Image uploaded successfully: url={}", response.url());
            return response.url();
        });
    }

    public void deleteByUrl(String cdnUrl) {
        if (cdnUrl == null) return;
        String prefix = properties.cdnBaseUrl() + "/";
        if (!cdnUrl.startsWith(prefix)) {
            log.warn("CDN URL does not match expected prefix, skipping delete: cdnUrl={}, expectedPrefix={}",
                    cdnUrl, prefix);
            return;
        }
        String key = cdnUrl.substring(prefix.length());
        log.info("Deleting image from image-service: key={}", key);
        try {
            restClient.delete()
                    .uri("/object")
                    .header("X-Object-Key", key)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Image deleted: key={}", key);
        } catch (Exception e) {
            log.warn("Failed to delete image: key={}, error={}", key, e.getMessage());
        }
    }

    private <T> T withRetry(Supplier<T> operation) {
        RestClientException lastException = null;
        for (int i = 0; i <= RETRY_DELAYS_SECONDS.length; i++) {
            try {
                return operation.get();
            } catch (HttpClientErrorException e) {
                // 4xx: image service rejected the request — don't retry
                String body = e.getResponseBodyAsString();
                log.error("Image service rejected request: status={}, body={}", e.getStatusCode(), body);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, extractErrorMessage(body));
            } catch (HttpServerErrorException e) {
                // 5xx from image service itself (e.g. R2 failure) — don't retry, propagate error
                String body = e.getResponseBodyAsString();
                log.error("Image service upstream error: status={}, body={}", e.getStatusCode(), body);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, extractErrorMessage(body));
            } catch (ResourceAccessException e) {
                // Network/timeout — service may be starting up, retry
                lastException = e;
                if (i < RETRY_DELAYS_SECONDS.length) {
                    log.warn("Image service unreachable (attempt {}/{}), retrying in {}s: {}",
                            i + 1, RETRY_DELAYS_SECONDS.length + 1, RETRY_DELAYS_SECONDS[i], e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAYS_SECONDS[i] * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image service unavailable");
                    }
                }
            }
        }
        log.error("Image service unreachable after all retries: {}", lastException != null ? lastException.getMessage() : "unknown");
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image service unavailable");
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "Image upload failed";
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.has("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {}
        return "Image upload failed";
    }

    private record ImageUploadResponse(String url) {}
}
