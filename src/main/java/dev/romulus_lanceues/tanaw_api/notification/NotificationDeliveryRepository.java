package dev.romulus_lanceues.tanaw_api.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<ClaimedAlert> claimPendingAlerts(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        String sql = """
                SELECT a.id, de.occurred_at
                FROM alerts a
                JOIN disaster_events de ON de.id = a.disaster_event_id
                WHERE a.status = 'PENDING'
                ORDER BY a.triggered_at ASC
                LIMIT :limit
                FOR UPDATE OF a SKIP LOCKED
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit);

        return jdbc.query(sql, params, (rs, i) -> new ClaimedAlert(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant()
        ));
    }

    public int cancelAlerts(List<UUID> alertIds) {
        if (alertIds == null || alertIds.isEmpty()) {
            return 0;
        }

        String sql = """
                UPDATE alerts
                SET status = 'CANCELLED'
                WHERE id IN (:ids)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", alertIds);

        return jdbc.update(sql, params);
    }

    public int markAlertsProcessed(List<UUID> alertIds) {
        if (alertIds == null || alertIds.isEmpty()) {
            return 0;
        }

        String sql = """
                UPDATE alerts
                SET status = 'PROCESSED'
                WHERE id IN (:ids)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", alertIds);

        return jdbc.update(sql, params);
    }

    public int createNotificationsForAlerts(List<UUID> alertIds, Set<NotificationChannel> supportedChannels, Instant now) {
        if (alertIds == null || alertIds.isEmpty() || supportedChannels == null || supportedChannels.isEmpty()) {
            return 0;
        }

        List<String> channelNames = supportedChannels.stream()
                .map(Enum::name)
                .toList();

        String sql = """
                INSERT INTO notifications (
                    id,
                    alert_id,
                    endpoint_id,
                    channel,
                    destination,
                    status,
                    attempt_count,
                    next_attempt_at,
                    created_at,
                    updated_at
                )
                SELECT
                    gen_random_uuid(),
                    a.id,
                    ne.id,
                    ne.channel,
                    ne.destination_hint,
                    'PENDING',
                    0,
                    :now,
                    :now,
                    :now
                FROM alerts a
                JOIN alert_rules ar ON ar.id = a.alert_rule_id
                JOIN locations l ON l.id = ar.location_id
                JOIN users u ON u.id = l.user_id
                JOIN notification_endpoints ne ON ne.user_id = u.id
                WHERE a.id IN (:alertIds)
                  AND u.status = 'ACTIVE'
                  AND ne.enabled = TRUE
                  AND ne.verified_at IS NOT NULL
                  AND ne.channel IN (:channels)
                ON CONFLICT (alert_id, channel) DO NOTHING
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("alertIds", alertIds)
                .addValue("channels", channelNames)
                .addValue("now", Timestamp.from(now));

        return jdbc.update(sql, params);
    }
}
