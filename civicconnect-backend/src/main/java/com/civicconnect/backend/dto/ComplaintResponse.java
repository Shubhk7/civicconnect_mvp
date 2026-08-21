package com.civicconnect.backend.dto;

import com.civicconnect.backend.model.Complaint;

import java.time.LocalDateTime;

public class ComplaintResponse {
    public Integer id;
    public String issueType;
    public String description;
    public String status;
    public String wardName;
    public String roadType;
    public String department;
    public String authorityName;
    public LocalDateTime slaDeadline;
    public LocalDateTime createdAt;
    public boolean isDuplicate;
    public String message;

    public static ComplaintResponse from(Complaint c) {
        ComplaintResponse r = new ComplaintResponse();
        r.id = c.getId();
        r.issueType = c.getIssueType();
        r.description = c.getDescription();
        r.status = c.getStatus();
        r.wardName = c.getWard() != null ? c.getWard().getName() : null;
        r.roadType = c.getRoadType();
        r.department = c.getDepartment();
        r.authorityName = c.getAuthorityName();
        r.slaDeadline = c.getSlaDeadline();
        r.createdAt = c.getCreatedAt();
        return r;
    }
}
