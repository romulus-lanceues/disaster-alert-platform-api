package dev.romulus_lanceues.tanaw_api.shared.exception;

import dev.romulus_lanceues.tanaw_api.auth.InvalidCredentialsException;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointNotFoundException;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.RateLimitExceededException;
import dev.romulus_lanceues.tanaw_api.notification.channel.EndpointVerificationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.UnsupportedNotificationChannelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;

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

    @Nested
    @DisplayName("Notification Endpoint Exceptions")
    class NotificationEndpointExceptionTests {

        @Test
        @DisplayName("NotificationEndpointNotFoundException returns 404 ProblemDetail")
        void handlesNotificationEndpointNotFound() {
            NotificationEndpointNotFoundException ex = new NotificationEndpointNotFoundException("Endpoint not found: 123");

            ProblemDetail problem = exceptionHandler.handleException(ex);

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(problem.getTitle()).isEqualTo("Notification Endpoint Not Found");
            assertThat(problem.getDetail()).isEqualTo("Endpoint not found: 123");
        }

        @Test
        @DisplayName("UnsupportedNotificationChannelException returns 400 ProblemDetail")
        void handlesUnsupportedChannel() {
            UnsupportedNotificationChannelException ex = new UnsupportedNotificationChannelException(NotificationChannel.TELEGRAM);

            ProblemDetail problem = exceptionHandler.handleException(ex);

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(problem.getTitle()).isEqualTo("Unsupported Notification Channel");
            assertThat(problem.getDetail()).isEqualTo("Notification channel 'TELEGRAM' is not supported yet.");
        }

        @Test
        @DisplayName("InvalidDestinationException returns 400 ProblemDetail")
        void handlesInvalidDestination() {
            InvalidDestinationException ex = new InvalidDestinationException("Discord webhook URL must use HTTPS scheme");

            ProblemDetail problem = exceptionHandler.handleException(ex);

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(problem.getTitle()).isEqualTo("Invalid Notification Destination");
            assertThat(problem.getDetail()).isEqualTo("Discord webhook URL must use HTTPS scheme");
        }

        @Test
        @DisplayName("EndpointVerificationException returns 502 ProblemDetail")
        void handlesEndpointVerificationFailure() {
            EndpointVerificationException ex = new EndpointVerificationException("Discord webhook verification failed: HTTP 404");

            ProblemDetail problem = exceptionHandler.handleException(ex);

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
            assertThat(problem.getTitle()).isEqualTo("Endpoint Verification Failed");
            assertThat(problem.getDetail()).isEqualTo("Discord webhook verification failed: HTTP 404");
        }

        @Test
        @DisplayName("RateLimitExceededException returns 429 ResponseEntity with Retry-After header and retryAfterSeconds property")
        void handlesRateLimitExceeded() {
            RateLimitExceededException ex = new RateLimitExceededException("Rate limit exceeded", Duration.ofSeconds(45));

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("45");
            ProblemDetail body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            assertThat(body.getTitle()).isEqualTo("Too Many Requests");
            assertThat(body.getProperties()).containsEntry("retryAfterSeconds", 45L);
        }

        @Test
        @DisplayName("RateLimitExceededException without retryAfter returns 429 without header or property")
        void handlesRateLimitExceededWithoutRetryAfter() {
            RateLimitExceededException ex = new RateLimitExceededException("Rate limit exceeded", null);

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
            ProblemDetail body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            assertThat(body.getTitle()).isEqualTo("Too Many Requests");
            assertThat(body.getProperties()).isNull();
        }
    }
}
