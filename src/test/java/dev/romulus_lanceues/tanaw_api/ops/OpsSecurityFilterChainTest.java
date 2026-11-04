package dev.romulus_lanceues.tanaw_api.ops;

import dev.romulus_lanceues.tanaw_api.auth.CsrfController;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAccessDeniedHandler;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAuthenticationEntryPoint;
import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CsrfController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
})
@DisplayName("Ops Security Filter Chain Tests")
class OpsSecurityFilterChainTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    OpsSecurityFilterChainTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("POST /api/ops/simulations/earthquake is permitted by opsSecurityFilterChain without token or CSRF")
    void opsEndpoint_isPermittedByOpsSecurityFilterChain() throws Exception {
        // Without controller registered in this WebMvcTest slice, MVC returns 404, proving request was not blocked by Security
        mockMvc.perform(post("/api/ops/simulations/earthquake"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/dev/simulate-earthquake is denied when devSecurityFilterChain is removed")
    void devEndpoint_isDeniedByDefaultSecurityFilterChain() throws Exception {
        // Falling through to anyRequest().denyAll() triggers AccessDeniedHandler or AuthenticationEntryPoint
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

