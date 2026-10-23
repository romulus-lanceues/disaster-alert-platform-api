package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates that all Flyway migrations apply cleanly against the same
 * PostGIS Docker image used in development ({@code postgis/postgis:18-3.6}).
 *
 * <p>The test boots a {@link DataJpaTest} slice which auto-runs Flyway
 * before any entity manager interaction, then asserts all expected
 * tables (including {@code refresh_token_session} and the
 * {@code authentication_version} column on {@code users}) are present.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
@DisplayName("Flyway Migration Integration Test")
class FlywayMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("all migrations apply cleanly and expected tables exist")
    void allMigrationsApplyCleanly_andExpectedTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type  = 'BASE TABLE'
                ORDER BY table_name
                """,
                String.class
        );

        assertThat(tables)
                .as("Core application tables should exist after Flyway migrations")
                .contains(
                        "users",
                        "locations",
                        "geographic_areas",
                        "alert_rules",
                        "disaster_events",
                        "alerts",
                        "notifications",
                        "refresh_token_session",
                        "earthquake",
                        "notification_endpoints"
                );
    }

    @Test
    @DisplayName("users table has authentication_version column")
    void usersTable_hasAuthenticationVersionColumn() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'users'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns)
                .as("users table should contain the authentication_version column from V11 migration")
                .contains("authentication_version");
    }

    @Test
    @DisplayName("refresh_token_session table has expected columns and indexes")
    void refreshTokenSessionTable_hasExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'refresh_token_session'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns)
                .as("refresh_token_session table should have all required columns from V12 migration")
                .contains(
                        "id",
                        "user_id",
                        "family_id",
                        "token_hash",
                        "created_at",
                        "expires_at",
                        "revoked_at",
                        "replaced_by_id"
                );
    }

    @Test
    @DisplayName("Flyway migration history records all versions")
    void flywayHistory_recordsAllVersions() {
        List<String> versions = jdbcTemplate.queryForList(
                """
                SELECT version FROM flyway_schema_history
                WHERE success = true
                ORDER BY installed_rank
                """,
                String.class
        );

        assertThat(versions)
                .as("All 17 versioned migrations should have been applied successfully")
                .containsExactly(
                        "1", "2", "3", "4", "5", "6",
                        "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17"
                );
    }

    @Test
    @DisplayName("disaster_events table has alert_matched_at column from V15 migration")
    void disasterEventsTable_hasAlertMatchedAtColumnFromV15() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'disaster_events'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns)
                .as("disaster_events table should contain alert_matched_at from V15 migration")
                .contains("alert_matched_at");
    }

    @Test
    @DisplayName("earthquake table has expected columns from V14 migration")
    void earthquakeTable_hasExpectedColumnsFromV14() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'earthquake'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns)
                .as("earthquake table should have all required columns from V14 migration")
                .contains(
                        "disaster_event_id",
                        "disaster_type",
                        "latitude",
                        "longitude",
                        "location",
                        "magnitude",
                        "depth_km"
                );
    }

    @Test
    @DisplayName("disaster_events table has updated columns from V14 migration")
    void disasterEventsTable_hasUpdatedColumnsFromV14() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'disaster_events'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns)
                .as("disaster_events table should contain new V14 columns")
                .contains("status", "source_updated_at", "place");

        assertThat(columns)
                .as("disaster_events table should no longer contain dropped earthquake-specific columns")
                .doesNotContain("latitude", "longitude", "location", "magnitude", "depth_km");
    }

    @Test
    @DisplayName("notification_endpoints table has expected columns from V16 migration")
    void notificationEndpointsTable_hasExpectedColumnsFromV16() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'notification_endpoints'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns)
                .as("notification_endpoints table should have all required columns from V16 migration")
                .contains(
                        "id",
                        "user_id",
                        "channel",
                        "destination",
                        "destination_hint",
                        "enabled",
                        "verified_at",
                        "disabled_reason",
                        "created_at",
                        "updated_at"
                );
    }

    @Test
    @DisplayName("notification_endpoints table enforces unique user and channel constraint")
    void notificationEndpointsTable_rejectsDuplicateUserAndChannel() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
                userId, "endpoints-unique@tanaw.dev", "hash123"
        );

        jdbcTemplate.update(
                """
                INSERT INTO notification_endpoints (user_id, channel, destination, destination_hint)
                VALUES (?, 'DISCORD', 'https://discord.com/api/webhooks/1/token', 'https://discord.com/...ken')
                """,
                userId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO notification_endpoints (user_id, channel, destination, destination_hint)
                VALUES (?, 'DISCORD', 'https://discord.com/api/webhooks/2/token2', 'https://discord.com/...en2')
                """,
                userId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("notification_endpoints table rejects invalid channel values")
    void notificationEndpointsTable_rejectsInvalidChannel() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
                userId, "endpoints-chk@tanaw.dev", "hash123"
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO notification_endpoints (user_id, channel, destination, destination_hint)
                VALUES (?, 'SLACK', 'https://hooks.slack.com/services/1', 'https://hooks.slack.com/...')
                """,
                userId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("notification_endpoints table cascades on user deletion")
    void notificationEndpointsTable_cascadesOnUserDeletion() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
                userId, "endpoints-cascade@tanaw.dev", "hash123"
        );

        jdbcTemplate.update(
                """
                INSERT INTO notification_endpoints (user_id, channel, destination, destination_hint)
                VALUES (?, 'DISCORD', 'https://discord.com/api/webhooks/1/token', 'https://discord.com/...ken')
                """,
                userId
        );

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_endpoints WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("notifications table has expected columns from V17 migration")
    void notificationsTable_hasExpectedColumnsFromV17() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'notifications'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns)
                .as("notifications table should have new and existing columns from V17 migration")
                .contains(
                        "id",
                        "alert_id",
                        "channel",
                        "destination",
                        "status",
                        "attempt_count",
                        "sent_at",
                        "failure_reason",
                        "created_at",
                        "endpoint_id",
                        "next_attempt_at",
                        "locked_until",
                        "updated_at",
                        "provider_message_id"
                );
    }

    @Test
    @DisplayName("notifications table generates UUID default, accepts endpoint_id, and sets null on endpoint deletion")
    void notificationsTable_enforcesEndpointForeignKeyAndSetNullOnDelete() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, status) VALUES (?, ?, ?, 'ACTIVE')",
                userId, "v17-test@tanaw.dev", "hash123"
        );

        UUID areaId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO geographic_areas (id, psgc_code, name, type) VALUES (?, '1300000000', 'NCR', 'REGION')",
                areaId
        );

        UUID locationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO locations (id, user_id, name, geographic_area_id, latitude, longitude, location) VALUES (?, ?, 'Loc', ?, 14.5, 121.0, ST_SetSRID(ST_MakePoint(121.0, 14.5), 4326)::geography)",
                locationId, userId, areaId
        );

        UUID alertRuleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO alert_rules (id, location_id, disaster_type, minimum_magnitude) VALUES (?, ?, 'EARTHQUAKE', 5.0)",
                alertRuleId, locationId
        );

        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO disaster_events (id, source, external_id, disaster_type, occurred_at, source_updated_at) VALUES (?, 'USGS', ?, 'EARTHQUAKE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                eventId, "usgs_" + eventId
        );

        UUID alertId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO alerts (id, disaster_event_id, alert_rule_id, status) VALUES (?, ?, ?, 'PENDING')",
                alertId, eventId, alertRuleId
        );

        UUID endpointId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO notification_endpoints (id, user_id, channel, destination, destination_hint) VALUES (?, ?, 'DISCORD', 'https://discord.com/...', 'hint')",
                endpointId, userId
        );

        // Test insertion without specifying id (verifies DEFAULT gen_random_uuid()), with endpoint_id and provider_message_id
        jdbcTemplate.update(
                """
                INSERT INTO notifications (alert_id, channel, destination, endpoint_id, provider_message_id)
                VALUES (?, 'DISCORD', 'https://discord.com/masked', ?, 'msg_123')
                """,
                alertId, endpointId
        );

        UUID generatedId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE alert_id = ? AND channel = 'DISCORD'",
                UUID.class,
                alertId
        );
        assertThat(generatedId).isNotNull();

        // Verify ON DELETE SET NULL on endpoint_id when endpoint is deleted
        jdbcTemplate.update("DELETE FROM notification_endpoints WHERE id = ?", endpointId);
        UUID endpointAfterDelete = jdbcTemplate.queryForObject(
                "SELECT endpoint_id FROM notifications WHERE id = ?",
                UUID.class,
                generatedId
        );
        assertThat(endpointAfterDelete).isNull();
    }

    @Test
    @DisplayName("partial indexes for notifications and alerts exist")
    void partialIndexes_existForDeliveryAndSweep() {
        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename IN ('notifications', 'alerts')
                ORDER BY indexname
                """,
                String.class
        );

        assertThat(indexes)
                .as("Targeted partial indexes should exist")
                .contains(
                        "idx_notifications_pending_claim",
                        "idx_notifications_processing_lease",
                        "idx_notifications_pending_endpoint",
                        "idx_alerts_pending_sweep"
                )
                .as("Low-selectivity status index on notifications should have been dropped")
                .doesNotContain("idx_notifications_status");
    }
}
