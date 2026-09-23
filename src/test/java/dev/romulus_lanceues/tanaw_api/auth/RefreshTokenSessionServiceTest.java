package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Unit Tests")
class RefreshTokenSessionServiceTest {

    @Mock
    private RefreshTokenSessionRepository refreshTokenSessionRepository;

    private JwtProperties jwtProperties;
    private Clock fixedClock;
    private Instant now;
    private RefreshTokenSessionService refreshTokenSessionService;
    private User testUser;

    @BeforeEach
    void setUp() {
        byte[] secretBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            secretBytes[i] = (byte) (i + 1);
        }
        String base64Secret = Base64.getEncoder().encodeToString(secretBytes);

        jwtProperties = new JwtProperties(
                "https://auth.tanaw.dev",
                "tanaw-api",
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                base64Secret
        );

        now = Instant.parse("2026-09-23T10:00:00Z");
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        refreshTokenSessionService = new RefreshTokenSessionService(
                refreshTokenSessionRepository,
                jwtProperties,
                fixedClock
        );

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hashed-password")
                .status(UserStatus.ACTIVE)
                .authenticationVersion(1L)
                .build();
    }

    @Nested
    @DisplayName("issueRefreshToken")
    class IssueRefreshToken {

        @Test
        @DisplayName("should generate raw token and store only SHA-256 hash in database")
        void shouldStoreOnlyHashAndNeverRawToken() {
            when(refreshTokenSessionRepository.save(any(RefreshTokenSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            String rawToken = refreshTokenSessionService.issueRefreshToken(testUser);

            assertThat(rawToken).isNotBlank();

            ArgumentCaptor<RefreshTokenSession> captor = ArgumentCaptor.forClass(RefreshTokenSession.class);
            verify(refreshTokenSessionRepository).save(captor.capture());

            RefreshTokenSession savedSession = captor.getValue();
            assertThat(savedSession.getUser()).isEqualTo(testUser);
            assertThat(savedSession.getFamilyId()).isNotNull();
            assertThat(savedSession.getCreatedAt()).isEqualTo(now);
            assertThat(savedSession.getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
            assertThat(savedSession.getRevokedAt()).isNull();

            String expectedHash = refreshTokenSessionService.hashToken(rawToken);
            assertThat(savedSession.getTokenHash())
                    .isEqualTo(expectedHash)
                    .isNotEqualTo(rawToken)
                    .matches("^[0-9a-f]{64}$");
        }
    }

    @Nested
    @DisplayName("rotate")
    class Rotate {

        @Test
        @DisplayName("should return new token and mark old session replaced when valid")
        void shouldReturnNewTokenAndMarkOldSessionReplaced_whenValid() {
            String oldRawToken = "old-raw-refresh-token";
            String oldTokenHash = refreshTokenSessionService.hashToken(oldRawToken);
            UUID familyId = UUID.randomUUID();

            RefreshTokenSession oldSession = RefreshTokenSession.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .familyId(familyId)
                    .tokenHash(oldTokenHash)
                    .createdAt(now.minus(Duration.ofDays(1)))
                    .expiresAt(now.plus(Duration.ofDays(29)))
                    .build();

            when(refreshTokenSessionRepository.findByTokenHashForUpdate(oldTokenHash))
                    .thenReturn(Optional.of(oldSession));
            when(refreshTokenSessionRepository.save(any(RefreshTokenSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RefreshTokenRotationResult result = refreshTokenSessionService.rotate(oldRawToken);

            assertThat(result.newRawToken()).isNotBlank().isNotEqualTo(oldRawToken);
            assertThat(result.user()).isEqualTo(testUser);


            assertThat(oldSession.getRevokedAt()).isEqualTo(now);
            assertThat(oldSession.getReplacedBy()).isNotNull();
            assertThat(oldSession.isActive(now)).isFalse();


            ArgumentCaptor<RefreshTokenSession> captor = ArgumentCaptor.forClass(RefreshTokenSession.class);
            verify(refreshTokenSessionRepository, times(2)).save(captor.capture());

            List<RefreshTokenSession> savedSessions = captor.getAllValues();
            RefreshTokenSession newSession = savedSessions.get(0);
            assertThat(newSession.getFamilyId()).isEqualTo(familyId);
            assertThat(newSession.getUser()).isEqualTo(testUser);
            assertThat(newSession.getCreatedAt()).isEqualTo(now);
            assertThat(newSession.getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
            assertThat(newSession.getTokenHash())
                    .isEqualTo(refreshTokenSessionService.hashToken(result.newRawToken()))
                    .matches("^[0-9a-f]{64}$");

            assertThat(oldSession.getReplacedBy()).isSameAs(newSession);
        }

        @Test
        @DisplayName("should revoke entire family and throw 401 when token was already revoked (reuse detection)")
        void shouldRevokeFamilyAndThrow401_whenTokenReusedOrAlreadyRevoked() {
            String rawToken = "reused-refresh-token";
            String tokenHash = refreshTokenSessionService.hashToken(rawToken);
            UUID familyId = UUID.randomUUID();

            RefreshTokenSession alreadyRevokedSession = RefreshTokenSession.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .familyId(familyId)
                    .tokenHash(tokenHash)
                    .createdAt(now.minus(Duration.ofDays(2)))
                    .expiresAt(now.plus(Duration.ofDays(28)))
                    .revokedAt(now.minus(Duration.ofDays(1)))
                    .build();

            when(refreshTokenSessionRepository.findByTokenHashForUpdate(tokenHash))
                    .thenReturn(Optional.of(alreadyRevokedSession));

            assertThatThrownBy(() -> refreshTokenSessionService.rotate(rawToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessageContaining("already been revoked");

            verify(refreshTokenSessionRepository).revokeFamily(eq(familyId), eq(now));
            verify(refreshTokenSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject expired token with 401")
        void shouldRejectExpiredTokenWith401() {
            String rawToken = "expired-refresh-token";
            String tokenHash = refreshTokenSessionService.hashToken(rawToken);

            RefreshTokenSession expiredSession = RefreshTokenSession.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .familyId(UUID.randomUUID())
                    .tokenHash(tokenHash)
                    .createdAt(now.minus(Duration.ofDays(31)))
                    .expiresAt(now.minusSeconds(1))
                    .build();

            when(refreshTokenSessionRepository.findByTokenHashForUpdate(tokenHash))
                    .thenReturn(Optional.of(expiredSession));

            assertThatThrownBy(() -> refreshTokenSessionService.rotate(rawToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessageContaining("expired");

            verify(refreshTokenSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject unknown token with 401")
        void shouldRejectUnknownTokenWith401() {
            String rawToken = "unknown-token";
            String tokenHash = refreshTokenSessionService.hashToken(rawToken);

            when(refreshTokenSessionRepository.findByTokenHashForUpdate(tokenHash))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenSessionService.rotate(rawToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessageContaining("not found");

            verify(refreshTokenSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject inactive user with 401")
        void shouldRejectInactiveUserWith401() {
            String rawToken = "valid-token-inactive-user";
            String tokenHash = refreshTokenSessionService.hashToken(rawToken);

            User disabledUser = User.builder()
                    .id(UUID.randomUUID())
                    .email("disabled@example.com")
                    .passwordHash("pass")
                    .status(UserStatus.DISABLED)
                    .build();

            RefreshTokenSession session = RefreshTokenSession.builder()
                    .id(UUID.randomUUID())
                    .user(disabledUser)
                    .familyId(UUID.randomUUID())
                    .tokenHash(tokenHash)
                    .createdAt(now.minus(Duration.ofDays(1)))
                    .expiresAt(now.plus(Duration.ofDays(29)))
                    .build();

            when(refreshTokenSessionRepository.findByTokenHashForUpdate(tokenHash))
                    .thenReturn(Optional.of(session));

            assertThatThrownBy(() -> refreshTokenSessionService.rotate(rawToken))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessageContaining("inactive");

            verify(refreshTokenSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject blank token with 401")
        void shouldRejectBlankTokenWith401() {
            assertThatThrownBy(() -> refreshTokenSessionService.rotate("   "))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            assertThatThrownBy(() -> refreshTokenSessionService.rotate(null))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            verifyNoInteractions(refreshTokenSessionRepository);
        }
    }

    @Nested
    @DisplayName("revokeCurrent")
    class RevokeCurrent {

        @Test
        @DisplayName("should revoke session by token hash")
        void shouldRevokeByTokenHash() {
            String rawToken = "my-token-to-revoke";
            String expectedHash = refreshTokenSessionService.hashToken(rawToken);

            refreshTokenSessionService.revokeCurrent(rawToken);

            verify(refreshTokenSessionRepository).revokeByTokenHash(eq(expectedHash), eq(now));
        }

        @Test
        @DisplayName("should do nothing when rawToken is null or blank")
        void shouldDoNothing_whenTokenIsBlankOrNull() {
            refreshTokenSessionService.revokeCurrent(null);
            refreshTokenSessionService.revokeCurrent("  ");

            verifyNoInteractions(refreshTokenSessionRepository);
        }
    }

    @Nested
    @DisplayName("revokeAllForUser")
    class RevokeAllForUser {

        @Test
        @DisplayName("should revoke all sessions for given userId")
        void shouldRevokeAllForUserId() {
            UUID userId = UUID.randomUUID();

            refreshTokenSessionService.revokeAllForUser(userId);

            verify(refreshTokenSessionRepository).revokeAllByUserId(eq(userId), eq(now));
        }

        @Test
        @DisplayName("should do nothing when userId is null")
        void shouldDoNothing_whenUserIdIsNull() {
            refreshTokenSessionService.revokeAllForUser(null);

            verifyNoInteractions(refreshTokenSessionRepository);
        }
    }
}
