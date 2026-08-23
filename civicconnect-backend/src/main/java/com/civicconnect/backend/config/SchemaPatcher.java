package com.civicconnect.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds columns/rows that existing Docker volumes will not pick up from
 * db/init.sql (that file only runs on first Postgres init). Idempotent.
 */
@Component
public class SchemaPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaPatcher.class);
    private final JdbcTemplate jdbc;

    public SchemaPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("ALTER TABLE complaints ADD COLUMN IF NOT EXISTS resolution_note TEXT");
        jdbc.execute("ALTER TABLE complaints ADD COLUMN IF NOT EXISTS verification_verdict VARCHAR(80)");
        jdbc.execute("ALTER TABLE complaints ADD COLUMN IF NOT EXISTS verification_confidence DOUBLE PRECISION");
        jdbc.execute("ALTER TABLE complaints ADD COLUMN IF NOT EXISTS verification_similarity DOUBLE PRECISION");
        jdbc.execute("ALTER TABLE complaints ADD COLUMN IF NOT EXISTS verification_change_detected BOOLEAN");
        jdbc.execute("ALTER TABLE complaints ADD COLUMN IF NOT EXISTS ai_issue_type VARCHAR(80)");
        jdbc.execute("ALTER TABLE complaints ADD COLUMN IF NOT EXISTS ai_confidence DOUBLE PRECISION");

        jdbc.update("""
            INSERT INTO authority_mapping (ward_id, road_type, issue_type, department, authority_name, sla_hours)
            SELECT w.id, 'municipal', 'ev_charger', 'Electrical',
                   CASE WHEN w.city ILIKE 'gurugram' THEN 'MCG' ELSE 'MCD' END, 48
            FROM wards w
            WHERE NOT EXISTS (
                SELECT 1 FROM authority_mapping am
                WHERE am.ward_id = w.id AND am.road_type = 'municipal' AND am.issue_type = 'ev_charger'
            )
            """);
        log.info("Schema patch applied (resolution/verification/AI columns + ev_charger mappings).");
    }
}
