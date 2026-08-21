package com.civicconnect.backend.service;

import com.civicconnect.backend.model.AuthorityMapping;
import com.civicconnect.backend.model.Ward;
import com.civicconnect.backend.repository.AuthorityMappingRepository;
import com.civicconnect.backend.repository.WardRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves which authority/department a complaint belongs to, given a
 * location and issue type. This is the platform's core differentiator:
 * the citizen never has to know whether something is MCD, MCG, PWD, or
 * NHAI territory — this service works it out.
 */
@Service
public class JurisdictionService {

    private final WardRepository wardRepository;
    private final AuthorityMappingRepository authorityMappingRepository;

    // Road types tried in priority order when the request doesn't specify
    // one explicitly. National highway / state roads are checked first
    // since they're more specific overrides within a municipal ward.
    private static final List<String> ROAD_TYPE_PRIORITY =
        List.of("national_highway", "state_pwd", "municipal");

    public JurisdictionService(WardRepository wardRepository,
                                AuthorityMappingRepository authorityMappingRepository) {
        this.wardRepository = wardRepository;
        this.authorityMappingRepository = authorityMappingRepository;
    }

    public static class RoutingResult {
        public Ward ward;
        public AuthorityMapping mapping;
        public boolean unresolved;
        public String reason;
    }

    public RoutingResult resolve(double lat, double lng, String issueType, String requestedRoadType) {
        RoutingResult result = new RoutingResult();

        // Step 1: which ward polygon contains this point
        Optional<Ward> wardOpt = wardRepository.findWardContainingPoint(lng, lat);
        if (wardOpt.isEmpty()) {
            result.unresolved = true;
            result.reason = "No ward boundary matches this location. Needs manual assignment.";
            return result;
        }
        Ward ward = wardOpt.get();
        result.ward = ward;

        // Step 2 + 3: match road ownership + issue type to a department/authority.
        // If the citizen (or client) supplied a road type, try that first;
        // otherwise walk the priority list so more specific ownership
        // (e.g. a national highway cutting through a municipal ward) wins.
        List<String> candidates = requestedRoadType != null
            ? List.of(requestedRoadType)
            : ROAD_TYPE_PRIORITY;

        for (String roadType : candidates) {
            Optional<AuthorityMapping> mapping =
                authorityMappingRepository.findByWardIdAndRoadTypeAndIssueType(ward.getId(), roadType, issueType);
            if (mapping.isPresent()) {
                result.mapping = mapping.get();
                return result;
            }
        }

        result.unresolved = true;
        result.reason = "No authority configured for issue type '" + issueType +
            "' in ward '" + ward.getName() + "'. Needs manual assignment.";
        return result;
    }
}
