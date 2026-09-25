package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.config.TestSecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserAlreadyExistsException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, AuthCookieHelper.class, AuthControllerTest.TestConfig.class})
@DisplayName("AuthController WebMvc Tests")
class AuthControllerTest {

    private static final String BASE_URL = "/api/v1/auth";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;
    @TestConfiguration
    static class TestConfig {
        @Bean
        public JwtProperties jwtProperties() {
            return new JwtProperties(
                    "tanaw-api",
                    "tanaw-client",
                    Duration.ofMinutes(15),
                    Duration.ofDays(7),
                    "dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
            );
        }
    }

    @Nested
    @DisplayName("register (POST /api/v1/auth/register)")
    class Register {

        @Test
        @DisplayName("should return 201 Created and no token body when payload is valid")
        void shouldReturn201Created_whenPayloadIsValid() throws Exception {
            RegisterRequest request = new RegisterRequest("alice@example.com", "SecurePass123!");
            willDoNothing().given(authService).register(any(RegisterRequest.class));

            mockMvc.perform(post(BASE_URL + "/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().string(""));

            then(authService).should().register(request);
        }

        @Test
        @DisplayName("should return 400 Bad Request when email is invalid")
        void shouldReturn400BadRequest_whenEmailInvalid() throws Exception {
            RegisterRequest request = new RegisterRequest("not-an-email", "SecurePass123!");

            mockMvc.perform(post(BASE_URL + "/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.email").exists());
        }

        @Test
        @DisplayName("should return 400 Bad Request when password is too short")
        void shouldReturn400BadRequest_whenPasswordTooShort() throws Exception {
            RegisterRequest request = new RegisterRequest("alice@example.com", "short");

            mockMvc.perform(post(BASE_URL + "/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        @Test
        @DisplayName("should return 409 Conflict when email already registered")
        void shouldReturn409Conflict_whenEmailAlreadyRegistered() throws Exception {
            RegisterRequest request = new RegisterRequest("alice@example.com", "SecurePass123!");
            willThrow(new UserAlreadyExistsException("Email is already registered: alice@example.com"))
                    .given(authService).register(any(RegisterRequest.class));

            mockMvc.perform(post(BASE_URL + "/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title", is("User Already Exists")))
                    .andExpect(jsonPath("$.detail", containsString("Email is already registered")));
        }
    }

    @Nested
    @DisplayName("login (POST /api/v1/auth/login)")
    class Login {

        @Test
        @DisplayName("should return 200 OK, token payload, and secure refresh cookie on success")
        void shouldReturn200AndCookie_onSuccessfulLogin() throws Exception {
            LoginRequest request = new LoginRequest("alice@example.com", "SecurePass123!");
            AuthResult authResult = new AuthResult("access-token-xyz", "raw-refresh-token-abc", 900L);

            given(authService.login(any(LoginRequest.class))).willReturn(authResult);

            mockMvc.perform(post(BASE_URL + "/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", is("access-token-xyz")))
                    .andExpect(jsonPath("$.expiresIn", is(900)))
                    .andExpect(cookie().value("refresh_token", "raw-refresh-token-abc"))
                    .andExpect(cookie().httpOnly("refresh_token", true))
                    .andExpect(cookie().secure("refresh_token", true))
                    .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

            then(authService).should().login(request);
        }

        @Test
        @DisplayName("should return 401 Unauthorized ProblemDetail when credentials fail")
        void shouldReturn401Unauthorized_whenCredentialsFail() throws Exception {
            LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword!");
            given(authService.login(any(LoginRequest.class))).willThrow(new InvalidCredentialsException());

            mockMvc.perform(post(BASE_URL + "/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title", is("Unauthorized")))
                    .andExpect(jsonPath("$.detail", is("Invalid email or password")));
        }

        @Test
        @DisplayName("should return 400 Bad Request when login request body is invalid")
        void shouldReturn400BadRequest_whenLoginBodyInvalid() throws Exception {
            LoginRequest request = new LoginRequest("", "");

            mockMvc.perform(post(BASE_URL + "/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }

    @Nested
    @DisplayName("refresh (POST /api/v1/auth/refresh)")
    class Refresh {

        @Test
        @DisplayName("should return 200 OK and updated cookie when refresh token cookie is valid")
        void shouldReturn200AndUpdatedCookie_whenCookieIsValid() throws Exception {
            AuthResult authResult = new AuthResult("new-access-token", "new-raw-refresh-token", 900L);
            given(authService.refresh("existing-refresh-token")).willReturn(authResult);

            mockMvc.perform(post(BASE_URL + "/refresh")
                            .with(csrf())
                            .cookie(new Cookie("refresh_token", "existing-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", is("new-access-token")))
                    .andExpect(jsonPath("$.expiresIn", is(900)))
                    .andExpect(cookie().value("refresh_token", "new-raw-refresh-token"))
                    .andExpect(cookie().httpOnly("refresh_token", true))
                    .andExpect(cookie().secure("refresh_token", true))
                    .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

            then(authService).should().refresh("existing-refresh-token");
        }

        @Test
        @DisplayName("should return 401 Unauthorized when refresh cookie is missing")
        void shouldReturn401Unauthorized_whenCookieMissing() throws Exception {
            given(authService.refresh(null)).willThrow(new InvalidRefreshTokenException("Refresh token is required"));

            mockMvc.perform(post(BASE_URL + "/refresh")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title", is("Invalid Refresh Token")))
                    .andExpect(jsonPath("$.detail", is("Refresh token is required")));
        }

        @Test
        @DisplayName("should return 401 Unauthorized when refresh token is revoked or invalid")
        void shouldReturn401Unauthorized_whenTokenInvalid() throws Exception {
            given(authService.refresh("revoked-token"))
                    .willThrow(new InvalidRefreshTokenException("Refresh token has already been revoked"));

            mockMvc.perform(post(BASE_URL + "/refresh")
                            .with(csrf())
                            .cookie(new Cookie("refresh_token", "revoked-token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title", is("Invalid Refresh Token")))
                    .andExpect(jsonPath("$.detail", is("Refresh token has already been revoked")));
        }
    }

    @Nested
    @DisplayName("logout (POST /api/v1/auth/logout)")
    class Logout {

        @Test
        @DisplayName("should return 204 No Content and clear cookie when cookie is present")
        void shouldReturn204AndClearCookie_whenCookiePresent() throws Exception {
            willDoNothing().given(authService).logout("sample-refresh-token");

            mockMvc.perform(post(BASE_URL + "/logout")
                            .with(csrf())
                            .cookie(new Cookie("refresh_token", "sample-refresh-token")))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

            then(authService).should().logout("sample-refresh-token");
        }

        @Test
        @DisplayName("should return 204 No Content and clear cookie even when cookie is missing")
        void shouldReturn204AndClearCookie_whenCookieMissing() throws Exception {
            willDoNothing().given(authService).logout(null);

            mockMvc.perform(post(BASE_URL + "/logout")
                            .with(csrf()))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));

            then(authService).should().logout(null);
        }
    }
}
