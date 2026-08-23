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

    // Used by the SLA escalation job: complaints whose deadline has
    // already passed, that are still open, and that haven't already been
    // flagged as escalated (so the job doesn't redo work every run).
    @Query(
        "SELECT c FROM Complaint c " +
        "WHERE c.slaDeadline IS NOT NULL " +
        "AND c.slaDeadline < CURRENT_TIMESTAMP " +
        "AND c.escalated = false " +
        "AND c.status IN ('REPORTED','ACKNOWLEDGED','ASSIGNED','IN_PROGRESS','UNASSIGNED','REOPENED')"
    )
    List<Complaint> findNewlyBreachedComplaints();

    // Duplicate check: same issue type within ~50 meters, still open.
    // ST_DWithin with geography cast gives distance in meters.
    @Query(value =
        "SELECT * FROM complaints c " +
        "WHERE c.issue_type = :issueType " +
        "AND c.status NOT IN ('RESOLVED','CLOSED') " +
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
