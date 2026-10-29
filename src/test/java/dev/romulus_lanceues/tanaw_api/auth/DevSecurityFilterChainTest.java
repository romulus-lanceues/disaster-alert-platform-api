package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CsrfController.class)
@ActiveProfiles("dev")
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
})
@DisplayName("Dev Profile Security Filter Chain Tests")
class DevSecurityFilterChainTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    DevSecurityFilterChainTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("POST /api/dev/simulate-earthquake is permitted without token or CSRF in dev profile")
    void simulateEarthquake_isPermittedInDevProfile() throws Exception {
        mockMvc.perform(post("/api/dev/simulate-earthquake"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/csrf returns 200 OK and sets XSRF-TOKEN cookie in dev profile without colliding with TestController")
    void getCsrf_returns200InDevProfile() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
