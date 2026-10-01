package dev.romulus_lanceues.tanaw_api.shared.exception;

import dev.romulus_lanceues.tanaw_api.auth.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleException for BadCredentialsException")
    class HandleBadCredentialsException {

        @Test
        @DisplayName("should return 401 ProblemDetail with generic invalid email or password message")
        void shouldReturn401ProblemDetail_whenBadCredentialsExceptionIsThrown() {
            BadCredentialsException exception = new BadCredentialsException("Some internal bad credentials detail");

            ProblemDetail problem = exceptionHandler.handleException(exception);

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(problem.getTitle()).isEqualTo("Unauthorized");
            assertThat(problem.getDetail()).isEqualTo("Invalid email or password");
        }
    }

    @Nested
    @DisplayName("handleException for InvalidCredentialsException")
    class HandleInvalidCredentialsException {

        @Test
        @DisplayName("should return 401 ProblemDetail with generic invalid email or password message")
        void shouldReturn401ProblemDetail_whenInvalidCredentialsExceptionIsThrown() {
            InvalidCredentialsException exception = new InvalidCredentialsException();

            ProblemDetail problem = exceptionHandler.handleException(exception);

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(problem.getTitle()).isEqualTo("Unauthorized");
            assertThat(problem.getDetail()).isEqualTo("Invalid email or password");
        }
    }
}
