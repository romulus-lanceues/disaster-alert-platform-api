package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RefreshTokenSession Tests")
class RefreshTokenSessionTest {

    private User user;
    private UUID familyId;
    private Instant now;
    private Instant expiresAt;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hashed_pass")
                .status(UserStatus.ACTIVE)
                .build();

        familyId = UUID.randomUUID();
        now = Instant.parse("2026-09-22T10:00:00Z");
        expiresAt = now.plus(7, ChronoUnit.DAYS);
    }

    @Nested
    @DisplayName("isActive")
    class IsActive {

        @Test
        @DisplayName("should return true when not revoked and current time is before expiresAt")
        void shouldReturnTrue_whenNotRevokedAndBeforeExpiration() {
            RefreshTokenSession session = RefreshTokenSession.builder()
                    .user(user)
                    .familyId(familyId)
                    .tokenHash("hash_abc")
                    .expiresAt(expiresAt)
                    .build();

            assertThat(session.isActive(now)).isTrue();
        }

        @Test
        @DisplayName("should return false when session is revoked")
        void shouldReturnFalse_whenRevoked() {
            RefreshTokenSession session = RefreshTokenSession.builder()
                    .user(user)
                    .familyId(familyId)
                    .tokenHash("hash_abc")
                    .expiresAt(expiresAt)
                    .revokedAt(now.minus(1, ChronoUnit.HOURS))
                    .build();

            assertThat(session.isActive(now)).isFalse();
        }

        @Test
        @DisplayName("should return false when current time is after expiresAt")
        void shouldReturnFalse_whenExpired() {
            RefreshTokenSession session = RefreshTokenSession.builder()
                    .user(user)
                    .familyId(familyId)
                    .tokenHash("hash_abc")
                    .expiresAt(expiresAt)
                    .build();

            Instant pastExpiration = expiresAt.plus(1, ChronoUnit.SECONDS);
            assertThat(session.isActive(pastExpiration)).isFalse();
        }

        @Test
        @DisplayName("should return false when current time is exactly at expiresAt")
        void shouldReturnFalse_whenExactlyAtExpiration() {
            RefreshTokenSession session = RefreshTokenSession.builder()
                    .user(user)
                    .familyId(familyId)
                    .tokenHash("hash_abc")
                    .expiresAt(expiresAt)
                    .build();

            assertThat(session.isActive(expiresAt)).isFalse();
        }

        @Test
        @DisplayName("should return false when session is both revoked and expired")
        void shouldReturnFalse_whenRevokedAndExpired() {
            RefreshTokenSession session = RefreshTokenSession.builder()
                    .user(user)
                    .familyId(familyId)
                    .tokenHash("hash_abc")
                    .expiresAt(expiresAt)
                    .revokedAt(now.minus(1, ChronoUnit.HOURS))
                    .build();

            Instant pastExpiration = expiresAt.plus(1, ChronoUnit.DAYS);
            assertThat(session.isActive(pastExpiration)).isFalse();
        }
    }

    @Nested
    @DisplayName("rotateTo")
    class RotateTo {

        @Test
        @DisplayName("should revoke current session and link to replacement session")
        void shouldRevokeCurrentAndLinkToReplacementSession() {
            RefreshTokenSession currentSession = RefreshTokenSession.builder()
                    .user(user)
                    .familyId(familyId)
                    .tokenHash("hash_current")
                    .expiresAt(expiresAt)
                    .build();

            RefreshTokenSession nextSession = RefreshTokenSession.builder()
                    .user(user)
                    .familyId(familyId)
                    .tokenHash("hash_next")
                    .expiresAt(now.plus(7, ChronoUnit.DAYS))
                    .build();

            Instant rotationTime = now.plus(1, ChronoUnit.HOURS);
            currentSession.rotateTo(nextSession, rotationTime);

            assertThat(currentSession.getRevokedAt()).isEqualTo(rotationTime);
            assertThat(currentSession.getReplacedBy()).isSameAs(nextSession);
            assertThat(currentSession.isActive(rotationTime)).isFalse();
        }
    }
}
