package dev.romulus_lanceues.tanaw_api.notification.endpoint;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
@DisplayName("NotificationEndpointRepository Tests")
public class NotificationEndpointRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private NotificationEndpointRepository endpointRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User primaryUser;
    private User secondaryUser;

    @BeforeEach
    void setUp() {
        primaryUser = persistUser("primary@example.com");
        secondaryUser = persistUser("secondary@example.com");
    }

    @Nested
    @DisplayName("Entity Persistence, Encryption and Auditing")
    class EntityPersistenceAndEncryption {

        @Test
        @DisplayName("Persists endpoint, encrypts destination in DB, and decrypts on retrieval")
        void persistsEndpointWithEncryptedDestinationAndAuditing() {
            String rawWebhook = "https://discord.com/api/webhooks/123456789/token-abc-123";
            String hint = "Discord webhook ...c-123";

            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination(rawWebhook)
                    .destinationHint(hint)
                    .build();

            NotificationEndpoint saved = endpointRepository.saveAndFlush(endpoint);
            UUID endpointId = saved.getId();

            entityManager.clear();

            // Verify raw DB value is encrypted and not stored as plaintext
            Object rawDbValue = entityManager.getEntityManager()
                    .createNativeQuery("SELECT destination FROM notification_endpoints WHERE id = :id")
                    .setParameter("id", endpointId)
                    .getSingleResult();

            assertThat(rawDbValue).isNotNull();
            assertThat(rawDbValue.toString()).isNotEqualTo(rawWebhook);

            // Verify JPA entity read decrypts back to original plaintext
            Optional<NotificationEndpoint> found = endpointRepository.findById(endpointId);
            assertThat(found).isPresent();
            NotificationEndpoint retrieved = found.get();
            assertThat(retrieved.getDestination()).isEqualTo(rawWebhook);
            assertThat(retrieved.getDestinationHint()).isEqualTo(hint);
            assertThat(retrieved.getChannel()).isEqualTo(NotificationChannel.DISCORD);
            assertThat(retrieved.isEnabled()).isTrue();
            assertThat(retrieved.getVerifiedAt()).isNull();
            assertThat(retrieved.getDisabledReason()).isNull();
            assertThat(retrieved.getCreatedAt()).isNotNull();
            assertThat(retrieved.getUpdatedAt()).isNotNull();
            assertThat(retrieved.getUser().getId()).isEqualTo(primaryUser.getId());
        }

        @Test
        @DisplayName("Rejects duplicate endpoint for same user and channel via unique constraint")
        void rejectsDuplicateUserAndChannel() {
            NotificationEndpoint first = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/token1")
                    .destinationHint("Discord webhook ...ken1")
                    .build();
            endpointRepository.saveAndFlush(first);

            NotificationEndpoint duplicate = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/2/token2")
                    .destinationHint("Discord webhook ...ken2")
                    .build();

            assertThatThrownBy(() -> endpointRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Allows same channel for different users")
        void allowsSameChannelForDifferentUsers() {
            NotificationEndpoint first = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/token1")
                    .destinationHint("Discord webhook ...ken1")
                    .build();
            endpointRepository.saveAndFlush(first);

            NotificationEndpoint second = NotificationEndpoint.builder()
                    .user(secondaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/2/token2")
                    .destinationHint("Discord webhook ...ken2")
                    .build();

            NotificationEndpoint savedSecond = endpointRepository.saveAndFlush(second);
            assertThat(savedSecond.getId()).isNotNull();
        }

        @Test
        @DisplayName("Cascade deletes endpoint when user is deleted")
        void cascadeDeletesEndpointWhenUserIsDeleted() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.EMAIL)
                    .destination("user@example.com")
                    .destinationHint("user@example.com")
                    .build();
            NotificationEndpoint saved = endpointRepository.saveAndFlush(endpoint);
            UUID endpointId = saved.getId();
            UUID userId = primaryUser.getId();

            entityManager.clear();
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM users WHERE id = :id")
                    .setParameter("id", userId)
                    .executeUpdate();
            entityManager.clear();

            Optional<NotificationEndpoint> found = endpointRepository.findById(endpointId);
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Repository Queries & IDOR Scoping")
    class RepositoryQueriesAndIdorScoping {

        @Test
        @DisplayName("findByIdAndUserId returns endpoint when owner matches and empty for other user (IDOR safe)")
        void findByIdAndUserIdEnforcesOwnership() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/token1")
                    .destinationHint("Discord webhook ...ken1")
                    .build();
            NotificationEndpoint saved = endpointRepository.saveAndFlush(endpoint);

            entityManager.clear();

            // Correct owner
            Optional<NotificationEndpoint> ownerResult = endpointRepository.findByIdAndUserId(saved.getId(), primaryUser.getId());
            assertThat(ownerResult).isPresent();
            assertThat(ownerResult.get().getId()).isEqualTo(saved.getId());

            // Unauthorized / different user (IDOR attempt)
            Optional<NotificationEndpoint> idorResult = endpointRepository.findByIdAndUserId(saved.getId(), secondaryUser.getId());
            assertThat(idorResult).isEmpty();
        }

        @Test
        @DisplayName("findByUserIdAndChannel returns endpoint for user and channel")
        void findByUserIdAndChannel() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.TELEGRAM)
                    .destination("123456789")
                    .destinationHint("Telegram chat 123456789")
                    .build();
            endpointRepository.saveAndFlush(endpoint);

            entityManager.clear();

            Optional<NotificationEndpoint> found = endpointRepository.findByUserIdAndChannel(primaryUser.getId(), NotificationChannel.TELEGRAM);
            assertThat(found).isPresent();
            assertThat(found.get().getChannel()).isEqualTo(NotificationChannel.TELEGRAM);

            Optional<NotificationEndpoint> notFound = endpointRepository.findByUserIdAndChannel(primaryUser.getId(), NotificationChannel.EMAIL);
            assertThat(notFound).isEmpty();
        }

        @Test
        @DisplayName("findByUserIdOrderByCreatedAtAsc returns user's endpoints in creation order")
        void findByUserIdOrderByCreatedAtAsc() {
            NotificationEndpoint ep1 = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.EMAIL)
                    .destination("primary@example.com")
                    .destinationHint("primary@example.com")
                    .build();
            NotificationEndpoint ep2 = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/token")
                    .destinationHint("Discord ...ken")
                    .build();
            NotificationEndpoint otherUserEp = NotificationEndpoint.builder()
                    .user(secondaryUser)
                    .channel(NotificationChannel.EMAIL)
                    .destination("secondary@example.com")
                    .destinationHint("secondary@example.com")
                    .build();

            endpointRepository.saveAndFlush(ep1);
            endpointRepository.saveAndFlush(ep2);
            endpointRepository.saveAndFlush(otherUserEp);

            entityManager.clear();

            List<NotificationEndpoint> endpoints = endpointRepository.findByUserIdOrderByCreatedAtAsc(primaryUser.getId());
            assertThat(endpoints).hasSize(2);
            assertThat(endpoints.get(0).getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(endpoints.get(1).getChannel()).isEqualTo(NotificationChannel.DISCORD);
        }

        @Test
        @DisplayName("existsByUserIdAndChannel returns true when endpoint exists and false otherwise")
        void existsByUserIdAndChannel() {
            NotificationEndpoint ep = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/token")
                    .destinationHint("Discord ...ken")
                    .build();
            endpointRepository.saveAndFlush(ep);

            assertThat(endpointRepository.existsByUserIdAndChannel(primaryUser.getId(), NotificationChannel.DISCORD)).isTrue();
            assertThat(endpointRepository.existsByUserIdAndChannel(primaryUser.getId(), NotificationChannel.EMAIL)).isFalse();
            assertThat(endpointRepository.existsByUserIdAndChannel(secondaryUser.getId(), NotificationChannel.DISCORD)).isFalse();
        }
    }

    @Nested
    @DisplayName("Domain Mutator Methods")
    class DomainMutatorMethods {

        @Test
        @DisplayName("enable and disable mutate state and reason correctly")
        void enableAndDisableMutators() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/token")
                    .destinationHint("Discord ...ken")
                    .build();
            endpointRepository.saveAndFlush(endpoint);

            endpoint.disable("WEBHOOK_NOT_FOUND");
            endpointRepository.saveAndFlush(endpoint);
            entityManager.clear();

            NotificationEndpoint disabled = endpointRepository.findById(endpoint.getId()).orElseThrow();
            assertThat(disabled.isEnabled()).isFalse();
            assertThat(disabled.getDisabledReason()).isEqualTo("WEBHOOK_NOT_FOUND");

            disabled.enable();
            endpointRepository.saveAndFlush(disabled);
            entityManager.clear();

            NotificationEndpoint reEnabled = endpointRepository.findById(endpoint.getId()).orElseThrow();
            assertThat(reEnabled.isEnabled()).isTrue();
            assertThat(reEnabled.getDisabledReason()).isNull();
        }

        @Test
        @DisplayName("markVerified updates verification timestamp")
        void markVerified() {
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/token")
                    .destinationHint("Discord ...ken")
                    .build();
            endpointRepository.saveAndFlush(endpoint);

            Instant verifiedTime = Instant.parse("2026-10-14T12:00:00Z");
            endpoint.markVerified(verifiedTime);
            endpointRepository.saveAndFlush(endpoint);
            entityManager.clear();

            NotificationEndpoint verified = endpointRepository.findById(endpoint.getId()).orElseThrow();
            assertThat(verified.getVerifiedAt()).isEqualTo(verifiedTime);
        }

        @Test
        @DisplayName("updateDestination changes destination and resets verifiedAt to null")
        void updateDestinationResetsVerification() {
            Instant verifiedTime = Instant.parse("2026-10-14T12:00:00Z");
            NotificationEndpoint endpoint = NotificationEndpoint.builder()
                    .user(primaryUser)
                    .channel(NotificationChannel.DISCORD)
                    .destination("https://discord.com/api/webhooks/1/token")
                    .destinationHint("Discord ...ken")
                    .verifiedAt(verifiedTime)
                    .build();
            endpointRepository.saveAndFlush(endpoint);

            endpoint.updateDestination("https://discord.com/api/webhooks/new/token2", "Discord ...ken2");
            endpointRepository.saveAndFlush(endpoint);
            entityManager.clear();

            NotificationEndpoint updated = endpointRepository.findById(endpoint.getId()).orElseThrow();
            assertThat(updated.getDestination()).isEqualTo("https://discord.com/api/webhooks/new/token2");
            assertThat(updated.getDestinationHint()).isEqualTo("Discord ...ken2");
            assertThat(updated.getVerifiedAt()).isNull();
        }
    }

    private User persistUser(String email) {
        User user = User.builder()
                .email(email)
                .passwordHash("$argon2id$v=19$m=4096,t=3,p=1$fakehash")
                .status(UserStatus.ACTIVE)
                .build();
        return entityManager.persistAndFlush(user);
    }
}
