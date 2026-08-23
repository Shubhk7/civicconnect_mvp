package com.civicconnect.backend.dto;

import com.civicconnect.backend.model.Complaint;

import java.time.LocalDateTime;

public class ComplaintResponse {
    public Integer id;
    public String issueType;
    public String description;
    public String status;
    public String wardName;
    public Integer wardId;
    public String roadType;
    public String department;
    public String authorityName;
    public LocalDateTime slaDeadline;
    public LocalDateTime createdAt;
    public boolean isDuplicate;
    public String message;
    public Integer upvoteCount;
    // How many citizens this complaint represents: the original reporter
    // plus everyone who upvoted it OR whose independent report was
    // merged into this one as a duplicate (see ComplaintService#submit —
    // a detected duplicate is recorded as an upvote via the same
    // tryUpvote path, so upvoteCount already carries both signals). This
    // is the number the frontend should show as "N residents affected" —
    // upvoteCount alone undercounts by one (the reporter never upvotes
    // their own report) and conflates two different citizen actions.
    public int affectedCount;
    // True when slaDeadline has passed and the complaint is still open.
    // Computed at read time, not stored, so it's always accurate relative
    // to "now" rather than whatever it was when last written.
    public boolean slaBreached;
    public boolean escalated;
    // Included so the frontend can plot reports on a map (heatmap page).
    // This is the report's location only — never anything about who
    // filed it.
    public Double lat;
    public Double lng;

    private static final java.util.Set<String> OPEN_STATUSES = java.util.Set.of(
        "REPORTED", "ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "UNASSIGNED", "REOPENED"
    );

    public static ComplaintResponse from(Complaint c) {
        ComplaintResponse r = new ComplaintResponse();
        r.id = c.getId();
        r.issueType = c.getIssueType();
        r.description = c.getDescription();
        r.status = c.getStatus();
        r.wardName = c.getWard() != null ? c.getWard().getName() : null;
        r.wardId = c.getWard() != null ? c.getWard().getId() : null;
        r.roadType = c.getRoadType();
        r.department = c.getDepartment();
        r.authorityName = c.getAuthorityName();
        r.slaDeadline = c.getSlaDeadline();
        r.createdAt = c.getCreatedAt();
        r.upvoteCount = c.getUpvoteCount() != null ? c.getUpvoteCount() : 0;
        r.affectedCount = r.upvoteCount + 1;
        r.slaBreached = c.getSlaDeadline() != null
            && c.getSlaDeadline().isBefore(LocalDateTime.now())
            && OPEN_STATUSES.contains(c.getStatus());
        r.escalated = c.isEscalated();
        if (c.getLocation() != null) {
            r.lat = c.getLocation().getY();
            r.lng = c.getLocation().getX();
        }
        return r;
    }
}
