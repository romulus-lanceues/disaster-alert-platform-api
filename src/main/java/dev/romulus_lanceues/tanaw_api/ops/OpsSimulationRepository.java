package dev.romulus_lanceues.tanaw_api.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OpsSimulationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public void insertSimulatedDisasterEvent(
            UUID id,
            String externalId,
            Instant now,
            String place,
            String severity,
            String rawPayload
    ) {
        String sql = """
                INSERT INTO disaster_events (
                    id, source, external_id, disaster_type, status, occurred_at,
                    source_updated_at, place, severity, raw_payload, alert_matched_at, created_at
                ) VALUES (
                    :id, 'SIMULATION', :externalId, 'EARTHQUAKE', 'test', :now,
                    :now, :place, :severity, CAST(:rawPayload AS JSONB), :now, :now
                )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("externalId", externalId)
                .addValue("now", Timestamp.from(now))
                .addValue("place", place)
                .addValue("severity", severity)
                .addValue("rawPayload", rawPayload != null && !rawPayload.isBlank() ? rawPayload : "{}");

        jdbc.update(sql, params);
    }

    public void insertEarthquake(
            UUID disasterEventId,
            double latitude,
            double longitude,
            Double magnitude,
            Double depthKm
    ) {
        String sql = """
                INSERT INTO earthquake (disaster_event_id, latitude, longitude, location, magnitude, depth_km)
                VALUES (:id, :lat, :lon, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :mag, :depthKm)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", disasterEventId)
                .addValue("lat", latitude)
                .addValue("lon", longitude)
                .addValue("mag", magnitude)
                .addValue("depthKm", depthKm != null ? depthKm : 10.0);

        jdbc.update(sql, params);
    }

    public Optional<UUID> createTargetedAlert(UUID disasterEventId, UUID alertRuleId, Instant now) {
        String sql = """
                INSERT INTO alerts (id, disaster_event_id, alert_rule_id, status, triggered_at)
                SELECT gen_random_uuid(), de.id, ar.id, 'PENDING', :now
                FROM disaster_events de
                JOIN earthquake eq ON eq.disaster_event_id = de.id
                JOIN alert_rules ar ON ar.id = :alertRuleId
                JOIN locations l ON l.id = ar.location_id
                JOIN users u ON u.id = l.user_id
                WHERE de.id = :disasterEventId
                  AND ar.disaster_type = de.disaster_type
                  AND u.status = 'ACTIVE'
                  AND ar.enabled = TRUE
                  AND (ar.minimum_magnitude IS NULL OR eq.magnitude >= ar.minimum_magnitude)
                  AND ST_DWithin(l.location, eq.location, COALESCE(ar.radius_km, 50.0) * 1000)
                ON CONFLICT (disaster_event_id, alert_rule_id) DO NOTHING
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("disasterEventId", disasterEventId)
                .addValue("alertRuleId", alertRuleId)
                .addValue("now", Timestamp.from(now));

        List<UUID> ids = jdbc.query(sql, params, (rs, i) -> rs.getObject("id", UUID.class));
        return ids.stream().findFirst();
    }

    public int deleteSimulation(UUID disasterEventId) {
        String sql = """
                DELETE FROM disaster_events
                WHERE id = :id AND source = 'SIMULATION'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", disasterEventId);

        return jdbc.update(sql, params);
    }
}
