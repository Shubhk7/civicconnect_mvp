package com.civicconnect.backend.repository;

import com.civicconnect.backend.model.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WardRepository extends JpaRepository<Ward, Integer> {

    // The actual jurisdiction lookup: "which ward polygon contains this point"
    // This is the PostGIS query that replaces manual boundary math.
    @Query(value =
        "SELECT * FROM wards w " +
        "WHERE ST_Contains(w.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) " +
        "LIMIT 1",
        nativeQuery = true)
    Optional<Ward> findWardContainingPoint(@Param("lng") double lng, @Param("lat") double lat);
}
