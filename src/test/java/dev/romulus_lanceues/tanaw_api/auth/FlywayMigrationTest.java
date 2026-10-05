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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
                        "refresh_token_session"
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
                .as("All 13 versioned migrations should have been applied successfully")
                .containsExactly(
                        "1", "2", "3", "4", "5", "6",
                        "7", "8", "9", "10", "11", "12", "13"
                );
    }
}
