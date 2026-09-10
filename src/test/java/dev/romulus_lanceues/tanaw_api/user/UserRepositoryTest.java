package dev.romulus_lanceues.tanaw_api.user;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
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

    @Test
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

    @Test
    void shouldFindUserByEmail_whenUserExists() {
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
    void shouldReturnEmpty_whenFindByEmail_givenNonExistentEmail() {
        Optional<User> notFound = userRepository.findByEmail("nonexistent@example.com");

        assertThat(notFound).isEmpty();
    }

    @Test
    void shouldReturnTrue_whenExistsByEmail_givenExistingEmail() {
        entityManager.persistAndFlush(activeUser);

        boolean exists = userRepository.existsByEmail(activeUser.getEmail());

        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalse_whenExistsByEmail_givenNonExistentEmail() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        assertThat(exists).isFalse();
    }

    @Test
    void shouldFindUser_whenFindByEmailAndStatus_givenMatchingCriteria() {
        entityManager.persistAndFlush(activeUser);
        entityManager.persistAndFlush(disabledUser);

        Optional<User> found = userRepository.findByEmailAndStatus(activeUser.getEmail(), UserStatus.ACTIVE);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(activeUser.getId());
        assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldReturnEmpty_whenFindByEmailAndStatus_givenMismatchedStatus() {
        entityManager.persistAndFlush(activeUser);

        Optional<User> found = userRepository.findByEmailAndStatus(activeUser.getEmail(), UserStatus.DISABLED);

        assertThat(found).isEmpty();
    }
}
