package dev.romulus_lanceues.tanaw_api.shared.config;

import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.jwt.secret=" + TestJwtFactory.DEFAULT_SECRET,
        "notification.crypto.key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Deployment Endpoints Integration Tests")
class DeploymentEndpointsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    private final MockMvc mockMvc;

    @Autowired
    DeploymentEndpointsIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Nested
    @DisplayName("Actuator Probes and Endpoints")
    class ActuatorEndpoints {

        @Test
        @DisplayName("GET /actuator/health is accessible without a token and returns 200 with status UP")
        void health_isAccessibleWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("UP")));
        }

        @Test
        @DisplayName("GET /actuator/health/readiness is accessible without a token and returns 200 with status UP")
        void healthReadiness_isAccessibleWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("UP")));
        }

        @Test
        @DisplayName("GET /actuator/health/liveness is accessible without a token and returns 200 with status UP")
        void healthLiveness_isAccessibleWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("UP")));
        }

        @Test
        @DisplayName("GET /actuator/info is accessible without a token and returns 200")
        void info_isAccessibleWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /actuator/env is not exposed and denied without a token returning 401 Unauthorized ProblemDetail")
        void env_isDeniedWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/env"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }
    }

    @Nested
    @DisplayName("CORS Preflight Requests")
    class CorsPreflight {

        @Test
        @DisplayName("OPTIONS /api/v1/auth/login from allowed origin http://localhost:3000 succeeds with 200 and CORS headers")
        void preflight_allowedOrigin3000_succeeds() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-XSRF-TOKEN"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"));
        }

        @Test
        @DisplayName("OPTIONS /api/v1/auth/login from allowed origin http://localhost:5173 succeeds with 200 and CORS headers")
        void preflight_allowedOrigin5173_succeeds() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }

        @Test
        @DisplayName("OPTIONS /api/v1/auth/login from disallowed origin is rejected with 403 Forbidden without Allow-Origin header")
        void preflight_disallowedOrigin_rejected() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://evil-attacker.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .andExpect(status().isForbidden());
        }
    }
}
