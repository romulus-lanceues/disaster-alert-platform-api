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
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProblemDetailsAccessDeniedHandler Unit Tests")
class ProblemDetailsAccessDeniedHandlerTest {

    private ObjectMapper objectMapper;
    private ProblemDetailsAccessDeniedHandler accessDeniedHandler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        accessDeniedHandler = new ProblemDetailsAccessDeniedHandler(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("should return 403, application/problem+json, and ProblemDetail body")
        void shouldReturn403AndProblemDetail_whenAccessDeniedExceptionOccurs() throws IOException {
            request.setRequestURI("/api/v1/admin/dashboard");
            AccessDeniedException exception = new AccessDeniedException("User does not have required permissions");

            accessDeniedHandler.handle(request, response, exception);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("status").asInt()).isEqualTo(403);
            assertThat(body.get("title").asText()).isEqualTo("Forbidden");
            assertThat(body.get("detail").asText()).isEqualTo("User does not have required permissions");
            assertThat(body.get("instance").asText()).isEqualTo("/api/v1/admin/dashboard");
        }

        @Test
        @DisplayName("should use default detail message when accessDeniedException has null message")
        void shouldReturnDefaultDetail_whenAccessDeniedExceptionMessageIsNull() throws IOException {
            request.setRequestURI("/api/v1/restricted");
            AccessDeniedException exception = new AccessDeniedException(null);

            accessDeniedHandler.handle(request, response, exception);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("status").asInt()).isEqualTo(403);
            assertThat(body.get("title").asText()).isEqualTo("Forbidden");
            assertThat(body.get("detail").asText()).isEqualTo("Access is denied");
            assertThat(body.get("instance").asText()).isEqualTo("/api/v1/restricted");
        }

        @Test
        @DisplayName("should use default detail message when accessDeniedException is null")
        void shouldReturnDefaultDetail_whenAccessDeniedExceptionIsNull() throws IOException {
            request.setRequestURI("/api/v1/secured");

            accessDeniedHandler.handle(request, response, null);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.get("status").asInt()).isEqualTo(403);
            assertThat(body.get("title").asText()).isEqualTo("Forbidden");
            assertThat(body.get("detail").asText()).isEqualTo("Access is denied");
            assertThat(body.get("instance").asText()).isEqualTo("/api/v1/secured");
        }
    }
}
