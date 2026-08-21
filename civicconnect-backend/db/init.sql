-- CivicConnect MVP schema
CREATE EXTENSION IF NOT EXISTS postgis;

-- Wards: real-world ward boundaries. For the MVP demo we store a simple
-- polygon per ward (rough boundary is fine — accuracy matters less than
-- the routing logic working end-to-end).
CREATE TABLE wards (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    boundary        GEOMETRY(POLYGON, 4326) NOT NULL
);

CREATE INDEX idx_wards_boundary ON wards USING GIST (boundary);

-- Road ownership layer: which authority owns which class of road within a ward.
-- road_type distinguishes municipal roads from state highways/national highways,
-- since a single ward can contain roads owned by different bodies.
CREATE TABLE authority_mapping (
    id              SERIAL PRIMARY KEY,
    ward_id         INTEGER NOT NULL REFERENCES wards(id),
    road_type       VARCHAR(50) NOT NULL,   -- 'municipal', 'state_pwd', 'national_highway'
    issue_type      VARCHAR(50) NOT NULL,   -- 'pothole', 'garbage', 'streetlight', 'water_leak', 'ewaste'
    department      VARCHAR(100) NOT NULL,  -- 'Roads', 'Sanitation', 'Electrical', 'Water', 'Recycling'
    authority_name  VARCHAR(100) NOT NULL,  -- 'MCD', 'MCG', 'PWD', 'NHAI'
    sla_hours       INTEGER NOT NULL DEFAULT 72,
    UNIQUE (ward_id, road_type, issue_type)
);

-- Complaints: the actual citizen reports.
CREATE TABLE complaints (
    id              SERIAL PRIMARY KEY,
    issue_type      VARCHAR(50) NOT NULL,
    description     TEXT,
    photo_url       TEXT,
    after_photo_url TEXT,
    location        GEOMETRY(POINT, 4326) NOT NULL,
    road_type       VARCHAR(50) NOT NULL DEFAULT 'municipal',
    ward_id         INTEGER REFERENCES wards(id),
    department      VARCHAR(100),
    authority_name  VARCHAR(100),
    status          VARCHAR(30) NOT NULL DEFAULT 'REPORTED',
        -- REPORTED, ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, RESOLVED, VERIFIED, CLOSED, REOPENED
    sla_deadline    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_complaints_location ON complaints USING GIST (location);
CREATE INDEX idx_complaints_status ON complaints (status);
CREATE INDEX idx_complaints_ward ON complaints (ward_id);

-- Status history: every transition, so the citizen-facing timeline is real
-- data, not derived guesswork.
CREATE TABLE complaint_status_history (
    id              SERIAL PRIMARY KEY,
    complaint_id    INTEGER NOT NULL REFERENCES complaints(id),
    status          VARCHAR(30) NOT NULL,
    note            TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
