package com.civicconnect.backend.repository;

import com.civicconnect.backend.model.AuthorityMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorityMappingRepository extends JpaRepository<AuthorityMapping, Integer> {

    Optional<AuthorityMapping> findByWardIdAndRoadTypeAndIssueType(
        Integer wardId, String roadType, String issueType
    );
}
