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
        -- REPORTED, ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, AWAITING_VERIFICATION, RESOLVED, VERIFIED, CLOSED, REOPENED
    sla_deadline    TIMESTAMP,
    upvote_count    INTEGER NOT NULL DEFAULT 0,
    escalated       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    resolution_note TEXT,
    verification_verdict VARCHAR(80),
    verification_confidence DOUBLE PRECISION,
    verification_similarity DOUBLE PRECISION,
    verification_change_detected BOOLEAN,
    ai_issue_type   VARCHAR(80),
    ai_confidence   DOUBLE PRECISION
);

CREATE INDEX idx_complaints_location ON complaints USING GIST (location);
CREATE INDEX idx_complaints_status ON complaints (status);
CREATE INDEX idx_complaints_ward ON complaints (ward_id);

-- Users: citizens and officials. Passwords are stored as BCrypt hashes,
-- never plaintext. Email is stored as-is (needed for login/lookup and
-- notifications) but should be treated as PII — access-controlled, not
-- publicly exposed via any API response.
CREATE TABLE users (
    id              SERIAL PRIMARY KEY,
    username        VARCHAR(50) UNIQUE NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    phone_number    VARCHAR(20) UNIQUE,     -- E.164-ish format, e.g. +919876543210
    password_hash   VARCHAR(255) NOT NULL,  -- BCrypt hash, ~60 chars, stored with room to spare
    full_name       VARCHAR(150),
    role            VARCHAR(30) NOT NULL DEFAULT 'CITIZEN', -- CITIZEN, OFFICER, ADMIN
    ward_id         INTEGER REFERENCES wards(id), -- for OFFICER accounts: which ward they manage
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_phone ON users (phone_number);

-- Link complaints to the citizen who filed them (nullable — anonymous
-- reporting stays supported, per the platform's privacy design).
ALTER TABLE complaints ADD COLUMN reported_by_user_id INTEGER REFERENCES users(id);

-- Status history: every transition, so the citizen-facing timeline is real
-- data, not derived guesswork.
CREATE TABLE complaint_status_history (
    id              SERIAL PRIMARY KEY,
    complaint_id    INTEGER NOT NULL REFERENCES complaints(id),
    status          VARCHAR(30) NOT NULL,
    note            TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- Upvotes: one row per (complaint, voter). voter_key is either an
-- authenticated user id (as text) or an anonymous per-browser token sent
-- by the frontend — this is a lightweight anti-spam measure, not a strong
-- identity system, appropriate for a hackathon demo. The unique
-- constraint is what actually prevents repeat-upvoting, not trust in the
-- client.
CREATE TABLE complaint_upvotes (
    id              SERIAL PRIMARY KEY,
    complaint_id    INTEGER NOT NULL REFERENCES complaints(id),
    voter_key       VARCHAR(200) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (complaint_id, voter_key)
);


