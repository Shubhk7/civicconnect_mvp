package com.civicconnect.backend.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "issue_type", nullable = false)
    private String issueType;

    private String description;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "after_photo_url")
    private String afterPhotoUrl;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;

    @Column(name = "road_type")
    private String roadType = "municipal";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id")
    private Ward ward;

    private String department;

    @Column(name = "authority_name")
    private String authorityName;

    private String status = "REPORTED";

    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // --- getters / setters ---
    public Integer getId() { return id; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getAfterPhotoUrl() { return afterPhotoUrl; }
    public void setAfterPhotoUrl(String afterPhotoUrl) { this.afterPhotoUrl = afterPhotoUrl; }

    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }

    public String getRoadType() { return roadType; }
    public void setRoadType(String roadType) { this.roadType = roadType; }

    public Ward getWard() { return ward; }
    public void setWard(Ward ward) { this.ward = ward; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getAuthorityName() { return authorityName; }
    public void setAuthorityName(String authorityName) { this.authorityName = authorityName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getSlaDeadline() { return slaDeadline; }
    public void setSlaDeadline(LocalDateTime slaDeadline) { this.slaDeadline = slaDeadline; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
