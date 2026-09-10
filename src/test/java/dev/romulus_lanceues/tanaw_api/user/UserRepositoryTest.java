package dev.romulus_lanceues.tanaw_api.user;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.enums.UserStatus;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingTestConfig.class})
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User activeUser;
    private User disabledUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .email("alice@example.com")
                .passwordHash("hash_alice_123")
                .status(UserStatus.ACTIVE)
                .build();

        disabledUser = User.builder()
                .email("bob@example.com")
                .passwordHash("hash_bob_456")
                .status(UserStatus.DISABLED)
                .build();
    }

    @Nested
    @DisplayName("Entity Persistence and Auditing")
    class PersistenceAndAuditing {

        @Test
        @DisplayName("should persist user and generate audit fields")
        void shouldPersistUserAndGenerateAuditFields() {
            User user = User.builder()
                    .email("charlie@example.com")
                    .passwordHash("hash_charlie_789")
                    .status(UserStatus.ACTIVE)
                    .build();

            User saved = userRepository.saveAndFlush(user);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("should find user by email when user exists")
        void shouldFindUserByEmailWhenUserExists() {
            entityManager.persistAndFlush(activeUser);
            entityManager.persistAndFlush(disabledUser);

            Optional<User> found = userRepository.findByEmail(activeUser.getEmail());

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(user -> {
                        assertThat(user.getId()).isEqualTo(activeUser.getId());
                        assertThat(user.getEmail()).isEqualTo(activeUser.getEmail());
                        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
                    });
        }

        @Test
        @DisplayName("should return empty when email does not exist")
        void shouldReturnEmptyWhenFindByEmailGivenNonExistentEmail() {
            Optional<User> notFound = userRepository.findByEmail("nonexistent@example.com");

            assertThat(notFound).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByEmail")
    class ExistsByEmail {

        @Test
        @DisplayName("should return true when email exists")
        void shouldReturnTrueWhenExistsByEmailGivenExistingEmail() {
            entityManager.persistAndFlush(activeUser);

            boolean exists = userRepository.existsByEmail(activeUser.getEmail());

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when email does not exist")
        void shouldReturnFalseWhenExistsByEmailGivenNonExistentEmail() {
            boolean exists = userRepository.existsByEmail("nonexistent@example.com");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByEmailAndStatus")
    class FindByEmailAndStatus {

        @Test
        @DisplayName("should find user when email and status match")
        void shouldFindUserWhenFindByEmailAndStatusGivenMatchingCriteria() {
            entityManager.persistAndFlush(activeUser);
            entityManager.persistAndFlush(disabledUser);

            Optional<User> found = userRepository.findByEmailAndStatus(activeUser.getEmail(), UserStatus.ACTIVE);

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(user -> {
                        assertThat(user.getId()).isEqualTo(activeUser.getId());
                        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
                    });
        }

        @Test
        @DisplayName("should return empty when status does not match")
        void shouldReturnEmptyWhenFindByEmailAndStatusGivenMismatchedStatus() {
            entityManager.persistAndFlush(activeUser);

            Optional<User> found = userRepository.findByEmailAndStatus(activeUser.getEmail(), UserStatus.DISABLED);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should return empty when email does not exist")
        void shouldReturnEmptyWhenFindByEmailAndStatusGivenNonExistentEmail() {
            Optional<User> found = userRepository.findByEmailAndStatus("nonexistent@example.com", UserStatus.ACTIVE);

            assertThat(found).isEmpty();
        }
    }
}
