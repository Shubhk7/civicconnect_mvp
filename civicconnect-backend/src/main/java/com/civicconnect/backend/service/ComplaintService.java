package com.civicconnect.backend.service;

import com.civicconnect.backend.dto.ComplaintRequest;
import com.civicconnect.backend.dto.ComplaintResponse;
import com.civicconnect.backend.model.Complaint;
import com.civicconnect.backend.model.ComplaintStatusHistory;
import com.civicconnect.backend.repository.ComplaintRepository;
import com.civicconnect.backend.repository.ComplaintStatusHistoryRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComplaintService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double DUPLICATE_RADIUS_METERS = 50.0;

    private final ComplaintRepository complaintRepository;
    private final ComplaintStatusHistoryRepository historyRepository;
    private final JurisdictionService jurisdictionService;
    private final AiClassificationService aiClassificationService;

    public ComplaintService(ComplaintRepository complaintRepository,
                             ComplaintStatusHistoryRepository historyRepository,
                             JurisdictionService jurisdictionService,
                             AiClassificationService aiClassificationService) {
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.jurisdictionService = jurisdictionService;
        this.aiClassificationService = aiClassificationService;
    }

    public ComplaintResponse submit(ComplaintRequest req) {
        // 0. If a photo was provided, ask the AI service what it thinks the
        // issue is. This never overrides the citizen silently — it's used
        // to cross-check, and if it disagrees strongly we still trust the
        // citizen's selection but note the AI's read for the officer.
        AiClassificationService.ClassificationResult aiResult =
            aiClassificationService.classify(req.getPhotoUrl());

        String effectiveIssueType = req.getIssueType();
        String aiNote = null;
        if (aiResult.available && aiResult.issueType != null
            && !aiResult.issueType.equals("unclassified") && !aiResult.issueType.equals("unknown")) {
            if (!aiResult.issueType.equalsIgnoreCase(effectiveIssueType)) {
                aiNote = "AI classified this as '" + aiResult.issueType + "' (confidence "
                    + aiResult.confidence + "), citizen selected '" + effectiveIssueType + "'.";
            }
        }

        // 1. Duplicate check first — no point routing a report that's
        // already being tracked.
        List<Complaint> nearby = complaintRepository.findNearbyDuplicates(
            req.getLng(), req.getLat(), effectiveIssueType, DUPLICATE_RADIUS_METERS
        );
        if (!nearby.isEmpty()) {
            ComplaintResponse resp = ComplaintResponse.from(nearby.get(0));
            resp.isDuplicate = true;
            resp.message = "A similar open report already exists nearby. Treated as a duplicate.";
            return resp;
        }

        // 2. Jurisdiction routing
        JurisdictionService.RoutingResult routing =
            jurisdictionService.resolve(req.getLat(), req.getLng(), effectiveIssueType, null);

        Point location = GEOMETRY_FACTORY.createPoint(new Coordinate(req.getLng(), req.getLat()));

        Complaint complaint = new Complaint();
        complaint.setIssueType(effectiveIssueType);
        complaint.setDescription(req.getDescription());
        complaint.setPhotoUrl(req.getPhotoUrl());
        complaint.setLocation(location);

        if (routing.unresolved) {
            complaint.setStatus("UNASSIGNED");
            complaint.setWard(routing.ward); // may be null
        } else {
            complaint.setWard(routing.ward);
            complaint.setRoadType(routing.mapping.getRoadType());
            complaint.setDepartment(routing.mapping.getDepartment());
            complaint.setAuthorityName(routing.mapping.getAuthorityName());
            complaint.setStatus("ASSIGNED");
            complaint.setSlaDeadline(LocalDateTime.now().plusHours(routing.mapping.getSlaHours()));
        }

        Complaint saved = complaintRepository.save(complaint);
        recordHistory(saved, saved.getStatus(),
            routing.unresolved ? routing.reason : "Auto-routed to " + saved.getAuthorityName());
        if (aiNote != null) {
            recordHistory(saved, saved.getStatus(), aiNote);
        }

        ComplaintResponse resp = ComplaintResponse.from(saved);
        resp.message = routing.unresolved
            ? "Could not confidently determine jurisdiction — queued for manual review."
            : "Routed to " + saved.getAuthorityName() + " (" + saved.getDepartment() + ")";
        if (aiNote != null) {
            resp.message += " Note: " + aiNote;
        }
        return resp;
    }

    public Complaint markResolved(Integer complaintId, String afterPhotoUrl) {
        Complaint c = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));
        c.setAfterPhotoUrl(afterPhotoUrl);
        c.setStatus("RESOLVED");
        c.setUpdatedAt(LocalDateTime.now());
        Complaint saved = complaintRepository.save(c);
        recordHistory(saved, "RESOLVED", "Officer marked resolved, pending AI verification");
        return saved;
    }

    public Complaint updateStatus(Integer complaintId, String newStatus, String note) {
        Complaint c = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));
        c.setStatus(newStatus);
        c.setUpdatedAt(LocalDateTime.now());
        Complaint saved = complaintRepository.save(c);
        recordHistory(saved, newStatus, note);
        return saved;
    }

    private void recordHistory(Complaint complaint, String status, String note) {
        ComplaintStatusHistory h = new ComplaintStatusHistory();
        h.setComplaint(complaint);
        h.setStatus(status);
        h.setNote(note);
        historyRepository.save(h);
    }
}
