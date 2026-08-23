package com.civicconnect.backend.controller;

import com.civicconnect.backend.service.PhotoUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final PhotoUploadService uploadService;

    public UploadController(PhotoUploadService uploadService) {
        this.uploadService = uploadService;
    }

    // Public — a citizen reporting anonymously still needs to attach a
    // photo. Every uploaded photo is run through face/plate blurring
    // before it's ever written to disk; see PhotoUploadService for why
    // this fails closed rather than falling back to the unblurred original.
    @PostMapping("/photo")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) {
        try {
            PhotoUploadService.UploadResult result = uploadService.uploadAndBlur(file);
            return ResponseEntity.ok(Map.of(
                "photoUrl", result.photoUrl,
                "facesBlurred", result.facesBlurred,
                "platesBlurred", result.platesBlurred,
                "note", result.note != null ? result.note : ""
            ));
        } catch (PhotoUploadService.UploadException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
