package dev.romulus_lanceues.tanaw_api.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProblemDetailsAuthenticationEntryPoint Unit Tests")
class ProblemDetailsAuthenticationEntryPointTest {

    private ObjectMapper objectMapper;
    private ProblemDetailsAuthenticationEntryPoint entryPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entryPoint = new ProblemDetailsAuthenticationEntryPoint(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("commence")
    class Commence {

        @Test
        @DisplayName("should return 401, application/problem+json, WWW-Authenticate Bearer, and ProblemDetail body")
        void shouldReturn401AndProblemDetail_whenAuthenticationExceptionOccurs() throws IOException {
            request.setRequestURI("/api/v1/alerts");
            InsufficientAuthenticationException exception =
                    new InsufficientAuthenticationException("Full authentication is required to access this resource");

            entryPoint.commence(request, response, exception);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("status").asInt()).isEqualTo(401);
            assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
            assertThat(body.get("detail").asText()).isEqualTo("Full authentication is required to access this resource");
            assertThat(body.get("instance").asText()).isEqualTo("/api/v1/alerts");
        }

        @Test
        @DisplayName("should use default detail message when authException has null message")
        void shouldReturnDefaultDetail_whenAuthExceptionMessageIsNull() throws IOException {
            request.setRequestURI("/api/v1/locations");
            BadCredentialsException exception = new BadCredentialsException(null);

            entryPoint.commence(request, response, exception);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("status").asInt()).isEqualTo(401);
            assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
            assertThat(body.get("detail").asText()).isEqualTo("Full authentication is required to access this resource");
            assertThat(body.get("instance").asText()).isEqualTo("/api/v1/locations");
        }

        @Test
        @DisplayName("should use default detail message when authException is null")
        void shouldReturnDefaultDetail_whenAuthExceptionIsNull() throws IOException {
            request.setRequestURI("/api/v1/users/me");

            entryPoint.commence(request, response, null);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("status").asInt()).isEqualTo(401);
            assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
            assertThat(body.get("detail").asText()).isEqualTo("Full authentication is required to access this resource");
            assertThat(body.get("instance").asText()).isEqualTo("/api/v1/users/me");
        }
    }
}
