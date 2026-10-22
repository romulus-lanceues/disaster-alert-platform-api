package dev.romulus_lanceues.tanaw_api.shared.exception;

import dev.romulus_lanceues.tanaw_api.alert.AlertAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.alert.AlertNotFoundException;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleNotFoundException;
import dev.romulus_lanceues.tanaw_api.auth.InvalidCredentialsException;
import dev.romulus_lanceues.tanaw_api.auth.InvalidRefreshTokenException;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventNotFoundException;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaNotFoundException;
import dev.romulus_lanceues.tanaw_api.location.LocationNotFoundException;
import dev.romulus_lanceues.tanaw_api.notification.NotificationNotFoundException;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.NotificationEndpointNotFoundException;
import dev.romulus_lanceues.tanaw_api.notification.endpoint.RateLimitExceededException;
import dev.romulus_lanceues.tanaw_api.notification.channel.EndpointVerificationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.UnsupportedNotificationChannelException;
import dev.romulus_lanceues.tanaw_api.user.InvalidPasswordException;
import dev.romulus_lanceues.tanaw_api.user.UserAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleException(UserAlreadyExistsException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        problem.setTitle("User Already Exists");

        return problem;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleException(UserNotFoundException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("User Not Found");

        return problem;
    }

    @ExceptionHandler(LocationNotFoundException.class)
    public ProblemDetail handleException(LocationNotFoundException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("Location Not Found");

        return problem;
    }

    @ExceptionHandler(GeographicAreaNotFoundException.class)
    public ProblemDetail handleException(GeographicAreaNotFoundException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("Geographic Area Not Found");

        return problem;
    }

    @ExceptionHandler(AlertRuleNotFoundException.class)
    public ProblemDetail handleException(AlertRuleNotFoundException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("Alert Rule Not Found");

        return problem;
    }

    @ExceptionHandler(DisasterEventNotFoundException.class)
    public ProblemDetail handleException(DisasterEventNotFoundException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("Disaster Event Not Found");

        return problem;
    }

    @ExceptionHandler(DisasterEventAlreadyExistsException.class)
    public ProblemDetail handleException(DisasterEventAlreadyExistsException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        problem.setTitle("Disaster Event Already Exists");

        return problem;
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ProblemDetail handleException(AlertNotFoundException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("Alert Not Found");

        return problem;
    }

    @ExceptionHandler(AlertAlreadyExistsException.class)
    public ProblemDetail handleException(AlertAlreadyExistsException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        problem.setTitle("Alert Already Exists");

        return problem;
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail handleException(NotificationNotFoundException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("Notification Not Found");

        return problem;
    }


    @ExceptionHandler(NotificationEndpointNotFoundException.class)
    public ProblemDetail handleException(NotificationEndpointNotFoundException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        problem.setTitle("Notification Endpoint Not Found");

        return problem;
    }

    @ExceptionHandler(UnsupportedNotificationChannelException.class)
    public ProblemDetail handleException(UnsupportedNotificationChannelException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );

        problem.setTitle("Unsupported Notification Channel");

        return problem;
    }

    @ExceptionHandler(InvalidDestinationException.class)
    public ProblemDetail handleException(InvalidDestinationException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );

        problem.setTitle("Invalid Notification Destination");

        return problem;
    }

    @ExceptionHandler(EndpointVerificationException.class)
    public ProblemDetail handleException(EndpointVerificationException ex) {
        log.error("Endpoint verification failed: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                ex.getMessage()
        );

        problem.setTitle("Endpoint Verification Failed");

        return problem;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleException(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage()
        );
        problem.setTitle("Too Many Requests");

        HttpHeaders headers = new HttpHeaders();
        if (ex.getRetryAfter() != null) {
            long seconds = Math.max(1, ex.getRetryAfter().toSeconds());
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
            problem.setProperty("retryAfterSeconds", seconds);
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(problem);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ProblemDetail handleException(InvalidPasswordException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );

        problem.setTitle("Invalid Password");

        return problem;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleException(InvalidRefreshTokenException ex) {
        log.warn("Invalid refresh token attempt: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage()
        );

        problem.setTitle("Invalid Refresh Token");

        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleException(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
        );

        problem.setTitle("Unauthorized");

        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleException(InvalidCredentialsException ex) {
        log.warn("Invalid credentials attempt: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
        );

        problem.setTitle("Unauthorized");

        return problem;
    }


    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.error("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed for one or more fields."
        );
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(problem);
    }

}
