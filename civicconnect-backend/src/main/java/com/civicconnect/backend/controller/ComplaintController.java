package com.civicconnect.backend.controller;

import com.civicconnect.backend.dto.ComplaintRequest;
import com.civicconnect.backend.dto.ComplaintResponse;
import com.civicconnect.backend.model.Complaint;
import com.civicconnect.backend.model.ComplaintStatusHistory;
import com.civicconnect.backend.repository.ComplaintRepository;
import com.civicconnect.backend.repository.ComplaintStatusHistoryRepository;
import com.civicconnect.backend.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final ComplaintRepository complaintRepository;
    private final ComplaintStatusHistoryRepository historyRepository;

    public ComplaintController(ComplaintService complaintService,
                                ComplaintRepository complaintRepository,
                                ComplaintStatusHistoryRepository historyRepository) {
        this.complaintService = complaintService;
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
    }

    // Citizen submits a new report
    @PostMapping
    public ComplaintResponse submit(@Valid @RequestBody ComplaintRequest req) {
        return complaintService.submit(req);
    }

    // Citizen checks status + timeline of their report
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Integer id) {
        Complaint c = complaintRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + id));
        List<ComplaintStatusHistory> history =
            historyRepository.findByComplaintIdOrderByCreatedAtAsc(id);
        return Map.of(
            "complaint", ComplaintResponse.from(c),
            "timeline", history
        );
    }

    // Officer dashboard: list complaints, optionally filtered by ward or status
    @GetMapping
    public List<ComplaintResponse> list(
        @RequestParam(required = false) Integer wardId,
        @RequestParam(required = false) String status
    ) {
        List<Complaint> complaints;
        if (wardId != null) {
            complaints = complaintRepository.findByWardId(wardId);
        } else if (status != null) {
            complaints = complaintRepository.findByStatus(status);
        } else {
            complaints = complaintRepository.findAll();
        }
        return complaints.stream().map(ComplaintResponse::from).toList();
    }

    // Officer marks the issue fixed and uploads an after-photo
    @PostMapping("/{id}/resolve")
    public ComplaintResponse resolve(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        Complaint c = complaintService.markResolved(id, body.get("afterPhotoUrl"));
        return ComplaintResponse.from(c);
    }

    // Generic status update (e.g. AI verification passes -> VERIFIED/CLOSED,
    // or fails -> REOPENED)
    @PatchMapping("/{id}/status")
    public ComplaintResponse updateStatus(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        Complaint c = complaintService.updateStatus(id, body.get("status"), body.get("note"));
        return ComplaintResponse.from(c);
    }
}
