package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.enums.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.enums.UserStatus;
import dev.romulus_lanceues.tanaw_api.jts.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.user.User;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
@DisplayName("LocationRepository Tests")
class LocationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();

    private User user;
    private GeographicArea geographicArea;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("alice@example.com")
                .passwordHash("hash_alice_123")
                .status(UserStatus.ACTIVE)
                .build();

        geographicArea = GeographicArea.builder()
                .psgcCode("137600000")
                .name("City of Manila")
                .type(GeographicAreaType.MUNICIPALITY)
                .active(true)
                .build();

        entityManager.persistAndFlush(user);
        entityManager.persistAndFlush(geographicArea);
    }

    @Nested
    @DisplayName("Entity Persistence and Auditing")
    class PersistenceAndAuditing {

        @Test
        @DisplayName("should persist location and generate audit fields")
        void shouldPersistLocationAndGenerateAuditFields() {
            Location location = Location.create(user, "Home", "123 Main St", geographicArea, 14.5995, 120.9842, geoPointFactory);

            Location saved = locationRepository.saveAndFlush(location);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should persist location with all fields")
        void shouldPersistLocationWithAllFields() {
            Location location = Location.create(user, "Office", "456 Ayala Ave", geographicArea, 14.5547, 121.0244, geoPointFactory);

            Location saved = locationRepository.saveAndFlush(location);

            Optional<Location> found = locationRepository.findById(saved.getId());

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(persisted -> {
                        assertThat(persisted.getUser().getId()).isEqualTo(user.getId());
                        assertThat(persisted.getName()).isEqualTo("Office");
                        assertThat(persisted.getAddress()).isEqualTo("456 Ayala Ave");
                        assertThat(persisted.getGeographicArea().getId()).isEqualTo(geographicArea.getId());
                        assertThat(persisted.getLatitude()).isEqualTo(14.5547);
                        assertThat(persisted.getLongitude()).isEqualTo(121.0244);
                        assertThat(persisted.getLocation()).isNotNull();
                        assertThat(persisted.getLocation().getY()).isEqualTo(14.5547);
                        assertThat(persisted.getLocation().getX()).isEqualTo(121.0244);
                    });
        }

        @Test
        @DisplayName("should persist location without address")
        void shouldPersistLocationWithoutAddress() {
            Location location = Location.create(user, "Warehouse", null, geographicArea, 14.6760, 121.0437, geoPointFactory);

            Location saved = locationRepository.saveAndFlush(location);

            Optional<Location> found = locationRepository.findById(saved.getId());

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(persisted -> assertThat(persisted.getAddress()).isNull());
        }
    }

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("should find locations by user ID")
        void shouldFindLocationsByUserId() {
            Location location1 = Location.create(user, "Home 1", "123 Main St", geographicArea, 14.5995, 120.9842, geoPointFactory);
            Location location2 = Location.create(user, "Home 2", "456 Main St", geographicArea, 14.6000, 120.9850, geoPointFactory);

            locationRepository.saveAndFlush(location1);
            locationRepository.saveAndFlush(location2);

            List<Location> foundLocations = locationRepository.findByUserId(user.getId());

            assertThat(foundLocations)
                    .hasSize(2)
                    .extracting(Location::getName)
                    .containsExactlyInAnyOrder("Home 1", "Home 2");
        }

        @Test
        @DisplayName("should return empty list when user has no locations")
        void shouldReturnEmptyListWhenUserHasNoLocations() {
            User anotherUser = User.builder()
                    .email("bob@example.com")
                    .passwordHash("hash_bob_123")
                    .status(UserStatus.ACTIVE)
                    .build();
            entityManager.persistAndFlush(anotherUser);

            List<Location> foundLocations = locationRepository.findByUserId(anotherUser.getId());

            assertThat(foundLocations).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdAndUserId")
    class FindByIdAndUserId {

        @Test
        @DisplayName("should find location by ID and user ID")
        void shouldFindLocationByIdAndUserId() {
            Location location = Location.create(user, "Home", "123 Main St", geographicArea, 14.5995, 120.9842, geoPointFactory);
            Location saved = locationRepository.saveAndFlush(location);

            Optional<Location> found = locationRepository.findByIdAndUserId(saved.getId(), user.getId());

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(persisted -> {
                        assertThat(persisted.getId()).isEqualTo(saved.getId());
                        assertThat(persisted.getUser().getId()).isEqualTo(user.getId());
                        assertThat(persisted.getName()).isEqualTo("Home");
                    });
        }

        @Test
        @DisplayName("should return empty when location belongs to different user")
        void shouldReturnEmptyWhenFindByIdAndUserIdWithDifferentUser() {
            Location location = Location.create(user, "Home", "123 Main St", geographicArea, 14.5995, 120.9842, geoPointFactory);
            Location saved = locationRepository.saveAndFlush(location);

            User anotherUser = User.builder()
                    .email("charlie@example.com")
                    .passwordHash("hash_charlie_123")
                    .status(UserStatus.ACTIVE)
                    .build();
            entityManager.persistAndFlush(anotherUser);

            Optional<Location> found = locationRepository.findByIdAndUserId(saved.getId(), anotherUser.getId());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should return empty when location ID does not exist")
        void shouldReturnEmptyWhenFindByIdAndUserIdWithNonExistentLocationId() {
            Optional<Location> found = locationRepository.findByIdAndUserId(UUID.randomUUID(), user.getId());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByIdAndUserId")
    class ExistsByIdAndUserId {

        @Test
        @DisplayName("should return true when location exists for user")
        void shouldReturnTrueWhenExistsByIdAndUserId() {
            Location location = Location.create(user, "Home", "123 Main St", geographicArea, 14.5995, 120.9842, geoPointFactory);
            Location saved = locationRepository.saveAndFlush(location);

            boolean exists = locationRepository.existsByIdAndUserId(saved.getId(), user.getId());

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when location belongs to different user")
        void shouldReturnFalseWhenExistsByIdAndUserIdWithDifferentUser() {
            Location location = Location.create(user, "Home", "123 Main St", geographicArea, 14.5995, 120.9842, geoPointFactory);
            Location saved = locationRepository.saveAndFlush(location);

            User anotherUser = User.builder()
                    .email("david@example.com")
                    .passwordHash("hash_david_123")
                    .status(UserStatus.ACTIVE)
                    .build();
            entityManager.persistAndFlush(anotherUser);

            boolean exists = locationRepository.existsByIdAndUserId(saved.getId(), anotherUser.getId());

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should return false when location ID does not exist")
        void shouldReturnFalseWhenExistsByIdAndUserIdWithNonExistentLocationId() {
            boolean exists = locationRepository.existsByIdAndUserId(UUID.randomUUID(), user.getId());

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByGeographicAreaId")
    class FindByGeographicAreaId {

        @Test
        @DisplayName("should find locations belonging to specified geographic area")
        void shouldFindLocationsByGeographicAreaId() {
            GeographicArea otherArea = GeographicArea.builder()
                    .psgcCode("137400000")
                    .name("Quezon City")
                    .type(GeographicAreaType.MUNICIPALITY)
                    .active(true)
                    .build();
            entityManager.persistAndFlush(otherArea);

            Location loc1 = Location.create(user, "Manila Spot 1", "123 Port Area", geographicArea, 14.5995, 120.9842, geoPointFactory);
            Location loc2 = Location.create(user, "Manila Spot 2", "456 Ermita", geographicArea, 14.5800, 120.9800, geoPointFactory);
            Location loc3 = Location.create(user, "QC Spot", "789 Diliman", otherArea, 14.6537, 121.0685, geoPointFactory);

            locationRepository.saveAndFlush(loc1);
            locationRepository.saveAndFlush(loc2);
            locationRepository.saveAndFlush(loc3);

            List<Location> manilaLocations = locationRepository.findByGeographicAreaId(geographicArea.getId());

            assertThat(manilaLocations)
                    .hasSize(2)
                    .extracting(Location::getName)
                    .containsExactlyInAnyOrder("Manila Spot 1", "Manila Spot 2");
        }

        @Test
        @DisplayName("should return empty list when no locations exist in geographic area")
        void shouldReturnEmptyListWhenNoLocationsInGeographicArea() {
            GeographicArea emptyArea = GeographicArea.builder()
                    .psgcCode("137500000")
                    .name("Pasig City")
                    .type(GeographicAreaType.MUNICIPALITY)
                    .active(true)
                    .build();
            entityManager.persistAndFlush(emptyArea);

            List<Location> foundLocations = locationRepository.findByGeographicAreaId(emptyArea.getId());

            assertThat(foundLocations).isEmpty();
        }
    }

    @Nested
    @DisplayName("findWithinRadius")
    class FindWithinRadius {

        @Test
        @DisplayName("should return locations within given radius and exclude locations outside")
        void shouldFindLocationsWithinRadius() {
            // Reference center: Manila City Hall (approx. 14.5895, 120.9815)
            double centerLat = 14.5895;
            double centerLon = 120.9815;

            // Intramuros: ~1.1 km away
            Location intramuros = Location.create(user, "Intramuros", "Manila", geographicArea, 14.5895, 120.9747, geoPointFactory);
            // Makati CBD: ~6.0 km away
            Location makati = Location.create(user, "Makati CBD", "Makati", geographicArea, 14.5547, 121.0244, geoPointFactory);
            // Cebu City: ~570 km away
            Location cebu = Location.create(user, "Cebu Office", "Cebu", geographicArea, 10.3157, 123.8854, geoPointFactory);

            locationRepository.saveAndFlush(intramuros);
            locationRepository.saveAndFlush(makati);
            locationRepository.saveAndFlush(cebu);

            // 1. Search radius of 3.0 km: should only find Intramuros (~1.1 km)
            List<Location> within3Km = locationRepository.findWithinRadius(centerLat, centerLon, 3.0);
            assertThat(within3Km)
                    .hasSize(1)
                    .extracting(Location::getName)
                    .containsExactly("Intramuros");

            // 2. Search radius of 10.0 km: should find Intramuros and Makati, excluding Cebu
            List<Location> within10Km = locationRepository.findWithinRadius(centerLat, centerLon, 10.0);
            assertThat(within10Km)
                    .hasSize(2)
                    .extracting(Location::getName)
                    .containsExactlyInAnyOrder("Intramuros", "Makati CBD");
        }

        @Test
        @DisplayName("should return empty list when no locations fall within given radius")
        void shouldReturnEmptyListWhenNoLocationsWithinRadius() {
            Location location = Location.create(user, "Manila Hub", "Manila", geographicArea, 14.5995, 120.9842, geoPointFactory);
            locationRepository.saveAndFlush(location);

            // Search at coordinates far away with small radius
            List<Location> results = locationRepository.findWithinRadius(0.0, 0.0, 10.0);

            assertThat(results).isEmpty();
        }
    }
}
