package com.civicconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ComplaintRequest {

    @NotBlank
    private String issueType; // pothole, garbage, streetlight, water_leak, ewaste

    private String description;
    private String photoUrl;

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;

    // getters / setters
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
}
