package dev.romulus_lanceues.tanaw_api.location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByUserId(UUID userId);

    Optional<Location> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    List<Location> findByGeographicAreaId(UUID geographicAreaId);

    /**
     * Finds all locations within a given radius (in kilometers) from a geographic point (WGS84).
     */
    @Query(value = """
            SELECT l.* FROM locations l
            WHERE ST_DWithin(
                l.location,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                :radiusInKm * 1000
            )
            """, nativeQuery = true)
    List<Location> findWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusInKm") double radiusInKm
    );
}
