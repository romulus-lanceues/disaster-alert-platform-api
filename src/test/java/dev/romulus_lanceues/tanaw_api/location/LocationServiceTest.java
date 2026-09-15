package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.jts.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationService Tests")
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeographicAreaRepository geographicAreaRepository;

    @Mock
    private GeoPointFactory geoPointFactory;

    @InjectMocks
    private LocationService locationService;


    private User buildUser(UUID id) {
        return User.builder()
                .id(id)
                .email("alice@example.com")
                .passwordHash("hash_abc")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private GeographicArea buildArea() {
        return GeographicArea.builder()
                .id(UUID.randomUUID())
                .psgcCode("137600000")
                .name("Manila")
                .type(GeographicAreaType.MUNICIPALITY)
                .active(true)
                .build();
    }

    private Point buildPoint() {
        return new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(120.98, 14.60));
    }

    private Location buildLocation(UUID id, User user, GeographicArea area) {
        return Location.builder()
                .id(id)
                .user(user)
                .name("Home")
                .address("123 Rizal St")
                .geographicArea(area)
                .latitude(14.60)
                .longitude(120.98)
                .location(buildPoint())
                .build();
    }


    @Nested
    @DisplayName("createLocation")
    class CreateLocation {

        @Test
        @DisplayName("should create and return location when user and geographic area exist")
        void shouldCreateAndReturnLocation_whenUserAndGeographicAreaExist() {

            UUID userId = UUID.randomUUID();
            User user = buildUser(userId);
            GeographicArea area = buildArea();
            Point point = buildPoint();

            LocationRequest request = new LocationRequest(
                    userId, "Home", "123 Rizal St", "137600000", 14.60, 120.98);

            Location savedLocation = buildLocation(UUID.randomUUID(), user, area);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(geographicAreaRepository.findByPsgcCode("137600000")).willReturn(Optional.of(area));
            given(geoPointFactory.create(14.60, 120.98)).willReturn(point);
            given(locationRepository.save(any(Location.class))).willReturn(savedLocation);

            Location result = locationService.createLocation(request);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Home");
            assertThat(result.getUser().getId()).isEqualTo(userId);
            assertThat(result.getGeographicArea().getPsgcCode()).isEqualTo("137600000");
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user does not exist")
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {

            UUID userId = UUID.randomUUID();
            LocationRequest request = new LocationRequest(
                    userId, "Home", "123 Rizal St", "137600000", 14.60, 120.98);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.createLocation(request))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(userId.toString());

            then(locationRepository).should(never()).save(any(Location.class));
        }

        @Test
        @DisplayName("should throw GeographicAreaNotFoundException when PSGC code is not found")
        void shouldThrowGeographicAreaNotFoundException_whenPsgcCodeNotFound() {

            UUID userId = UUID.randomUUID();
            User user = buildUser(userId);
            LocationRequest request = new LocationRequest(
                    userId, "Home", "123 Rizal St", "INVALID_CODE", 14.60, 120.98);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(geographicAreaRepository.findByPsgcCode("INVALID_CODE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.createLocation(request))
                    .isInstanceOf(GeographicAreaNotFoundException.class)
                    .hasMessageContaining("INVALID_CODE");

            then(locationRepository).should(never()).save(any(Location.class));
        }
    }


    @Nested
    @DisplayName("getLocationsByUser")
    class GetLocationsByUser {

        @Test
        @DisplayName("should return all locations when user exists")
        void shouldReturnAllLocations_whenUserExists() {

            UUID userId = UUID.randomUUID();
            User user = buildUser(userId);
            GeographicArea area = buildArea();

            List<Location> locations = List.of(
                    buildLocation(UUID.randomUUID(), user, area),
                    buildLocation(UUID.randomUUID(), user, area)
            );

            given(userRepository.existsById(userId)).willReturn(true);
            given(locationRepository.findByUserId(userId)).willReturn(locations);

            List<Location> result = locationService.getLocationsByUser(userId);

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(loc ->
                    assertThat(loc.getUser().getId()).isEqualTo(userId));
        }

        @Test
        @DisplayName("should return empty list when user has no locations")
        void shouldReturnEmptyList_whenUserHasNoLocations() {

            UUID userId = UUID.randomUUID();

            given(userRepository.existsById(userId)).willReturn(true);
            given(locationRepository.findByUserId(userId)).willReturn(List.of());

            List<Location> result = locationService.getLocationsByUser(userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw UserNotFoundException when user does not exist")
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {

            UUID userId = UUID.randomUUID();

            given(userRepository.existsById(userId)).willReturn(false);

            assertThatThrownBy(() -> locationService.getLocationsByUser(userId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(userId.toString());

            then(locationRepository).should(never()).findByUserId(any());
        }
    }


    @Nested
    @DisplayName("getLocation")
    class GetLocation {

        @Test
        @DisplayName("should return location when it belongs to the user")
        void shouldReturnLocation_whenItBelongsToUser() {

            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            User user = buildUser(userId);
            GeographicArea area = buildArea();
            Location location = buildLocation(locationId, user, area);

            given(locationRepository.findByIdAndUserId(locationId, userId))
                    .willReturn(Optional.of(location));

            Location result = locationService.getLocation(locationId, userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(locationId);
            assertThat(result.getUser().getId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should throw LocationNotFoundException when location does not exist for user")
        void shouldThrowLocationNotFoundException_whenLocationDoesNotBelongToUser() {

            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            given(locationRepository.findByIdAndUserId(locationId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.getLocation(locationId, userId))
                    .isInstanceOf(LocationNotFoundException.class)
                    .hasMessageContaining(locationId.toString());
        }
    }
}
