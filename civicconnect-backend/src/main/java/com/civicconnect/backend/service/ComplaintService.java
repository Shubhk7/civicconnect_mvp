package com.civicconnect.backend.service;

import com.civicconnect.backend.dto.ComplaintRequest;
import com.civicconnect.backend.dto.ComplaintResponse;
import com.civicconnect.backend.dto.OfficerStatsResponse;
import com.civicconnect.backend.model.Complaint;
import com.civicconnect.backend.model.ComplaintStatusHistory;
import com.civicconnect.backend.model.ComplaintUpvote;
import com.civicconnect.backend.repository.ComplaintRepository;
import com.civicconnect.backend.repository.ComplaintStatusHistoryRepository;
import com.civicconnect.backend.repository.ComplaintUpvoteRepository;
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
    private final ComplaintUpvoteRepository upvoteRepository;
    private final JurisdictionService jurisdictionService;
    private final AiClassificationService aiClassificationService;

    public ComplaintService(ComplaintRepository complaintRepository,
                             ComplaintStatusHistoryRepository historyRepository,
                             ComplaintUpvoteRepository upvoteRepository,
                             JurisdictionService jurisdictionService,
                             AiClassificationService aiClassificationService) {
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.upvoteRepository = upvoteRepository;
        this.jurisdictionService = jurisdictionService;
        this.aiClassificationService = aiClassificationService;
    }

    /**
     * @param authenticatedUserId the caller's user id if a valid JWT was
     *                            sent, or null for an anonymous/unauthenticated
     *                            caller. Comes only from JwtAuthFilter — a
     *                            controller never passes anything the
     *                            client claimed directly.
     */
    public ComplaintResponse submit(ComplaintRequest req, Integer authenticatedUserId) {
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
                    + String.format("%.0f", aiResult.confidence * 100) + "%), citizen selected '"
                    + effectiveIssueType + "'.";
            } else {
                aiNote = "AI assessment agrees with the selected issue type '"
                    + effectiveIssueType + "' (confidence "
                    + String.format("%.0f", aiResult.confidence * 100) + "%).";
            }
        }

        // 1. Duplicate check first — no point routing a report that's
        // already being tracked.
        List<Complaint> nearby = complaintRepository.findNearbyDuplicates(
            req.getLng(), req.getLat(), effectiveIssueType, DUPLICATE_RADIUS_METERS
        );
        if (!nearby.isEmpty()) {
            Complaint existing = nearby.get(0);
            // A duplicate report is, functionally, the same signal as an
            // upvote — someone else independently confirming the same
            // problem. Count it as one, using the same anti-spam voter_key
            // mechanism as the explicit upvote endpoint, keyed on the
            // reporter if known, otherwise on the submitted location as a
            // weak fallback (imperfect, but this path already passed
            // duplicate detection, so it's a reasonable proxy for a
            // hackathon demo).
            String voterKey = authenticatedUserId != null
                ? "user:" + authenticatedUserId
                : "anon-dup:" + req.getLat() + "," + req.getLng() + ":" + System.currentTimeMillis();
            tryUpvote(existing.getId(), voterKey);
            Complaint refreshed = complaintRepository.findById(existing.getId()).orElse(existing);
            ComplaintResponse resp = ComplaintResponse.from(refreshed);
            resp.isDuplicate = true;
            resp.message = resp.affectedCount + " citizen" + (resp.affectedCount == 1 ? "" : "s")
                + " (including you) " + (resp.affectedCount == 1 ? "has" : "have")
                + " now reported this location. Added your report to the existing one instead of creating a duplicate.";
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
        if (aiResult.available && aiResult.issueType != null) {
            complaint.setAiIssueType(aiResult.issueType);
            complaint.setAiConfidence(aiResult.confidence);
        }
        complaint.setLocation(location);

        // Identity linking: only ever from the validated JWT, and only
        // if the citizen didn't opt out via "anonymous": true. A logged-in
        // citizen's reports link by default; anonymous reporting requires
        // no account at all (authenticatedUserId is simply null then).
        if (authenticatedUserId != null && !req.isAnonymous()) {
            complaint.setReportedByUserId(authenticatedUserId);
        }

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
        resp.aiAvailable = aiResult.available;
        resp.aiIssueType = saved.getAiIssueType();
        resp.aiConfidence = saved.getAiConfidence();
        return resp;
    }

    /**
     * Explicit upvote on an existing report (citizen-facing "me too"
     * button). One vote per voterKey per complaint, enforced by a unique
     * DB constraint — the check-then-insert here is a convenience for a
     * clean response message, not the actual source of the guarantee.
     * Returns false without changing anything if this voterKey already
     * voted, so callers can tell the difference between "counted" and
     * "already counted."
     */
    public boolean tryUpvote(Integer complaintId, String voterKey) {
        if (upvoteRepository.existsByComplaintIdAndVoterKey(complaintId, voterKey)) {
            return false;
        }
        Complaint c = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        ComplaintUpvote vote = new ComplaintUpvote();
        vote.setComplaint(c);
        vote.setVoterKey(voterKey);
        try {
            upvoteRepository.save(vote);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race condition: another request for the same voterKey landed
            // between the exists-check and this save. The unique
            // constraint caught it — treat as "already voted," not an error.
            return false;
        }

        c.setUpvoteCount((c.getUpvoteCount() != null ? c.getUpvoteCount() : 0) + 1);
        complaintRepository.save(c);
        return true;
    }

    /**
     * Officer submits a claimed fix: after-photo + note. The complaint
     * moves to AWAITING_VERIFICATION (not silently closed). If both
     * before/after photos exist, the AI service is asked whether a real
     * visual change is present; that result is stored for the citizen-
     * facing page. AI failure does not block the submission.
     */
    public Complaint submitForVerification(Integer complaintId, String afterPhotoUrl, String note) {
        if (afterPhotoUrl == null || afterPhotoUrl.isBlank()) {
            throw new IllegalArgumentException("An after photo is required to submit for verification.");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("A resolution note is required to submit for verification.");
        }

        Complaint c = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));
        c.setAfterPhotoUrl(afterPhotoUrl);
        c.setResolutionNote(note.trim());
        c.setStatus("AWAITING_VERIFICATION");
        c.setUpdatedAt(LocalDateTime.now());

        String historyNote = "Officer submitted for verification: " + note.trim();
        if (c.getPhotoUrl() != null && !c.getPhotoUrl().isBlank()) {
            AiClassificationService.VerificationResult vr =
                aiClassificationService.verify(c.getPhotoUrl(), afterPhotoUrl);
            if (vr.available) {
                c.setVerificationVerdict(vr.verdict);
                c.setVerificationSimilarity(vr.similarityScore);
                c.setVerificationChangeDetected(vr.changeDetected);
                c.setVerificationConfidence(confidenceFrom(vr));
                historyNote += " AI verdict: " + vr.verdict
                    + " (similarity " + vr.similarityScore + ").";
            } else {
                historyNote += " AI verification unavailable: " + vr.note;
            }
        }

        Complaint saved = complaintRepository.save(c);
        recordHistory(saved, "AWAITING_VERIFICATION", historyNote);
        return saved;
    }

    private static double confidenceFrom(AiClassificationService.VerificationResult vr) {
        // Map SSIM-style similarity into a 0–1 "how sure is this verdict"
        // score. likely_resolved sits in the mid band; identical photos
        // (no_change) and unrelated photos (unreliable) are both low.
        String verdict = vr.verdict == null ? "" : vr.verdict;
        if (verdict.contains("likely_resolved") || verdict.contains("likely_resolved")) {
            return Math.max(0.55, Math.min(0.95, 1.0 - Math.abs(vr.similarityScore - 0.55)));
        }
        if (verdict.contains("no_change") || verdict.contains("no_change")) {
            return Math.max(0.6, Math.min(0.98, vr.similarityScore));
        }
        return 0.35;
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

    private static final List<String> OPEN_STATUSES =
        List.of("REPORTED", "ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "UNASSIGNED", "REOPENED",
            "AWAITING_VERIFICATION");
    private static final List<String> CLOSED_STATUSES =
        List.of("RESOLVED", "VERIFIED", "CLOSED");
    private static final long NEAR_SLA_WINDOW_HOURS = 24;

    /**
     * Aggregate KPIs for the officer dashboard's stat row. wardId null
     * means "all wards" (ADMIN); a non-null wardId scopes everything to
     * that ward (OFFICER). The caller (ComplaintController) is
     * responsible for deciding which of those applies — this method just
     * trusts the wardId it's given.
     */
    public OfficerStatsResponse computeStats(Integer wardId) {
        List<Complaint> openComplaints = wardId != null
            ? complaintRepository.findByWardIdAndStatusIn(wardId, OPEN_STATUSES)
            : complaintRepository.findByStatusIn(OPEN_STATUSES);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nearSlaCutoff = now.plusHours(NEAR_SLA_WINDOW_HOURS);
        int nearSla = 0;
        int breached = 0;
        for (Complaint c : openComplaints) {
            if (c.getSlaDeadline() == null) continue;
            if (c.getSlaDeadline().isBefore(now)) {
                breached++;
            } else if (c.getSlaDeadline().isBefore(nearSlaCutoff)) {
                nearSla++;
            }
        }

        long closedCount = complaintRepository.countClosed(CLOSED_STATUSES, wardId);
        long onTimeClosed = complaintRepository.countClosedOnTime(CLOSED_STATUSES, wardId);
        Integer onTimePercentage = closedCount > 0
            ? Math.round((onTimeClosed * 100f) / closedCount)
            : null;

        return new OfficerStatsResponse(openComplaints.size(), nearSla, breached, (int) closedCount, onTimePercentage);
    }

    private void recordHistory(Complaint complaint, String status, String note) {
        ComplaintStatusHistory h = new ComplaintStatusHistory();
        h.setComplaint(complaint);
        h.setStatus(status);
        h.setNote(note);
        historyRepository.save(h);
    }
}
