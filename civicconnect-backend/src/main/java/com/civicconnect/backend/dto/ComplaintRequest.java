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

    // If true, the report is never linked to the caller's account even
    // if they're logged in (a valid JWT was sent). Defaults to false,
    // meaning a logged-in citizen's reports auto-link unless they opt
    // out here. Deliberately NOT a userId field — the backend derives
    // identity from the JWT only; this DTO has no field a client could
    // use to claim someone else's identity.
    private boolean anonymous = false;

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
    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
}
