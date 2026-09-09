package dev.romulus_lanceues.tanaw_api.disaster;

import dev.romulus_lanceues.tanaw_api.enums.DisasterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisasterEventRepository extends JpaRepository<DisasterEvent, UUID>, JpaSpecificationExecutor<DisasterEvent> {

    Optional<DisasterEvent> findBySourceAndExternalId(String source, String externalId);

    boolean existsBySourceAndExternalId(String source, String externalId);

    Page<DisasterEvent> findByDisasterTypeOrderByOccurredAtDesc(DisasterType disasterType, Pageable pageable);

    List<DisasterEvent> findByOccurredAtBetweenOrderByOccurredAtDesc(Instant start, Instant end);

    Page<DisasterEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Optional<DisasterEvent> findTopByDisasterTypeOrderByOccurredAtDesc(DisasterType disasterType);

    /**
     * Finds disaster events within a specified radius (in kilometers) from a geographic point (WGS84).
     */
    @Query(value = """
            SELECT de.* FROM disaster_events de
            WHERE ST_DWithin(
                de.location,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                :radiusInKm * 1000
            )
            ORDER BY de.occurred_at DESC
            """, nativeQuery = true)
    List<DisasterEvent> findWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusInKm") double radiusInKm
    );
}
