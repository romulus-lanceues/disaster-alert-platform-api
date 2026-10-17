package dev.romulus_lanceues.tanaw_api.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.romulus_lanceues.tanaw_api.user.UserStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final DestinationCrypto crypto;

    public List<NotificationDeliveryContext> findDeliveryContexts(Collection<UUID> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT
                    n.id AS notification_id,
                    n.alert_id AS alert_id,
                    n.channel AS channel,
                    n.destination AS destination_hint_snapshot,
                    ne.id AS endpoint_id,
                    ne.destination AS encrypted_destination,
                    ne.destination_hint AS endpoint_destination_hint,
                    ne.enabled AS endpoint_enabled,
                    ne.verified_at AS endpoint_verified_at,
                    u.id AS user_id,
                    u.status AS user_status,
                    l.name AS location_name,
                    de.place AS place,
                    de.occurred_at AS occurred_at,
                    de.status AS event_status,
                    de.severity AS severity,
                    de.raw_payload -> 'properties' ->> 'url' AS usgs_url,
                    eq.magnitude AS magnitude,
                    eq.depth_km AS depth_km,
                    CASE
                        WHEN eq.location IS NOT NULL AND l.location IS NOT NULL
                        THEN ST_Distance(l.location, eq.location) / 1000.0
                        ELSE NULL
                    END AS distance_km
                FROM notifications n
                JOIN alerts a ON a.id = n.alert_id
                JOIN alert_rules ar ON ar.id = a.alert_rule_id
                JOIN locations l ON l.id = ar.location_id
                JOIN users u ON u.id = l.user_id
                JOIN disaster_events de ON de.id = a.disaster_event_id
                LEFT JOIN earthquake eq ON eq.disaster_event_id = de.id
                LEFT JOIN notification_endpoints ne ON ne.id = n.endpoint_id
                WHERE n.id IN (:notificationIds)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("notificationIds", notificationIds);

        return jdbc.query(sql, params, (rs, rowNum) -> {
            UUID notificationId = rs.getObject("notification_id", UUID.class);
            UUID alertId = rs.getObject("alert_id", UUID.class);
            NotificationChannel channel = NotificationChannel.valueOf(rs.getString("channel"));

            UUID endpointId = rs.getObject("endpoint_id", UUID.class);
            String encryptedDestination = rs.getString("encrypted_destination");
            String destination = (endpointId != null && encryptedDestination != null)
                    ? crypto.decrypt(encryptedDestination)
                    : null;

            String endpointHint = rs.getString("endpoint_destination_hint");
            String destinationHint = endpointHint != null ? endpointHint : rs.getString("destination_hint_snapshot");

            Boolean endpointEnabled = rs.getObject("endpoint_enabled", Boolean.class);

            Timestamp verifiedTs = rs.getTimestamp("endpoint_verified_at");
            Instant endpointVerifiedAt = verifiedTs != null ? verifiedTs.toInstant() : null;

            UUID userId = rs.getObject("user_id", UUID.class);
            String userStatusStr = rs.getString("user_status");
            UserStatus userStatus = userStatusStr != null ? UserStatus.valueOf(userStatusStr) : null;

            String locationName = rs.getString("location_name");
            String place = rs.getString("place");
            Timestamp occurredTs = rs.getTimestamp("occurred_at");
            Instant occurredAt = occurredTs != null ? occurredTs.toInstant() : null;
            String eventStatus = rs.getString("event_status");
            String severity = rs.getString("severity");
            String usgsUrl = rs.getString("usgs_url");

            Double magnitude = getDoubleOrNull(rs, "magnitude");
            Double depthKm = getDoubleOrNull(rs, "depth_km");
            Double distanceKm = getDoubleOrNull(rs, "distance_km");

            return new NotificationDeliveryContext(
                    notificationId,
                    alertId,
                    channel,
                    destination,
                    destinationHint,
                    endpointId,
                    endpointEnabled,
                    endpointVerifiedAt,
                    userId,
                    userStatus,
                    locationName,
                    place,
                    occurredAt,
                    eventStatus,
                    severity,
                    usgsUrl,
                    magnitude,
                    depthKm,
                    distanceKm
            );
        });
    }

    private static Double getDoubleOrNull(ResultSet rs, String column) throws SQLException {
        Number number = (Number) rs.getObject(column);
        return number != null ? number.doubleValue() : null;
    }

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
