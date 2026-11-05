package dev.romulus_lanceues.tanaw_api.ops;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@DisplayName("OpsApiKeyAuthenticationFilter Unit Tests")
class OpsApiKeyAuthenticationFilterTest {

    private static final String OPS_KEY_HEADER = "X-Ops-Key";
    private static final String VALID_KEY = "configured-valid-key-at-least-16-chars";
    private static final String TEST_URI = "/api/ops/simulations/earthquake";

    private ObjectMapper objectMapper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        request = new MockHttpServletRequest();
        request.setRequestURI(TEST_URI);
        response = new MockHttpServletResponse();
        filterChain = spy(new MockFilterChain());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should return 404 ProblemDetail when ops simulation is disabled")
    void shouldReturn404ProblemDetailWhenOpsSimulationDisabled() throws Exception {
        OpsSimulationProperties properties = new OpsSimulationProperties(false, null);
        OpsApiKeyAuthenticationFilter filter = new OpsApiKeyAuthenticationFilter(properties, objectMapper);
        request.addHeader(OPS_KEY_HEADER, VALID_KEY);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("title").asText()).isEqualTo("Not Found");
        assertThat(body.get("detail").asText()).isEqualTo("Ops simulation endpoints are disabled");
        assertThat(body.get("instance").asText()).isEqualTo(TEST_URI);

        then(filterChain).shouldHaveNoInteractions();
        assertThat(filterChain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("should return 401 ProblemDetail when X-Ops-Key header is missing")
    void shouldReturn401ProblemDetailWhenHeaderIsMissing() throws Exception {
        OpsSimulationProperties properties = new OpsSimulationProperties(true, VALID_KEY);
        OpsApiKeyAuthenticationFilter filter = new OpsApiKeyAuthenticationFilter(properties, objectMapper);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("detail").asText()).isEqualTo("Missing or empty X-Ops-Key header");
        assertThat(body.get("instance").asText()).isEqualTo(TEST_URI);

        then(filterChain).shouldHaveNoInteractions();
        assertThat(filterChain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("should return 401 ProblemDetail when X-Ops-Key header is blank or whitespace")
    void shouldReturn401ProblemDetailWhenHeaderIsBlank(String blankKey) throws Exception {
        OpsSimulationProperties properties = new OpsSimulationProperties(true, VALID_KEY);
        OpsApiKeyAuthenticationFilter filter = new OpsApiKeyAuthenticationFilter(properties, objectMapper);
        request.addHeader(OPS_KEY_HEADER, blankKey);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("detail").asText()).isEqualTo("Missing or empty X-Ops-Key header");
        assertThat(body.get("instance").asText()).isEqualTo(TEST_URI);

        then(filterChain).shouldHaveNoInteractions();
        assertThat(filterChain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("should return 401 ProblemDetail when simulation key is not configured or blank")
    void shouldReturn401ProblemDetailWhenSimulationKeyNotConfigured(String unconfiguredKey) throws Exception {
        OpsSimulationProperties properties = mock(OpsSimulationProperties.class);
        given(properties.enabled()).willReturn(true);
        given(properties.simulationKey()).willReturn(unconfiguredKey);

        OpsApiKeyAuthenticationFilter filter = new OpsApiKeyAuthenticationFilter(properties, objectMapper);
        request.addHeader(OPS_KEY_HEADER, VALID_KEY);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("detail").asText()).isEqualTo("Ops simulation key is not configured");
        assertThat(body.get("instance").asText()).isEqualTo(TEST_URI);

        then(filterChain).shouldHaveNoInteractions();
        assertThat(filterChain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("should return 401 ProblemDetail when X-Ops-Key is invalid")
    void shouldReturn401ProblemDetailWhenKeyIsInvalid() throws Exception {
        OpsSimulationProperties properties = new OpsSimulationProperties(true, VALID_KEY);
        OpsApiKeyAuthenticationFilter filter = new OpsApiKeyAuthenticationFilter(properties, objectMapper);
        request.addHeader(OPS_KEY_HEADER, "invalid-ops-key-attempt");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("detail").asText()).isEqualTo("Invalid X-Ops-Key header");
        assertThat(body.get("instance").asText()).isEqualTo(TEST_URI);

        then(filterChain).shouldHaveNoInteractions();
        assertThat(filterChain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("should authenticate and proceed when X-Ops-Key is valid")
    void shouldAuthenticateAndProceedWhenKeyIsValid() throws Exception {
        OpsSimulationProperties properties = new OpsSimulationProperties(true, VALID_KEY);
        OpsApiKeyAuthenticationFilter filter = new OpsApiKeyAuthenticationFilter(properties, objectMapper);
        request.addHeader(OPS_KEY_HEADER, VALID_KEY);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        then(filterChain).should().doFilter(request, response);
        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(filterChain.getResponse()).isSameAs(response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("ops-runner");
        assertThat(auth.getName()).isEqualTo("ops-runner");
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OPS");
    }
}
