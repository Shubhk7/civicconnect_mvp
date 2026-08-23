package com.civicconnect.backend.repository;

import com.civicconnect.backend.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Integer> {

    List<Complaint> findByWardId(Integer wardId);

    List<Complaint> findByStatus(String status);

    List<Complaint> findByReportedByUserIdOrderByCreatedAtDesc(Integer reportedByUserId);

    // Used by /api/complaints/stats to compute the "open" / "SLA < 24h" /
    // "breached" KPIs. Fetches the small working set (open complaints,
    // optionally ward-scoped) so the actual deadline math — which needs
    // "now" — can happen in Java rather than being baked into SQL.
    List<Complaint> findByStatusIn(List<String> statuses);

    List<Complaint> findByWardIdAndStatusIn(Integer wardId, List<String> statuses);

    // On-time resolution %: closed complaints (RESOLVED/VERIFIED/CLOSED)
    // that were never flagged as escalated, out of all closed complaints.
    // Uses the persisted `escalated` flag rather than re-deriving breach
    // from slaDeadline, because slaDeadline comparisons only tell you
    // about *now* — a resolved complaint's deadline is almost always in
    // the past regardless of whether it was met. `escalated` is the one
    // fact that survives resolution: SlaEscalationService sets it exactly
    // once, the moment a complaint first goes overdue, and it's never
    // cleared afterward.
    @Query(
        "SELECT COUNT(c) FROM Complaint c " +
        "WHERE c.status IN :closedStatuses " +
        "AND (:wardId IS NULL OR c.ward.id = :wardId)"
    )
    long countClosed(@Param("closedStatuses") List<String> closedStatuses, @Param("wardId") Integer wardId);

    @Query(
        "SELECT COUNT(c) FROM Complaint c " +
        "WHERE c.status IN :closedStatuses " +
        "AND c.escalated = false " +
        "AND (:wardId IS NULL OR c.ward.id = :wardId)"
    )
    long countClosedOnTime(@Param("closedStatuses") List<String> closedStatuses, @Param("wardId") Integer wardId);

    // Used by the SLA escalation job: complaints whose deadline has
    // already passed, that are still open, and that haven't already been
    // flagged as escalated (so the job doesn't redo work every run).
    @Query(
        "SELECT c FROM Complaint c " +
        "WHERE c.slaDeadline IS NOT NULL " +
        "AND c.slaDeadline < CURRENT_TIMESTAMP " +
        "AND c.escalated = false " +
        "AND c.status IN ('REPORTED','ACKNOWLEDGED','ASSIGNED','IN_PROGRESS','UNASSIGNED','REOPENED','AWAITING_VERIFICATION')"
    )
    List<Complaint> findNewlyBreachedComplaints();

    // Duplicate check: same issue type within ~50 meters, still open.
    // ST_DWithin with geography cast gives distance in meters.
    @Query(value =
        "SELECT * FROM complaints c " +
        "WHERE c.issue_type = :issueType " +
        "AND c.status NOT IN ('RESOLVED','VERIFIED','CLOSED') " +
        "AND ST_DWithin(c.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters) " +
        "LIMIT 1",
        nativeQuery = true)
    List<Complaint> findNearbyDuplicates(
        @Param("lng") double lng,
        @Param("lat") double lat,
        @Param("issueType") String issueType,
        @Param("radiusMeters") double radiusMeters
    );
}
