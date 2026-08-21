package com.civicconnect.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "authority_mapping")
public class AuthorityMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id")
    private Ward ward;

    @Column(name = "road_type")
    private String roadType;

    @Column(name = "issue_type")
    private String issueType;

    private String department;

    @Column(name = "authority_name")
    private String authorityName;

    @Column(name = "sla_hours")
    private Integer slaHours;

    public Integer getId() { return id; }
    public Ward getWard() { return ward; }
    public String getRoadType() { return roadType; }
    public String getIssueType() { return issueType; }
    public String getDepartment() { return department; }
    public String getAuthorityName() { return authorityName; }
    public Integer getSlaHours() { return slaHours; }
}
