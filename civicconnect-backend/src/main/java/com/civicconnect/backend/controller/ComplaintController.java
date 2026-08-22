package com.civicconnect.backend.controller;

import com.civicconnect.backend.dto.ComplaintRequest;
import com.civicconnect.backend.dto.ComplaintResponse;
import com.civicconnect.backend.dto.PublicTimelineEntry;
import com.civicconnect.backend.model.Complaint;
import com.civicconnect.backend.repository.ComplaintRepository;
import com.civicconnect.backend.repository.ComplaintStatusHistoryRepository;
import com.civicconnect.backend.security.AuthenticatedUser;
import com.civicconnect.backend.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * The authenticated principal, if any. Spring Security's
     * SecurityContext is the single source of truth for identity now —
     * populated by JwtAuthFilter, never by anything a controller trusts
     * directly from the request.
     */
    private AuthenticatedUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user != null && "ADMIN".equals(user.role());
    }

    private boolean isOfficer(AuthenticatedUser user) {
        return user != null && "OFFICER".equals(user.role());
    }

    // Citizen submits a new report. Public — no account required. If a
    // valid JWT is present, the report auto-links to that account unless
    // the body sets "anonymous": true. There is no field on
    // ComplaintRequest for a client to supply a user id directly —
    // identity only ever comes from the SecurityContext, which only
    // Spring Security (via JwtAuthFilter) can populate.
    @PostMapping
    public ComplaintResponse submit(@Valid @RequestBody ComplaintRequest req) {
        AuthenticatedUser authUser = currentUser();
        Integer userId = authUser != null ? authUser.userId() : null;
        return complaintService.submit(req, userId);
    }

    // Citizen's own report history. SecurityConfig requires a valid JWT
    // for this path (401 via JsonAuthenticationEntryPoint if missing).
    // Filtered server-side by the user id embedded in the token — there
    // is no query parameter that lets the client ask for someone else's
    // complaints.
    @GetMapping("/my")
    public List<ComplaintResponse> myComplaints() {
        AuthenticatedUser authUser = currentUser();
        // SecurityConfig already guarantees authUser != null here, but
        // we don't trust that alone — if it's somehow null, fail closed.
        if (authUser == null) {
            throw new IllegalStateException("Authenticated endpoint reached without a principal");
        }
        return complaintRepository
            .findByReportedByUserIdOrderByCreatedAtDesc(authUser.userId())
            .stream().map(ComplaintResponse::from).toList();
    }

    // Public: single complaint + its status timeline. Both DTOs used here
    // are explicitly sanitized — ComplaintResponse never carries
    // reportedByUserId, and PublicTimelineEntry avoids serializing the
    // full nested Complaint entity that the raw JPA entity would pull in.
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Integer id) {
        Complaint c = complaintRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + id));
        List<PublicTimelineEntry> history = historyRepository.findByComplaintIdOrderByCreatedAtAsc(id)
            .stream().map(PublicTimelineEntry::from).toList();
        return Map.of(
            "complaint", ComplaintResponse.from(c),
            "timeline", history
        );
    }

    // Public, unauthenticated feed for the homepage's public ledger and
    // any other logged-out view of recent activity. Deliberately a
    // separate path from the plain GET /api/complaints below — that one
    // is officer/admin-only. This one exists specifically so a page like
    // the home ledger can show recent activity to a visitor with no
    // account, without needing officer-level access to the full list.
    // Returns the same ComplaintResponse shape, which never includes who
    // filed a report, so this is safe to leave fully public. Capped at
    // 50 results and always newest-first, since this is a feed, not an
    // admin export.
    @GetMapping("/public")
    public List<ComplaintResponse> publicFeed(
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
        return complaints.stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(50)
            .map(ComplaintResponse::from)
            .toList();
    }

    // Officer/admin dashboard: list complaints, optionally filtered by
    // ward or status. SecurityConfig already restricts this path to
    // ROLE_OFFICER/ROLE_ADMIN. On top of that role check, an OFFICER is
    // additionally scoped to their own ward here — they can request
    // another ward's id, but the server ignores that and uses their own.
    // ADMIN has no such restriction.
    @GetMapping
    public List<ComplaintResponse> list(
        @RequestParam(required = false) Integer wardId,
        @RequestParam(required = false) String status
    ) {
        AuthenticatedUser authUser = currentUser();
        Integer effectiveWardId = wardId;

        if (isOfficer(authUser) && !isAdmin(authUser)) {
            // Officers only ever see their own ward, regardless of what
            // wardId (if any) was requested.
            effectiveWardId = authUser.wardId();
        }

        List<Complaint> complaints;
        if (effectiveWardId != null) {
            complaints = complaintRepository.findByWardId(effectiveWardId);
        } else if (status != null) {
            complaints = complaintRepository.findByStatus(status);
        } else {
            complaints = complaintRepository.findAll();
        }
        return complaints.stream().map(ComplaintResponse::from).toList();
    }

    // Officer marks the issue fixed and uploads an after-photo.
    // SecurityConfig restricts this to ROLE_OFFICER/ROLE_ADMIN; an
    // OFFICER is further restricted here to complaints in their own ward.
    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        ResponseEntity<?> denied = denyIfOutsideOfficerWard(id);
        if (denied != null) return denied;

        Complaint c = complaintService.markResolved(id, body.get("afterPhotoUrl"));
        return ResponseEntity.ok(ComplaintResponse.from(c));
    }

    // Generic status update (e.g. AI verification passes -> VERIFIED/CLOSED,
    // or fails -> REOPENED). Same ward restriction as /resolve.
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        ResponseEntity<?> denied = denyIfOutsideOfficerWard(id);
        if (denied != null) return denied;

        Complaint c = complaintService.updateStatus(id, body.get("status"), body.get("note"));
        return ResponseEntity.ok(ComplaintResponse.from(c));
    }

    /**
     * Returns a 403 ResponseEntity if the caller is an OFFICER (not ADMIN)
     * trying to act on a complaint outside their assigned ward, otherwise
     * null to indicate "proceed". ADMIN always passes. Role itself
     * (OFFICER/ADMIN vs CITIZEN) is already enforced by SecurityConfig
     * before this method ever runs — this only adds the ward-ownership
     * layer that a URL-pattern rule can't express.
     */
    private ResponseEntity<?> denyIfOutsideOfficerWard(Integer complaintId) {
        AuthenticatedUser authUser = currentUser();
        if (isAdmin(authUser)) return null;

        if (isOfficer(authUser)) {
            Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));
            Integer complaintWardId = c.getWard() != null ? c.getWard().getId() : null;
            if (authUser.wardId() == null || !authUser.wardId().equals(complaintWardId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This complaint is outside your assigned ward."));
            }
        }
        return null;
    }
}
