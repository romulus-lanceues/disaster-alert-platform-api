package dev.romulus_lanceues.tanaw_api.ops;

import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleNotFoundException;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAccessDeniedHandler;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAuthenticationEntryPoint;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventNotFoundException;
import dev.romulus_lanceues.tanaw_api.ops.dto.SimulateEarthquakeRequest;
import dev.romulus_lanceues.tanaw_api.ops.dto.SimulatedEarthquakeResponse;
import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpsSimulationController.class)
@EnableConfigurationProperties(OpsSimulationProperties.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng==",
        "tanaw.ops.enabled=true",
        "tanaw.ops.simulation-key=test-secret-at-least-16-chars"
})
@DisplayName("OpsSimulationController Tests")
class OpsSimulationControllerTest {

    private static final String BASE_URL = "/api/ops/simulations";
    private static final String OPS_KEY_HEADER = "X-Ops-Key";
    private static final String VALID_KEY = "test-secret-at-least-16-chars";
    private static final String INVALID_KEY = "wrong-ops-simulation-key";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private OpsSimulationService opsSimulationService;

    @Autowired
    OpsSimulationControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Nested
    @DisplayName("POST /api/ops/simulations/earthquake")
    class SimulateEarthquakeTests {

        @Test
        @DisplayName("returns 401 Unauthorized ProblemDetail when X-Ops-Key header is missing")
        void simulateEarthquake_returns401WhenHeaderMissing() throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.0, 5.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Missing or empty X-Ops-Key header")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("returns 401 Unauthorized ProblemDetail when X-Ops-Key header is blank or empty")
        void simulateEarthquake_returns401WhenHeaderBlank(String blankKey) throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.0, 5.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, blankKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Missing or empty X-Ops-Key header")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 401 Unauthorized ProblemDetail when X-Ops-Key header does not match configured key")
        void simulateEarthquake_returns401WhenKeyInvalid() throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.0, 5.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, INVALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Invalid X-Ops-Key header")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 200 OK and response payload when valid X-Ops-Key header is provided")
        void simulateEarthquake_returns200WhenKeyValid() throws Exception {
            UUID ruleId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID alertId = UUID.randomUUID();

            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.2, 5.0);
            SimulatedEarthquakeResponse response = new SimulatedEarthquakeResponse(
                    eventId,
                    "sim-eq-" + eventId,
                    14.5995,
                    120.9842,
                    6.2,
                    5.0,
                    true,
                    alertId,
                    "Manila Bay",
                    "Simulated earthquake created and alert triggered for rule " + ruleId
            );

            given(opsSimulationService.simulateEarthquake(request)).willReturn(response);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disasterEventId", is(eventId.toString())))
                    .andExpect(jsonPath("$.externalId", is("sim-eq-" + eventId)))
                    .andExpect(jsonPath("$.latitude", is(14.5995)))
                    .andExpect(jsonPath("$.longitude", is(120.9842)))
                    .andExpect(jsonPath("$.magnitude", is(6.2)))
                    .andExpect(jsonPath("$.distanceKm", is(5.0)))
                    .andExpect(jsonPath("$.matched", is(true)))
                    .andExpect(jsonPath("$.alertId", is(alertId.toString())))
                    .andExpect(jsonPath("$.locationName", is("Manila Bay")))
                    .andExpect(jsonPath("$.message", is("Simulated earthquake created and alert triggered for rule " + ruleId)));

            then(opsSimulationService).should().simulateEarthquake(request);
        }

        @Test
        @DisplayName("returns 400 Bad Request ProblemDetail when alertRuleId is null")
        void simulateEarthquake_returns400WhenAlertRuleIdNull() throws Exception {
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(null, 6.0, 5.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.title", is("Validation Failed")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 400 Bad Request ProblemDetail when magnitude is negative")
        void simulateEarthquake_returns400WhenMagnitudeNegative() throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, -0.1, 5.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.title", is("Validation Failed")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 400 Bad Request ProblemDetail when magnitude exceeds 10.0")
        void simulateEarthquake_returns400WhenMagnitudeExceedsMax() throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 10.1, 5.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.title", is("Validation Failed")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 400 Bad Request ProblemDetail when offsetKm is negative")
        void simulateEarthquake_returns400WhenOffsetKmNegative() throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.0, -1.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.title", is("Validation Failed")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 400 Bad Request ProblemDetail when offsetKm exceeds 1000.0")
        void simulateEarthquake_returns400WhenOffsetKmExceedsMax() throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.0, 1001.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.title", is("Validation Failed")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 404 Not Found ProblemDetail when alert rule is not found")
        void simulateEarthquake_returns404WhenRuleNotFound() throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.0, 5.0);

            given(opsSimulationService.simulateEarthquake(request))
                    .willThrow(new AlertRuleNotFoundException("Alert rule not found: " + ruleId));

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.title", is("Alert Rule Not Found")))
                    .andExpect(jsonPath("$.detail", is("Alert rule not found: " + ruleId)));

            then(opsSimulationService).should().simulateEarthquake(request);
        }
    }

    @Nested
    @DisplayName("DELETE /api/ops/simulations/{disasterEventId}")
    class CleanupSimulationTests {

        @Test
        @DisplayName("returns 401 Unauthorized ProblemDetail when X-Ops-Key header is missing on DELETE")
        void cleanup_returns401WhenHeaderMissing() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(delete(BASE_URL + "/" + eventId))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Missing or empty X-Ops-Key header")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 401 Unauthorized ProblemDetail when X-Ops-Key header is invalid on DELETE")
        void cleanup_returns401WhenKeyInvalid() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(delete(BASE_URL + "/" + eventId)
                            .header(OPS_KEY_HEADER, INVALID_KEY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Invalid X-Ops-Key header")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns 204 No Content when valid X-Ops-Key header is provided on DELETE")
        void cleanup_returns204WhenKeyValid() throws Exception {
            UUID eventId = UUID.randomUUID();
            doNothing().when(opsSimulationService).cleanup(eventId);

            mockMvc.perform(delete(BASE_URL + "/" + eventId)
                            .header(OPS_KEY_HEADER, VALID_KEY))
                    .andExpect(status().isNoContent());

            then(opsSimulationService).should().cleanup(eventId);
        }

        @Test
        @DisplayName("returns 404 Not Found ProblemDetail when disaster event is not found on DELETE")
        void cleanup_returns404WhenEventNotFound() throws Exception {
            UUID eventId = UUID.randomUUID();
            doThrow(new DisasterEventNotFoundException("Simulated disaster event not found: " + eventId))
                    .when(opsSimulationService).cleanup(eventId);

            mockMvc.perform(delete(BASE_URL + "/" + eventId)
                            .header(OPS_KEY_HEADER, VALID_KEY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.title", is("Disaster Event Not Found")))
                    .andExpect(jsonPath("$.detail", is("Simulated disaster event not found: " + eventId)));

            then(opsSimulationService).should().cleanup(eventId);
        }
    }

    @Nested
    @TestPropertySource(properties = {
            "tanaw.ops.enabled=false",
            "tanaw.ops.simulation-key="
    })
    @DisplayName("When ops simulation feature is disabled")
    class DisabledFeatureTests {

        @Test
        @DisplayName("POST /earthquake returns 404 Not Found even with valid X-Ops-Key header when disabled")
        void postEarthquake_returns404WhenDisabled() throws Exception {
            UUID ruleId = UUID.randomUUID();
            SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.0, 5.0);

            mockMvc.perform(post(BASE_URL + "/earthquake")
                            .header(OPS_KEY_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.detail", is("Ops simulation endpoints are disabled")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("DELETE /{id} returns 404 Not Found even with valid X-Ops-Key header when disabled")
        void deleteSimulation_returns404WhenDisabled() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(delete(BASE_URL + "/" + eventId)
                            .header(OPS_KEY_HEADER, VALID_KEY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.detail", is("Ops simulation endpoints are disabled")));

            then(opsSimulationService).shouldHaveNoInteractions();
        }
    }
}
