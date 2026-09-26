package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.auth.JwtProperties;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAccessDeniedHandler;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAuthenticationEntryPoint;
import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.geo.area.GeoAreaSummary;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaNotFoundException;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserAuthState;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
})
@DisplayName("LocationController Tests")
class LocationControllerTest {

    private static final String BASE_URL = "/api/v1/locations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean
    private UserRepository userRepository;

    private String validToken;

    @BeforeEach
    void setUp() {
        TestJwtFactory jwtFactory = new TestJwtFactory(jwtProperties, clock);
        validToken = jwtFactory.createValidToken();

        given(userRepository.findAuthState(any(UUID.class)))
                .willReturn(Optional.of(new UserAuthState(UserStatus.ACTIVE, 0L)));
    }

    @Nested
    @DisplayName("createLocation (POST /api/v1/locations)")
    class CreateLocation {

        @Test
        @DisplayName("should return 201 Created and Location header when payload is valid")
        void shouldReturn201CreatedAndLocationHeader_whenPayloadIsValid() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            UUID geoAreaId = UUID.randomUUID();
            Instant now = Instant.parse("2026-09-18T10:00:00Z");

            LocationRequest request = new LocationRequest(
                    userId,
                    "Home",
                    "123 Rizal St",
                    "137600000",
                    14.60,
                    120.98
            );

            GeoAreaSummary geoAreaSummary = new GeoAreaSummary(
                    geoAreaId,
                    "137600000",
                    "Manila",
                    GeographicAreaType.MUNICIPALITY
            );

            LocationResponse response = new LocationResponse(
                    locationId,
                    userId,
                    request.name(),
                    request.address(),
                    request.latitude(),
                    request.longitude(),
                    geoAreaSummary,
                    now,
                    now
            );

            given(locationService.createLocation(any(LocationRequest.class))).willReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith(BASE_URL + "/" + locationId)))
                    .andExpect(jsonPath("$.id", is(locationId.toString())))
                    .andExpect(jsonPath("$.userId", is(userId.toString())))
                    .andExpect(jsonPath("$.name", is("Home")))
                    .andExpect(jsonPath("$.address", is("123 Rizal St")))
                    .andExpect(jsonPath("$.latitude", is(14.60)))
                    .andExpect(jsonPath("$.longitude", is(120.98)))
                    .andExpect(jsonPath("$.geographicArea.id", is(geoAreaId.toString())))
                    .andExpect(jsonPath("$.geographicArea.psgcCode", is("137600000")))
                    .andExpect(jsonPath("$.geographicArea.name", is("Manila")))
                    .andExpect(jsonPath("$.geographicArea.type", is("MUNICIPALITY")))
                    .andExpect(jsonPath("$.createdAt", is(now.toString())))
                    .andExpect(jsonPath("$.updatedAt", is(now.toString())));

            then(locationService).should().createLocation(request);
        }

        @Test
        @DisplayName("should return 400 Bad Request when validation fails")
        void shouldReturn400BadRequest_whenValidationFails() throws Exception {
            LocationRequest invalidRequest = new LocationRequest(
                    null,
                    "",
                    "x".repeat(256),
                    "",
                    95.0,
                    190.0
            );

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.userId").exists())
                    .andExpect(jsonPath("$.errors.name").exists())
                    .andExpect(jsonPath("$.errors.address").exists())
                    .andExpect(jsonPath("$.errors.geographicAreaCode").exists())
                    .andExpect(jsonPath("$.errors.latitude").exists())
                    .andExpect(jsonPath("$.errors.longitude").exists());

            then(locationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 404 Not Found when user does not exist")
        void shouldReturn404NotFound_whenUserDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();
            LocationRequest request = new LocationRequest(
                    userId,
                    "Home",
                    "123 Rizal St",
                    "137600000",
                    14.60,
                    120.98
            );

            given(locationService.createLocation(any(LocationRequest.class)))
                    .willThrow(new UserNotFoundException("User not found: " + userId));

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("User not found: " + userId)));

            then(locationService).should().createLocation(request);
        }

        @Test
        @DisplayName("should return 404 Not Found when geographic area does not exist")
        void shouldReturn404NotFound_whenGeographicAreaDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();
            LocationRequest request = new LocationRequest(
                    userId,
                    "Home",
                    "123 Rizal St",
                    "999999999",
                    14.60,
                    120.98
            );

            given(locationService.createLocation(any(LocationRequest.class)))
                    .willThrow(new GeographicAreaNotFoundException("Geographic area not found with PSGC code: 999999999"));

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Geographic Area Not Found")))
                    .andExpect(jsonPath("$.detail", is("Geographic area not found with PSGC code: 999999999")));

            then(locationService).should().createLocation(request);
        }
    }

    @Nested
    @DisplayName("getLocationsByUser (GET /api/v1/locations?userId={userId})")
    class GetLocationsByUser {

        @Test
        @DisplayName("should return 200 OK and list of locations when user has locations")
        void shouldReturn200OkAndLocationList_whenLocationsExist() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID locId1 = UUID.randomUUID();
            UUID locId2 = UUID.randomUUID();
            Instant now = Instant.parse("2026-09-18T10:00:00Z");

            GeoAreaSummary geoAreaSummary = new GeoAreaSummary(
                    UUID.randomUUID(),
                    "137600000",
                    "Manila",
                    GeographicAreaType.MUNICIPALITY
            );

            LocationResponse loc1 = new LocationResponse(
                    locId1,
                    userId,
                    "Home",
                    "123 Rizal St",
                    14.60,
                    120.98,
                    geoAreaSummary,
                    now,
                    now
            );
            LocationResponse loc2 = new LocationResponse(
                    locId2,
                    userId,
                    "Office",
                    "456 Ayala Ave",
                    14.55,
                    121.02,
                    geoAreaSummary,
                    now,
                    now
            );

            given(locationService.getLocationsByUser(userId)).willReturn(List.of(loc1, loc2));

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(locId1.toString())))
                    .andExpect(jsonPath("$[0].userId", is(userId.toString())))
                    .andExpect(jsonPath("$[0].name", is("Home")))
                    .andExpect(jsonPath("$[0].address", is("123 Rizal St")))
                    .andExpect(jsonPath("$[0].latitude", is(14.60)))
                    .andExpect(jsonPath("$[0].longitude", is(120.98)))
                    .andExpect(jsonPath("$[0].geographicArea.psgcCode", is("137600000")))
                    .andExpect(jsonPath("$[0].createdAt", is(now.toString())))
                    .andExpect(jsonPath("$[0].updatedAt", is(now.toString())))
                    .andExpect(jsonPath("$[1].id", is(locId2.toString())))
                    .andExpect(jsonPath("$[1].userId", is(userId.toString())))
                    .andExpect(jsonPath("$[1].name", is("Office")))
                    .andExpect(jsonPath("$[1].address", is("456 Ayala Ave")))
                    .andExpect(jsonPath("$[1].latitude", is(14.55)))
                    .andExpect(jsonPath("$[1].longitude", is(121.02)))
                    .andExpect(jsonPath("$[1].geographicArea.psgcCode", is("137600000")))
                    .andExpect(jsonPath("$[1].createdAt", is(now.toString())))
                    .andExpect(jsonPath("$[1].updatedAt", is(now.toString())));

            then(locationService).should().getLocationsByUser(userId);
        }

        @Test
        @DisplayName("should return 200 OK and empty list when user has no locations")
        void shouldReturn200OkAndEmptyList_whenUserHasNoLocations() throws Exception {
            UUID userId = UUID.randomUUID();

            given(locationService.getLocationsByUser(userId)).willReturn(List.of());

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            then(locationService).should().getLocationsByUser(userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when user does not exist")
        void shouldReturn404NotFound_whenUserDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();

            given(locationService.getLocationsByUser(userId))
                    .willThrow(new UserNotFoundException("User not found: " + userId));

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("User Not Found")))
                    .andExpect(jsonPath("$.detail", is("User not found: " + userId)));

            then(locationService).should().getLocationsByUser(userId);
        }
    }

    @Nested
    @DisplayName("getLocation (GET /api/v1/locations/{id}?userId={userId})")
    class GetLocation {

        @Test
        @DisplayName("should return 200 OK and location details when location exists and belongs to user")
        void shouldReturn200OkAndLocationResponse_whenLocationExistsAndOwnedByUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            UUID geoAreaId = UUID.randomUUID();
            Instant now = Instant.parse("2026-09-18T10:00:00Z");

            GeoAreaSummary geoAreaSummary = new GeoAreaSummary(
                    geoAreaId,
                    "137600000",
                    "Manila",
                    GeographicAreaType.MUNICIPALITY
            );

            LocationResponse response = new LocationResponse(
                    locationId,
                    userId,
                    "Home",
                    "123 Rizal St",
                    14.60,
                    120.98,
                    geoAreaSummary,
                    now,
                    now
            );

            given(locationService.getLocation(locationId, userId)).willReturn(response);

            mockMvc.perform(get(BASE_URL + "/{id}", locationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(locationId.toString())))
                    .andExpect(jsonPath("$.userId", is(userId.toString())))
                    .andExpect(jsonPath("$.name", is("Home")))
                    .andExpect(jsonPath("$.address", is("123 Rizal St")))
                    .andExpect(jsonPath("$.latitude", is(14.60)))
                    .andExpect(jsonPath("$.longitude", is(120.98)))
                    .andExpect(jsonPath("$.geographicArea.id", is(geoAreaId.toString())))
                    .andExpect(jsonPath("$.geographicArea.psgcCode", is("137600000")))
                    .andExpect(jsonPath("$.geographicArea.name", is("Manila")))
                    .andExpect(jsonPath("$.geographicArea.type", is("MUNICIPALITY")))
                    .andExpect(jsonPath("$.createdAt", is(now.toString())))
                    .andExpect(jsonPath("$.updatedAt", is(now.toString())));

            then(locationService).should().getLocation(locationId, userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when location does not exist or does not belong to user")
        void shouldReturn404NotFound_whenLocationDoesNotExistOrNotOwnedByUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            given(locationService.getLocation(locationId, userId))
                    .willThrow(new LocationNotFoundException("Location not found: " + locationId));

            mockMvc.perform(get(BASE_URL + "/{id}", locationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Location Not Found")))
                    .andExpect(jsonPath("$.detail", is("Location not found: " + locationId)));

            then(locationService).should().getLocation(locationId, userId);
        }
    }
}
