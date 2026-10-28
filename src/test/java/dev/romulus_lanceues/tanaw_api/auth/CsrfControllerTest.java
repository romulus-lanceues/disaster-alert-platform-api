package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CsrfController.class, AuthController.class})
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
@DisplayName("CsrfController WebMvc Tests")
class CsrfControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    CsrfControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("GET /api/csrf returns 200 OK with empty body and non-HttpOnly XSRF-TOKEN cookie")
    void getCsrf_withoutAuth_returns200AndSetsXsrfCookie() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().value("XSRF-TOKEN", not(emptyOrNullString())))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().path("XSRF-TOKEN", "/"));
    }

    @Test
    @DisplayName("GET /api/csrf over HTTPS marks XSRF-TOKEN cookie as Secure")
    void getCsrf_overHttps_setsSecureXsrfCookie() throws Exception {
        mockMvc.perform(get("/api/csrf").secure(true))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().secure("XSRF-TOKEN", true))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    @DisplayName("POST /api/csrf without CSRF token is rejected with 403 ProblemDetail")
    void postCsrf_withoutCsrfToken_returns403ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/csrf"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.title", is("Forbidden")));
    }

    @Test
    @DisplayName("XSRF-TOKEN cookie from GET /api/csrf is accepted when echoed as X-XSRF-TOKEN on POST /api/v1/auth/login")
    void csrfCookieFromEndpoint_isAcceptedOnLogin() throws Exception {
        var csrfResult = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();
        assertThat(xsrfCookie.getValue()).isNotBlank();

        LoginRequest request = new LoginRequest("user@example.com", "Password123!");
        given(authService.login(any(LoginRequest.class)))
                .willReturn(new AuthResult("sample-access-token", "raw-refresh-token", 900L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("tampered X-XSRF-TOKEN header against valid XSRF-TOKEN cookie returns 403 ProblemDetail")
    void tamperedCsrfHeader_returns403ProblemDetail() throws Exception {
        var csrfResult = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();

        LoginRequest request = new LoginRequest("user@example.com", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", "tampered-invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.title", is("Forbidden")));
    }
}
