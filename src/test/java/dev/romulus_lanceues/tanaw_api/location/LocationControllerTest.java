package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.config.TestSecurityConfig;
import dev.romulus_lanceues.tanaw_api.geo.area.GeoAreaSummary;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaNotFoundException;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("LocationController Tests")
class LocationControllerTest {

    private static final String BASE_URL = "/api/v1/locations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LocationService locationService;

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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Geographic Area Not Found")))
                    .andExpect(jsonPath("$.detail", is("Geographic area not found with PSGC code: 999999999")));

            then(locationService).should().createLocation(request);
        }
    }
}
