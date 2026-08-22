package com.civicconnect.backend.dto;

import com.civicconnect.backend.model.ComplaintStatusHistory;

import java.time.LocalDateTime;

/**
 * ComplaintStatusHistory (the JPA entity) has a getComplaint() that
 * returns the full parent Complaint — including reportedByUserId — so
 * returning entities directly from GET /api/complaints/{id} would leak
 * complainant identity through the timeline, even though ComplaintResponse
 * itself is clean. This DTO exposes only what a public timeline needs.
 */
public class PublicTimelineEntry {
    public String status;
    public String note;
    public LocalDateTime createdAt;

    public static PublicTimelineEntry from(ComplaintStatusHistory h) {
        PublicTimelineEntry e = new PublicTimelineEntry();
        e.status = h.getStatus();
        e.note = h.getNote();
        e.createdAt = h.getCreatedAt();
        return e;
    }
}
