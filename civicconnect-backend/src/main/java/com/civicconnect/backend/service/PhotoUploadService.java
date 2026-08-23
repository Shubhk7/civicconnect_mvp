package com.civicconnect.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Handles citizen photo uploads. The original uploaded bytes are NEVER
 * written to disk — only the blurred result from the AI service's
 * /blur endpoint is persisted. This is the actual enforcement point for
 * the "blur before storage" requirement from the project synopsis: it
 * isn't a policy statement, it's the only code path that writes a file.
 *
 * If the AI service is unreachable, the upload is REJECTED rather than
 * falling back to storing the unblurred original — unlike AI
 * classification (which degrades gracefully because it's a cross-check,
 * not a safety control), skipping blurring silently would defeat the
 * actual privacy guarantee. Fail closed, not open, for this one.
 */
@Service
public class PhotoUploadService {

    private final RestClient restClient;
    private final Path uploadDir;
    private final String publicBaseUrl;

    public PhotoUploadService(
        @Value("${app.ai-service.base-url}") String aiServiceBaseUrl,
        @Value("${app.uploads.dir:/app/uploads}") String uploadDirPath,
        @Value("${app.uploads.public-base-url}") String publicBaseUrl
    ) {
        this.restClient = RestClient.builder().baseUrl(aiServiceBaseUrl).build();
        this.uploadDir = Paths.get(uploadDirPath);
        this.publicBaseUrl = publicBaseUrl;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + uploadDirPath, e);
        }
    }

    public static class UploadException extends RuntimeException {
        public UploadException(String message) { super(message); }
        public UploadException(String message, Throwable cause) { super(message, cause); }
    }

    public static class UploadResult {
        public String photoUrl;
        public int facesBlurred;
        public int platesBlurred;
        public String note;
    }

    public UploadResult uploadAndBlur(MultipartFile file) {
        if (file.isEmpty()) {
            throw new UploadException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new UploadException("Only image uploads are accepted");
        }

        byte[] blurredBytes;
        int facesBlurred = 0;
        int platesBlurred = 0;
        String note = null;

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            byte[] originalBytes = file.getBytes();
            body.add("file", new org.springframework.core.io.ByteArrayResource(originalBytes) {
                @Override
                public String getFilename() { return "upload.jpg"; }
            });

            var response = restClient.post()
                .uri("/blur")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(byte[].class);

            blurredBytes = response.getBody();
            if (blurredBytes == null || blurredBytes.length == 0) {
                throw new UploadException("Blurring service returned an empty result");
            }

            facesBlurred = parseIntHeader(response.getHeaders().getFirst("X-Faces-Blurred"));
            platesBlurred = parseIntHeader(response.getHeaders().getFirst("X-Plates-Blurred"));
            note = response.getHeaders().getFirst("X-Blur-Note");

        } catch (UploadException e) {
            throw e;
        } catch (Exception e) {
            // Fail closed: if we can't blur it, we don't store it. A
            // citizen retries the upload rather than risk a face or
            // plate ending up in a public photo unblurred.
            throw new UploadException(
                "Could not process photo for privacy protection right now. Please try again shortly.", e
            );
        }

        String filename = UUID.randomUUID() + ".jpg";
        Path target = uploadDir.resolve(filename);
        try {
            Files.write(target, blurredBytes);
        } catch (IOException e) {
            throw new UploadException("Could not save processed photo", e);
        }

        UploadResult result = new UploadResult();
        result.photoUrl = publicBaseUrl.replaceAll("/$", "") + "/uploads/" + filename;
        result.facesBlurred = facesBlurred;
        result.platesBlurred = platesBlurred;
        result.note = note;
        return result;
    }

    private int parseIntHeader(String value) {
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
