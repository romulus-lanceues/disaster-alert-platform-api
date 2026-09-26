package dev.romulus_lanceues.tanaw_api.alert.rule;

import dev.romulus_lanceues.tanaw_api.auth.JwtProperties;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAccessDeniedHandler;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAuthenticationEntryPoint;
import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.location.LocationNotFoundException;
import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserAuthState;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertRuleController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
})
@DisplayName("AlertRuleController Tests")
class AlertRuleControllerTest {

    private static final String BASE_URL = "/api/v1/alert-rules";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    @MockitoBean
    private AlertRuleService alertRuleService;

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

    private AlertRuleResponse buildResponse(UUID id, UUID locationId) {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        return new AlertRuleResponse(
                id,
                locationId,
                "Home",
                DisasterType.EARTHQUAKE,
                true,
                5.0,
                50.0,
                "MODERATE",
                now,
                now
        );
    }

    @Nested
    @DisplayName("createAlertRule (POST /api/v1/alert-rules)")
    class CreateAlertRule {

        @Test
        @DisplayName("should return 201 Created and Location header when payload is valid")
        void shouldReturn201CreatedAndLocationHeader_whenPayloadIsValid() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            AlertRuleRequest request = new AlertRuleRequest(
                    userId, locationId, DisasterType.EARTHQUAKE, 5.0, 50.0, "MODERATE");

            AlertRuleResponse response = buildResponse(ruleId, locationId);

            given(alertRuleService.createAlertRule(any(AlertRuleRequest.class))).willReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith(BASE_URL + "/" + ruleId)))
                    .andExpect(jsonPath("$.id", is(ruleId.toString())))
                    .andExpect(jsonPath("$.locationId", is(locationId.toString())))
                    .andExpect(jsonPath("$.locationName", is("Home")))
                    .andExpect(jsonPath("$.disasterType", is("EARTHQUAKE")))
                    .andExpect(jsonPath("$.enabled", is(true)))
                    .andExpect(jsonPath("$.minimumMagnitude", is(5.0)))
                    .andExpect(jsonPath("$.radiusKm", is(50.0)))
                    .andExpect(jsonPath("$.minimumSeverity", is("MODERATE")));

            then(alertRuleService).should().createAlertRule(request);
        }

        @Test
        @DisplayName("should return 400 Bad Request when required fields are missing")
        void shouldReturn400BadRequest_whenRequiredFieldsAreMissing() throws Exception {
            AlertRuleRequest invalidRequest = new AlertRuleRequest(
                    null, null, null, null, null, null);

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.userId").exists())
                    .andExpect(jsonPath("$.errors.locationId").exists())
                    .andExpect(jsonPath("$.errors.disasterType").exists());

            then(alertRuleService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 400 Bad Request when thresholds are negative")
        void shouldReturn400BadRequest_whenThresholdsAreNegative() throws Exception {
            AlertRuleRequest invalidRequest = new AlertRuleRequest(
                    UUID.randomUUID(), UUID.randomUUID(), DisasterType.EARTHQUAKE, -1.0, -5.0, null);

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.minimumMagnitude").exists())
                    .andExpect(jsonPath("$.errors.radiusKm").exists());

            then(alertRuleService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 404 Not Found when location does not exist")
        void shouldReturn404NotFound_whenLocationDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            AlertRuleRequest request = new AlertRuleRequest(
                    userId, locationId, DisasterType.EARTHQUAKE, 5.0, 50.0, "MODERATE");

            given(alertRuleService.createAlertRule(any(AlertRuleRequest.class)))
                    .willThrow(new LocationNotFoundException("Location not found: " + locationId));

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Location Not Found")))
                    .andExpect(jsonPath("$.detail", is("Location not found: " + locationId)));

            then(alertRuleService).should().createAlertRule(request);
        }
    }

    @Nested
    @DisplayName("getAlertRules by user (GET /api/v1/alert-rules?userId={userId})")
    class GetAlertRulesByUser {

        @Test
        @DisplayName("should return 200 OK and list of alert rules for a user")
        void shouldReturn200OkAndAlertRuleList_whenRulesExist() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId1 = UUID.randomUUID();
            UUID ruleId2 = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            List<AlertRuleResponse> rules = List.of(
                    buildResponse(ruleId1, locationId),
                    buildResponse(ruleId2, locationId));

            given(alertRuleService.getAlertRulesByUser(userId)).willReturn(rules);

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(ruleId1.toString())))
                    .andExpect(jsonPath("$[1].id", is(ruleId2.toString())));

            then(alertRuleService).should().getAlertRulesByUser(userId);
        }

        @Test
        @DisplayName("should return 200 OK and empty list when user has no alert rules")
        void shouldReturn200OkAndEmptyList_whenUserHasNoRules() throws Exception {
            UUID userId = UUID.randomUUID();

            given(alertRuleService.getAlertRulesByUser(userId)).willReturn(List.of());

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            then(alertRuleService).should().getAlertRulesByUser(userId);
        }
    }

    @Nested
    @DisplayName("getAlertRules by location (GET /api/v1/alert-rules?userId={userId}&locationId={locationId})")
    class GetAlertRulesByLocation {

        @Test
        @DisplayName("should return 200 OK and list of alert rules for a location owned by user")
        void shouldReturn200OkAndAlertRuleList_whenLocationBelongsToUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            List<AlertRuleResponse> rules = List.of(buildResponse(ruleId, locationId));

            given(alertRuleService.getAlertRulesByLocation(locationId, userId)).willReturn(rules);

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString())
                            .param("locationId", locationId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(ruleId.toString())))
                    .andExpect(jsonPath("$[0].locationId", is(locationId.toString())));

            then(alertRuleService).should().getAlertRulesByLocation(locationId, userId);
        }

        @Test
        @DisplayName("should return 200 OK and empty list when location has no rules")
        void shouldReturn200OkAndEmptyList_whenLocationHasNoRules() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            given(alertRuleService.getAlertRulesByLocation(locationId, userId)).willReturn(List.of());

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString())
                            .param("locationId", locationId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            then(alertRuleService).should().getAlertRulesByLocation(locationId, userId);
        }
    }

    @Nested
    @DisplayName("getAlertRule (GET /api/v1/alert-rules/{id}?userId={userId})")
    class GetAlertRule {

        @Test
        @DisplayName("should return 200 OK and alert rule response when it exists for user")
        void shouldReturn200OkAndAlertRuleResponse_whenExistsForUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            AlertRuleResponse response = buildResponse(ruleId, locationId);

            given(alertRuleService.getAlertRule(ruleId, userId)).willReturn(response);

            mockMvc.perform(get(BASE_URL + "/{id}", ruleId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(ruleId.toString())))
                    .andExpect(jsonPath("$.locationId", is(locationId.toString())))
                    .andExpect(jsonPath("$.disasterType", is("EARTHQUAKE")))
                    .andExpect(jsonPath("$.enabled", is(true)))
                    .andExpect(jsonPath("$.minimumMagnitude", is(5.0)))
                    .andExpect(jsonPath("$.radiusKm", is(50.0)))
                    .andExpect(jsonPath("$.minimumSeverity", is("MODERATE")));

            then(alertRuleService).should().getAlertRule(ruleId, userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when alert rule does not exist for user")
        void shouldReturn404NotFound_whenAlertRuleDoesNotExistForUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            given(alertRuleService.getAlertRule(ruleId, userId))
                    .willThrow(new AlertRuleNotFoundException("Alert rule not found: " + ruleId));

            mockMvc.perform(get(BASE_URL + "/{id}", ruleId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Alert Rule Not Found")))
                    .andExpect(jsonPath("$.detail", is("Alert rule not found: " + ruleId)));

            then(alertRuleService).should().getAlertRule(ruleId, userId);
        }
    }

    @Nested
    @DisplayName("updateThresholds (PATCH /api/v1/alert-rules/{id}/thresholds?userId={userId})")
    class UpdateThresholds {

        @Test
        @DisplayName("should return 200 OK and updated alert rule response")
        void shouldReturn200OkAndUpdatedResponse() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            AlertRuleThresholdRequest request = new AlertRuleThresholdRequest(7.0, 100.0, "SEVERE");

            Instant now = Instant.parse("2026-09-18T10:00:00Z");
            AlertRuleResponse response = new AlertRuleResponse(
                    ruleId, locationId, "Home", DisasterType.EARTHQUAKE, true,
                    7.0, 100.0, "SEVERE", now, now);

            given(alertRuleService.updateThresholds(ruleId, userId, 7.0, 100.0, "SEVERE"))
                    .willReturn(response);

            mockMvc.perform(patch(BASE_URL + "/{id}/thresholds", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(ruleId.toString())))
                    .andExpect(jsonPath("$.minimumMagnitude", is(7.0)))
                    .andExpect(jsonPath("$.radiusKm", is(100.0)))
                    .andExpect(jsonPath("$.minimumSeverity", is("SEVERE")));

            then(alertRuleService).should().updateThresholds(ruleId, userId, 7.0, 100.0, "SEVERE");
        }

        @Test
        @DisplayName("should return 200 OK when clearing thresholds with null values")
        void shouldReturn200Ok_whenClearingThresholdsWithNullValues() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            AlertRuleThresholdRequest request = new AlertRuleThresholdRequest(null, null, null);

            Instant now = Instant.parse("2026-09-18T10:00:00Z");
            AlertRuleResponse response = new AlertRuleResponse(
                    ruleId, locationId, "Home", DisasterType.EARTHQUAKE, true,
                    null, null, null, now, now);

            given(alertRuleService.updateThresholds(ruleId, userId, null, null, null))
                    .willReturn(response);

            mockMvc.perform(patch(BASE_URL + "/{id}/thresholds", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.minimumMagnitude").doesNotExist())
                    .andExpect(jsonPath("$.radiusKm").doesNotExist())
                    .andExpect(jsonPath("$.minimumSeverity").doesNotExist());

            then(alertRuleService).should().updateThresholds(ruleId, userId, null, null, null);
        }

        @Test
        @DisplayName("should return 400 Bad Request when threshold values are negative")
        void shouldReturn400BadRequest_whenThresholdValuesAreNegative() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            AlertRuleThresholdRequest request = new AlertRuleThresholdRequest(-1.0, -5.0, null);

            mockMvc.perform(patch(BASE_URL + "/{id}/thresholds", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.minimumMagnitude").exists())
                    .andExpect(jsonPath("$.errors.radiusKm").exists());

            then(alertRuleService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should return 404 Not Found when alert rule does not exist for user")
        void shouldReturn404NotFound_whenAlertRuleDoesNotExistForUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            AlertRuleThresholdRequest request = new AlertRuleThresholdRequest(7.0, 100.0, "SEVERE");

            given(alertRuleService.updateThresholds(ruleId, userId, 7.0, 100.0, "SEVERE"))
                    .willThrow(new AlertRuleNotFoundException("Alert rule not found: " + ruleId));

            mockMvc.perform(patch(BASE_URL + "/{id}/thresholds", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Alert Rule Not Found")))
                    .andExpect(jsonPath("$.detail", is("Alert rule not found: " + ruleId)));

            then(alertRuleService).should().updateThresholds(ruleId, userId, 7.0, 100.0, "SEVERE");
        }
    }

    @Nested
    @DisplayName("enableAlertRule (PATCH /api/v1/alert-rules/{id}/enable?userId={userId})")
    class EnableAlertRule {

        @Test
        @DisplayName("should return 200 OK and enabled alert rule response")
        void shouldReturn200OkAndEnabledResponse() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            AlertRuleResponse response = buildResponse(ruleId, locationId);

            given(alertRuleService.enableAlertRule(ruleId, userId)).willReturn(response);

            mockMvc.perform(patch(BASE_URL + "/{id}/enable", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(ruleId.toString())))
                    .andExpect(jsonPath("$.enabled", is(true)));

            then(alertRuleService).should().enableAlertRule(ruleId, userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when alert rule does not exist for user")
        void shouldReturn404NotFound_whenAlertRuleDoesNotExistForUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            given(alertRuleService.enableAlertRule(ruleId, userId))
                    .willThrow(new AlertRuleNotFoundException("Alert rule not found: " + ruleId));

            mockMvc.perform(patch(BASE_URL + "/{id}/enable", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Alert Rule Not Found")))
                    .andExpect(jsonPath("$.detail", is("Alert rule not found: " + ruleId)));

            then(alertRuleService).should().enableAlertRule(ruleId, userId);
        }
    }

    @Nested
    @DisplayName("disableAlertRule (PATCH /api/v1/alert-rules/{id}/disable?userId={userId})")
    class DisableAlertRule {

        @Test
        @DisplayName("should return 200 OK and disabled alert rule response")
        void shouldReturn200OkAndDisabledResponse() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            Instant now = Instant.parse("2026-09-18T10:00:00Z");
            AlertRuleResponse response = new AlertRuleResponse(
                    ruleId, locationId, "Home", DisasterType.EARTHQUAKE, false,
                    5.0, 50.0, "MODERATE", now, now);

            given(alertRuleService.disableAlertRule(ruleId, userId)).willReturn(response);

            mockMvc.perform(patch(BASE_URL + "/{id}/disable", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(ruleId.toString())))
                    .andExpect(jsonPath("$.enabled", is(false)));

            then(alertRuleService).should().disableAlertRule(ruleId, userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when alert rule does not exist for user")
        void shouldReturn404NotFound_whenAlertRuleDoesNotExistForUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            given(alertRuleService.disableAlertRule(ruleId, userId))
                    .willThrow(new AlertRuleNotFoundException("Alert rule not found: " + ruleId));

            mockMvc.perform(patch(BASE_URL + "/{id}/disable", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Alert Rule Not Found")))
                    .andExpect(jsonPath("$.detail", is("Alert rule not found: " + ruleId)));

            then(alertRuleService).should().disableAlertRule(ruleId, userId);
        }
    }

    @Nested
    @DisplayName("deleteAlertRule (DELETE /api/v1/alert-rules/{id}?userId={userId})")
    class DeleteAlertRule {

        @Test
        @DisplayName("should return 204 No Content when alert rule is deleted")
        void shouldReturn204NoContent_whenAlertRuleIsDeleted() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            doNothing().when(alertRuleService).deleteAlertRule(ruleId, userId);

            mockMvc.perform(delete(BASE_URL + "/{id}", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isNoContent());

            then(alertRuleService).should().deleteAlertRule(ruleId, userId);
        }

        @Test
        @DisplayName("should return 404 Not Found when alert rule does not exist for user")
        void shouldReturn404NotFound_whenAlertRuleDoesNotExistForUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            doThrow(new AlertRuleNotFoundException("Alert rule not found: " + ruleId))
                    .when(alertRuleService).deleteAlertRule(ruleId, userId);

            mockMvc.perform(delete(BASE_URL + "/{id}", ruleId)
                            .with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .param("userId", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Alert Rule Not Found")))
                    .andExpect(jsonPath("$.detail", is("Alert rule not found: " + ruleId)));

            then(alertRuleService).should().deleteAlertRule(ruleId, userId);
        }
    }
}
