package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.enums.DisasterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    List<AlertRule> findByLocationId(UUID locationId);

    List<AlertRule> findByLocationUserId(UUID userId);

    Optional<AlertRule> findByIdAndLocationUserId(UUID id, UUID userId);

    List<AlertRule> findByEnabledTrueAndDisasterType(DisasterType disasterType);

    /**
     * Finds active alert rules that match a disaster event's type, location within radius,
     * and optional magnitude threshold.
     */
    @Query(value = """
            SELECT ar.* FROM alert_rules ar
            JOIN locations l ON ar.location_id = l.id
            WHERE ar.enabled = TRUE
              AND ar.disaster_type = :disasterType
              AND (:magnitude IS NULL OR ar.minimum_magnitude IS NULL OR :magnitude >= ar.minimum_magnitude)
              AND (
                  ar.radius_km IS NULL
                  OR ST_DWithin(
                      l.location,
                      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                      ar.radius_km * 1000
                  )
              )
            """, nativeQuery = true)
    List<AlertRule> findMatchingRules(
            @Param("disasterType") String disasterType,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("magnitude") Double magnitude
    );

    default List<AlertRule> findMatchingRules(
            DisasterType disasterType,
            double latitude,
            double longitude,
            Double magnitude
    ) {
        return findMatchingRules(disasterType.name(), latitude, longitude, magnitude);
    }
}
