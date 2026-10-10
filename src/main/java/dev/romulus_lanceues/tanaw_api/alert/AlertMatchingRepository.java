package dev.romulus_lanceues.tanaw_api.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AlertMatchingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<CreatedAlert> persistAlert(List<UUID> persistedDisasterEvents) {
        if (persistedDisasterEvents == null || persistedDisasterEvents.isEmpty()) {
            return List.of();
        }

        String sql = """
                INSERT INTO alerts (id, disaster_event_id, alert_rule_id, status, triggered_at)
                SELECT gen_random_uuid(), de.id, ar.id, 'PENDING', now()
                FROM disaster_events de
                JOIN earthquake eq ON eq.disaster_event_id = de.id
                JOIN alert_rules ar ON ar.disaster_type = de.disaster_type
                JOIN locations l ON l.id = ar.location_id
                JOIN users u ON u.id = l.user_id
                WHERE de.id IN (:eventIds)
                    AND u.status = 'ACTIVE'
                    AND ar.enabled = TRUE
                    AND (ar.minimum_magnitude IS NULL OR eq.magnitude >= ar.minimum_magnitude)
                    AND ST_DWithin(l.location, eq.location, COALESCE(ar.radius_km, 50.0) * 1000)
                ON CONFLICT (disaster_event_id, alert_rule_id) DO NOTHING
                RETURNING id, alert_rule_id, disaster_event_id;
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventIds", persistedDisasterEvents);
        return jdbc.query(sql, params,
                (rs, i) -> new CreatedAlert(
                        rs.getObject("id", UUID.class),
                        rs.getObject("alert_rule_id", UUID.class),
                        rs.getObject("disaster_event_id", UUID.class))
        );
    }
}
