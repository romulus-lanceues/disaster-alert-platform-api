package dev.romulus_lanceues.tanaw_api.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AlertMatchingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<UUID> claimUnmatchedEvents(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        String sql = """
                SELECT id
                FROM disaster_events
                WHERE alert_matched_at IS NULL
                    AND disaster_type = 'EARTHQUAKE'
                ORDER BY source_updated_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, i) -> rs.getObject("id", UUID.class));
    }

    public int markMatched(List<UUID> ids, Instant now) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String sql = """
                UPDATE disaster_events
                SET alert_matched_at = :now
                WHERE id IN (:ids)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("ids", ids);
        return jdbc.update(sql, params);
    }

    public Optional<Instant> findOldestUnmatchedEventTime() {
        String sql = """
                SELECT MIN(source_updated_at) AS oldest
                FROM disaster_events
                WHERE alert_matched_at IS NULL
                    AND disaster_type = 'EARTHQUAKE'
                """;
        List<Instant> result = jdbc.query(sql, (rs, i) -> {
            Timestamp ts = rs.getTimestamp("oldest");
            return ts != null ? ts.toInstant() : null;
        });
        return result.stream().filter(Objects::nonNull).findFirst();
    }

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
