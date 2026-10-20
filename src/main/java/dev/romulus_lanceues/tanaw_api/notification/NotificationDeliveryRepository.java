package dev.romulus_lanceues.tanaw_api.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.romulus_lanceues.tanaw_api.user.UserStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
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

    public List<ClaimedNotification> claimPendingNotifications(int limit, Instant now, Duration lease) {
        if (limit <= 0 || now == null || lease == null) {
            return List.of();
        }

        String sql = """
                WITH claimed AS (
                    SELECT id, next_attempt_at
                    FROM notifications
                    WHERE status = 'PENDING'
                      AND next_attempt_at <= :now
                    ORDER BY next_attempt_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                ),
                updated AS (
                    UPDATE notifications n
                    SET status = 'PROCESSING',
                        attempt_count = n.attempt_count + 1,
                        locked_until = :lockedUntil,
                        updated_at = :now
                    FROM claimed c
                    WHERE n.id = c.id
                    RETURNING n.id, n.attempt_count, c.next_attempt_at
                )
                SELECT id, attempt_count
                FROM updated
                ORDER BY next_attempt_at ASC, id ASC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("now", Timestamp.from(now))
                .addValue("lockedUntil", Timestamp.from(now.plus(lease)));

        return jdbc.query(sql, params, (rs, rowNum) -> new ClaimedNotification(
                rs.getObject("id", UUID.class),
                rs.getInt("attempt_count")
        ));
    }

    public int reapExpiredLeases(Instant now, int maxAttempts) {
        if (now == null || maxAttempts <= 0) {
            return 0;
        }

        String sql = """
                UPDATE notifications
                SET status = CASE
                        WHEN attempt_count >= :maxAttempts THEN 'FAILED'
                        ELSE 'PENDING'
                    END,
                    failure_reason = CASE
                        WHEN attempt_count >= :maxAttempts THEN 'Lease expired; max attempts exceeded'
                        ELSE 'Lease expired; returned to queue'
                    END,
                    next_attempt_at = :now,
                    locked_until = NULL,
                    updated_at = :now
                WHERE status = 'PROCESSING'
                  AND locked_until < :now
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("maxAttempts", maxAttempts);

        return jdbc.update(sql, params);
    }

    public boolean recordSuccess(UUID id, int claimedAttempt, String providerMessageId, Instant sentAt, Instant now) {
        if (id == null || now == null) {
            return false;
        }

        String safeMessageId = (providerMessageId != null && providerMessageId.length() > 100)
                ? providerMessageId.substring(0, 100)
                : providerMessageId;

        String sql = """
                UPDATE notifications
                SET status = 'SENT',
                    provider_message_id = :providerMessageId,
                    sent_at = :sentAt,
                    failure_reason = NULL,
                    locked_until = NULL,
                    updated_at = :now
                WHERE id = :id
                  AND status = 'PROCESSING'
                  AND attempt_count = :claimedAttempt
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("claimedAttempt", claimedAttempt)
                .addValue("providerMessageId", safeMessageId)
                .addValue("sentAt", sentAt != null ? Timestamp.from(sentAt) : Timestamp.from(now))
                .addValue("now", Timestamp.from(now));

        return jdbc.update(sql, params) > 0;
    }

    public boolean recordPreSendCancellation(UUID id, int claimedAttempt, String reason, Instant now) {
        if (id == null || now == null) {
            return false;
        }

        String sql = """
                UPDATE notifications
                SET status = 'CANCELLED',
                    failure_reason = :reason,
                    locked_until = NULL,
                    updated_at = :now
                WHERE id = :id
                  AND status = 'PROCESSING'
                  AND attempt_count = :claimedAttempt
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("claimedAttempt", claimedAttempt)
                .addValue("reason", reason)
                .addValue("now", Timestamp.from(now));

        return jdbc.update(sql, params) > 0;
    }

    public boolean recordRetryableFailure(UUID id, int claimedAttempt, String reason, Instant nextAttemptAt, int maxAttempts, Instant now) {
        if (id == null || now == null) {
            return false;
        }

        boolean exhausted = claimedAttempt >= maxAttempts;
        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("claimedAttempt", claimedAttempt)
                .addValue("reason", reason)
                .addValue("now", Timestamp.from(now));

        if (exhausted) {
            sql = """
                    UPDATE notifications
                    SET status = 'FAILED',
                        failure_reason = :reason,
                        locked_until = NULL,
                        updated_at = :now
                    WHERE id = :id
                      AND status = 'PROCESSING'
                      AND attempt_count = :claimedAttempt
                    """;
        } else {
            sql = """
                    UPDATE notifications
                    SET status = 'PENDING',
                        next_attempt_at = :nextAttemptAt,
                        failure_reason = :reason,
                        locked_until = NULL,
                        updated_at = :now
                    WHERE id = :id
                      AND status = 'PROCESSING'
                      AND attempt_count = :claimedAttempt
                    """;
            params.addValue("nextAttemptAt", nextAttemptAt != null ? Timestamp.from(nextAttemptAt) : Timestamp.from(now));
        }

        return jdbc.update(sql, params) > 0;
    }

    public boolean recordPermanentFailure(UUID id, int claimedAttempt, String reason, Instant now) {
        if (id == null || now == null) {
            return false;
        }

        String sql = """
                UPDATE notifications
                SET status = 'FAILED',
                    failure_reason = :reason,
                    locked_until = NULL,
                    updated_at = :now
                WHERE id = :id
                  AND status = 'PROCESSING'
                  AND attempt_count = :claimedAttempt
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("claimedAttempt", claimedAttempt)
                .addValue("reason", reason)
                .addValue("now", Timestamp.from(now));

        return jdbc.update(sql, params) > 0;
    }

    public int invalidateEndpointAndCancelPending(UUID endpointId, String reason, Instant now) {
        if (endpointId == null || now == null) {
            return 0;
        }

        String endpointReason = (reason != null && reason.length() > 100)
                ? reason.substring(0, 100)
                : reason;

        String updateEndpointSql = """
                UPDATE notification_endpoints
                SET enabled = FALSE,
                    disabled_reason = :reason,
                    updated_at = :now
                WHERE id = :endpointId
                """;

        MapSqlParameterSource endpointParams = new MapSqlParameterSource()
                .addValue("endpointId", endpointId)
                .addValue("reason", endpointReason)
                .addValue("now", Timestamp.from(now));

        jdbc.update(updateEndpointSql, endpointParams);

        String cancelPendingSql = """
                UPDATE notifications
                SET status = 'CANCELLED',
                    failure_reason = :reason,
                    locked_until = NULL,
                    updated_at = :now
                WHERE endpoint_id = :endpointId
                  AND status = 'PENDING'
                """;

        MapSqlParameterSource notifParams = new MapSqlParameterSource()
                .addValue("endpointId", endpointId)
                .addValue("reason", reason)
                .addValue("now", Timestamp.from(now));

        return jdbc.update(cancelPendingSql, notifParams);
    }
}
