package dev.romulus_lanceues.tanaw_api.ops;

import dev.romulus_lanceues.tanaw_api.auth.CsrfController;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAccessDeniedHandler;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAuthenticationEntryPoint;
import dev.romulus_lanceues.tanaw_api.ops.dto.SimulatedEarthquakeResponse;
import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Ops Security Filter Chain Tests")
class OpsSecurityFilterChainTest {

    private static final String OPS_URL = "/api/ops/simulations/earthquake";
    private static final String VALID_KEY = "test-secret-at-least-16-chars";
    private static final String VALID_PAYLOAD = """
            {
                "alertRuleId": "11111111-1111-1111-1111-111111111111",
                "magnitude": 6.0,
                "depthKm": 10.0
            }
            """;

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @WebMvcTest(controllers = {CsrfController.class, OpsSimulationController.class})
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
            "tanaw.ops.simulation-key=" + VALID_KEY
    })
    @DisplayName("When ops simulation is enabled")
    class OpsEnabledTests {

        private final MockMvc mockMvc;

        @MockitoBean
        private UserRepository userRepository;

        @MockitoBean
        private OpsSimulationService opsSimulationService;

        @Autowired
        OpsEnabledTests(MockMvc mockMvc) {
            this.mockMvc = mockMvc;
        }

        @Test
        @DisplayName("POST /api/ops/simulations/earthquake returns 401 ProblemDetail when X-Ops-Key is missing")
        void opsEndpoint_returns401WhenHeaderMissing() throws Exception {
            mockMvc.perform(post(OPS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PAYLOAD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")))
                    .andExpect(jsonPath("$.detail", is("Missing or empty X-Ops-Key header")))
                    .andExpect(jsonPath("$.instance", is(OPS_URL)));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("POST /api/ops/simulations/earthquake returns 401 ProblemDetail when X-Ops-Key is invalid")
        void opsEndpoint_returns401WhenHeaderInvalid() throws Exception {
            mockMvc.perform(post(OPS_URL)
                            .header("X-Ops-Key", "invalid-key-value-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PAYLOAD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")))
                    .andExpect(jsonPath("$.detail", is("Invalid X-Ops-Key header")))
                    .andExpect(jsonPath("$.instance", is(OPS_URL)));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("POST /api/ops/simulations/earthquake proceeds past security filter chain when X-Ops-Key is valid")
        void opsEndpoint_proceedsPastSecurityFilterChainWhenKeyIsValid() throws Exception {
            UUID eventId = UUID.randomUUID();
            UUID alertId = UUID.randomUUID();
            SimulatedEarthquakeResponse response = new SimulatedEarthquakeResponse(
                    eventId,
                    "sim-eq-" + eventId,
                    14.5995,
                    120.9842,
                    6.0,
                    5.0,
                    true,
                    alertId,
                    "Manila Bay",
                    "Simulated earthquake processed"
            );

            given(opsSimulationService.simulateEarthquake(any())).willReturn(response);

            mockMvc.perform(post(OPS_URL)
                            .header("X-Ops-Key", VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PAYLOAD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matched", is(true)))
                    .andExpect(jsonPath("$.locationName", is("Manila Bay")));

            then(opsSimulationService).should().simulateEarthquake(any());
        }

        @Test
        @DisplayName("DELETE /api/ops/simulations/{id} proceeds past security filter chain when X-Ops-Key is valid")
        void cleanup_proceedsPastSecurityFilterChainWhenKeyIsValid() throws Exception {
            UUID disasterEventId = UUID.randomUUID();

            mockMvc.perform(delete("/api/ops/simulations/{id}", disasterEventId)
                            .header("X-Ops-Key", VALID_KEY))
                    .andExpect(status().isNoContent());

            then(opsSimulationService).should().cleanup(disasterEventId);
        }

        @Test
        @DisplayName("POST /api/dev/simulate-earthquake is denied by defaultSecurityFilterChain")
        void devEndpoint_isDeniedByDefaultSecurityFilterChain() throws Exception {
            mockMvc.perform(post("/api/dev/simulate-earthquake"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/csrf returns 200 OK and sets XSRF-TOKEN cookie without filter collision")
        void getCsrf_returns200() throws Exception {
            mockMvc.perform(get("/api/csrf"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("XSRF-TOKEN"));
        }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @WebMvcTest(controllers = {CsrfController.class, OpsSimulationController.class})
    @EnableConfigurationProperties(OpsSimulationProperties.class)
    @Import({
            SecurityConfig.class,
            GlobalExceptionHandler.class,
            ProblemDetailsAuthenticationEntryPoint.class,
            ProblemDetailsAccessDeniedHandler.class
    })
    @TestPropertySource(properties = {
            "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng==",
            "tanaw.ops.enabled=false"
    })
    @DisplayName("When ops simulation is disabled")
    class OpsDisabledTests {

        private final MockMvc mockMvc;

        @MockitoBean
        private UserRepository userRepository;

        @MockitoBean
        private OpsSimulationService opsSimulationService;

        @Autowired
        OpsDisabledTests(MockMvc mockMvc) {
            this.mockMvc = mockMvc;
        }

        @Test
        @DisplayName("POST /api/ops/simulations/earthquake returns 404 ProblemDetail when ops simulations are disabled")
        void opsEndpoint_returns404ProblemDetailWhenDisabled() throws Exception {
            mockMvc.perform(post(OPS_URL)
                            .header("X-Ops-Key", VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PAYLOAD))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.title", is("Not Found")))
                    .andExpect(jsonPath("$.detail", is("Ops simulation endpoints are disabled")))
                    .andExpect(jsonPath("$.instance", is(OPS_URL)));

            then(opsSimulationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("POST /api/dev/simulate-earthquake is denied by defaultSecurityFilterChain when ops is disabled")
        void devEndpoint_isDeniedByDefaultSecurityFilterChain() throws Exception {
            mockMvc.perform(post("/api/dev/simulate-earthquake"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
