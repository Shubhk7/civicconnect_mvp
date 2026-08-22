package com.civicconnect.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Talks to the separate Python/FastAPI AI service over HTTP. Kept as its
 * own service so the AI stack can fail, be slow, or be swapped out without
 * touching the core complaint-submission logic — if this call fails, the
 * citizen's manually-selected issue type is used instead (see
 * ComplaintService), so classification is an enhancement, not a hard
 * dependency.
 */
@Service
public class AiClassificationService {

    private final RestClient restClient;
    private final String aiServiceBaseUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AiClassificationService(@Value("${app.ai-service.base-url}") String aiServiceBaseUrl) {
        this.aiServiceBaseUrl = aiServiceBaseUrl;
        this.restClient = RestClient.builder().baseUrl(aiServiceBaseUrl).build();
    }

    public static class ClassificationResult {
        public String issueType;
        public double confidence;
        public String severity;
        public boolean available;
        public String note;

        static ClassificationResult unavailable(String reason) {
            ClassificationResult r = new ClassificationResult();
            r.available = false;
            r.note = reason;
            return r;
        }
    }

    /**
     * Downloads the photo from photoUrl and forwards it to the AI service's
     * /classify endpoint. Returns ClassificationResult.available = false if
     * anything goes wrong — callers should treat that as "fall back to
     * citizen-provided issue type," not as an error to surface to the user.
     */
    public ClassificationResult classify(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return ClassificationResult.unavailable("No photo provided");
        }

        try {
            byte[] imageBytes = downloadImage(photoUrl);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "upload.jpg";
                }
            });

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri("/classify")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);

            ClassificationResult result = new ClassificationResult();
            result.available = true;
            result.issueType = (String) response.get("issueType");
            result.severity = (String) response.get("severity");
            Object conf = response.get("confidence");
            result.confidence = conf instanceof Number ? ((Number) conf).doubleValue() : 0.0;
            return result;

        } catch (Exception e) {
            // AI service down, image unreachable, timeout, etc. — degrade
            // gracefully rather than failing the whole complaint submission.
            return ClassificationResult.unavailable("AI classification unavailable: " + e.getMessage());
        }
    }

    private byte[] downloadImage(String photoUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(photoUrl)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download photo, status " + response.statusCode());
        }
        return response.body();
    }
}
