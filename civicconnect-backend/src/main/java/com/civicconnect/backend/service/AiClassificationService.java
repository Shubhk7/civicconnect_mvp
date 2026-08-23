package com.civicconnect.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AiClassificationService(@Value("${app.ai-service.base-url}") String aiServiceBaseUrl) {
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

    public static class VerificationResult {
        public boolean available;
        public boolean changeDetected;
        public double similarityScore;
        public String verdict;
        public String note;

        static VerificationResult unavailable(String reason) {
            VerificationResult r = new VerificationResult();
            r.available = false;
            r.note = reason;
            return r;
        }
    }

    /**
     * Downloads the photo from photoUrl and forwards it to the AI service's
     * /classify endpoint. Returns available=false if anything goes wrong —
     * callers should treat that as "fall back to citizen-provided issue type."
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
            return ClassificationResult.unavailable("AI classification unavailable: " + e.getMessage());
        }
    }

    /**
     * Compares the original report photo with the officer's after-photo
     * via POST /verify. Returns available=false on any failure — callers
     * must still accept the officer submission and omit the AI panel.
     */
    public VerificationResult verify(String beforePhotoUrl, String afterPhotoUrl) {
        if (beforePhotoUrl == null || beforePhotoUrl.isBlank()
            || afterPhotoUrl == null || afterPhotoUrl.isBlank()) {
            return VerificationResult.unavailable("Before and after photos are required");
        }
        try {
            byte[] beforeBytes = downloadImage(beforePhotoUrl);
            byte[] afterBytes = downloadImage(afterPhotoUrl);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("before", new ByteArrayResource(beforeBytes) {
                @Override public String getFilename() { return "before.jpg"; }
            });
            body.add("after", new ByteArrayResource(afterBytes) {
                @Override public String getFilename() { return "after.jpg"; }
            });

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri("/verify")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);

            VerificationResult result = new VerificationResult();
            result.available = true;
            Object change = first(response, "changeDetected", "changeDetected");
            result.changeDetected = change instanceof Boolean b ? b : Boolean.TRUE.equals(change);
            Object sim = first(response, "similarityScore", "similarityScore");
            result.similarityScore = sim instanceof Number n ? n.doubleValue() : 0.0;
            result.verdict = (String) response.get("verdict");
            return result;
        } catch (Exception e) {
            return VerificationResult.unavailable("AI verification unavailable: " + e.getMessage());
        }
    }

    private static Object first(Map<String, Object> map, String a, String b) {
        Object v = map.get(a);
        return v != null ? v : map.get(b);
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
