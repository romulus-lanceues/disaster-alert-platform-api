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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
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
        @DisplayName("valid token is authenticated and allowed through to controller")
        void validToken_isAllowedThrough() throws Exception {
            UUID userId = UUID.randomUUID();
            String validToken = jwtFactory.createValidToken(userId, 1L);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(UserStatus.ACTIVE, 1L)));
            given(userService.findById(userId))
                    .willReturn(new dev.romulus_lanceues.tanaw_api.user.UserResponse(
                            userId, "alice@example.com", UserStatus.ACTIVE, clock.instant(), clock.instant()));

            mockMvc.perform(get("/api/v1/users/" + userId)
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
}
