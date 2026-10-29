package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserAuthState;
import dev.romulus_lanceues.tanaw_api.user.UserController;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserService;
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
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserController.class, CsrfController.class})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        AuthCookieHelper.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
})
@DisplayName("Security Filter Chain Integration Tests")
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    private TestJwtFactory jwtFactory;

    @BeforeEach
    void setUp() {
        jwtFactory = new TestJwtFactory(jwtProperties, clock);
    }

    @Nested
    @DisplayName("Authentication enforcement on /api/v1/**")
    class AuthenticationEnforcement {

        @Test
        @DisplayName("missing token on /api/v1/** returns 401 application/problem+json")
        void missingToken_returns401ProblemDetail() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("expired token returns 401 application/problem+json")
        void expiredToken_returns401ProblemDetail() throws Exception {
            String expiredToken = jwtFactory.createExpiredToken();

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("wrong issuer token returns 401 application/problem+json")
        void wrongIssuerToken_returns401ProblemDetail() throws Exception {
            String wrongIssuerToken = jwtFactory.createWrongIssuerToken();

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongIssuerToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("wrong audience token returns 401 application/problem+json")
        void wrongAudienceToken_returns401ProblemDetail() throws Exception {
            String wrongAudienceToken = jwtFactory.createWrongAudienceToken();

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudienceToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("bad signature (tampered) token returns 401 application/problem+json")
        void badSignatureToken_returns401ProblemDetail() throws Exception {
            String badSignatureToken = jwtFactory.createBadSignatureToken();

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + badSignatureToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("token with outdated authentication version returns 401 application/problem+json")
        void versionMismatchToken_returns401ProblemDetail() throws Exception {
            UUID userId = UUID.randomUUID();
            String outdatedToken = jwtFactory.createValidToken(userId, 0L);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(UserStatus.ACTIVE, 1L)));

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outdatedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("token for disabled account returns 401 application/problem+json")
        void disabledAccountToken_returns401ProblemDetail() throws Exception {
            UUID userId = UUID.randomUUID();
            String token = jwtFactory.createValidToken(userId, 1L);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(UserStatus.DISABLED, 1L)));

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("valid token is authenticated and allowed through to controller")
        void validToken_isAllowedThrough() throws Exception {
            UUID userId = UUID.randomUUID();
            String validToken = jwtFactory.createValidToken(userId, 1L);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(UserStatus.ACTIVE, 1L)));
            given(userService.findById(userId))
                    .willReturn(new dev.romulus_lanceues.tanaw_api.user.UserResponse(
                            userId, "alice@example.com", UserStatus.ACTIVE, clock.instant(), clock.instant()));

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(userId.toString())))
                    .andExpect(jsonPath("$.email", is("alice@example.com")));
        }
    }

    @Nested
    @DisplayName("CSRF enforcement")
    class CsrfEnforcement {

        @Test
        @DisplayName("POST /api/v1/auth/login without CSRF header returns 403 application/problem+json")
        void loginWithoutCsrf_returns403ProblemDetail() throws Exception {
            LoginRequest request = new LoginRequest("user@example.com", "Password123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.title", is("Forbidden")));
        }

        @Test
        @DisplayName("POST /api/v1/auth/login with CSRF header is allowed through to service")
        void loginWithCsrf_isAllowedThrough() throws Exception {
            LoginRequest request = new LoginRequest("user@example.com", "Password123!");
            given(authService.login(any(LoginRequest.class)))
                    .willReturn(new AuthResult("sample-access-token", "raw-refresh-token", 900L));

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /api/v1/auth/refresh without CSRF header returns 403 application/problem+json")
        void refreshWithoutCsrf_returns403ProblemDetail() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("refresh_token", "some-token")))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.title", is("Forbidden")));
        }

        @Test
        @DisplayName("POST /api/v1/auth/logout without CSRF header returns 403 application/problem+json")
        void logoutWithoutCsrf_returns403ProblemDetail() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.title", is("Forbidden")));
        }
    }

    @Nested
    @DisplayName("Permitted endpoints")
    class PermittedEndpoints {

        @Test
        @DisplayName("POST /api/v1/auth/register is permitted without token")
        void register_isPermittedWithoutToken() throws Exception {
            RegisterRequest request = new RegisterRequest("newuser@example.com", "Password123!");

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("POST /api/v1/auth/logout is permitted without token")
        void logout_isPermittedWithoutToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Swagger and OpenAPI restriction in non-dev profile")
    class SwaggerRestriction {

        @Test
        @DisplayName("GET /swagger-ui/index.html is denied when non-dev")
        void swaggerUi_isDenied() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("GET /v3/api-docs is denied when non-dev")
        void apiDocs_isDenied() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }
    }

    @Nested
    @DisplayName("Dev endpoint restriction in non-dev profile")
    class DevEndpointRestriction {

        @Test
        @DisplayName("POST /api/dev/simulate-earthquake without token is denied with 401 ProblemDetail when non-dev")
        void simulateEarthquake_unauthenticated_isDeniedWith401() throws Exception {
            mockMvc.perform(post("/api/dev/simulate-earthquake"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("GET /api/dev/test without token is denied with 401 ProblemDetail when non-dev")
        void getDevEndpoint_unauthenticated_isDeniedWith401() throws Exception {
            mockMvc.perform(get("/api/dev/test"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }

        @Test
        @DisplayName("POST /api/dev/simulate-earthquake even with valid JWT is denied with 403 ProblemDetail when non-dev")
        void simulateEarthquake_authenticated_isDeniedWith403() throws Exception {
            UUID userId = UUID.randomUUID();
            String validToken = jwtFactory.createValidToken(userId, 1L);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(UserStatus.ACTIVE, 1L)));

            mockMvc.perform(post("/api/dev/simulate-earthquake")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.title", is("Forbidden")));
        }
    }

    @Nested
    @DisplayName("Actuator endpoint security")
    class ActuatorEndpointSecurity {

        @Test
        @DisplayName("GET /actuator/health is permitted without token and passes security filter chain")
        void health_isPermittedWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /actuator/health/readiness is permitted without token and passes security filter chain")
        void healthReadiness_isPermittedWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /actuator/info is permitted without token and passes security filter chain")
        void info_isPermittedWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /actuator/env is denied with 401 Unauthorized ProblemDetail")
        void env_isDeniedWith401ProblemDetail() throws Exception {
            mockMvc.perform(get("/actuator/env"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.title", is("Unauthorized")));
        }
    }

    @Nested
    @DisplayName("CORS enforcement")
    class CorsEnforcement {

        @Test
        @DisplayName("OPTIONS preflight to /api/v1/users/me with allowed origin succeeds with 200 and CORS headers")
        void preflight_allowedOrigin_succeeds() throws Exception {
            mockMvc.perform(options("/api/v1/users/me")
                            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"));
        }

        @Test
        @DisplayName("OPTIONS preflight to /api/v1/auth/login with allowed origin succeeds with 200 and CORS headers")
        void preflight_authEndpoint_allowedOrigin_succeeds() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-XSRF-TOKEN"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }

        @Test
        @DisplayName("OPTIONS preflight with disallowed origin does not return Access-Control-Allow-Origin")
        void preflight_disallowedOrigin_rejected() throws Exception {
            mockMvc.perform(options("/api/v1/users/me")
                            .header(HttpHeaders.ORIGIN, "http://evil-attacker.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .andExpect(status().isForbidden());
        }
    }
}
