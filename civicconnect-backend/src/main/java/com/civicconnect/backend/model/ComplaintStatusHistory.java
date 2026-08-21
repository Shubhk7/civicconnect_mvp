package com.civicconnect.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_status_history")
public class ComplaintStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    private String status;
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Integer getId() { return id; }
    public Complaint getComplaint() { return complaint; }
    public void setComplaint(Complaint complaint) { this.complaint = complaint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
