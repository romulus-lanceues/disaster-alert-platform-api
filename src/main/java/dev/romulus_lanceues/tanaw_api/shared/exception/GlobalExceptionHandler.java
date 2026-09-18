package dev.romulus_lanceues.tanaw_api.shared.exception;

import dev.romulus_lanceues.tanaw_api.alert.AlertAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.alert.AlertNotFoundException;
import dev.romulus_lanceues.tanaw_api.alert.AlertRuleNotFoundException;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventNotFoundException;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaNotFoundException;
import dev.romulus_lanceues.tanaw_api.location.LocationNotFoundException;
import dev.romulus_lanceues.tanaw_api.notification.NotificationAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.notification.NotificationNotFoundException;
import dev.romulus_lanceues.tanaw_api.user.InvalidPasswordException;
import dev.romulus_lanceues.tanaw_api.user.UserAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(NotificationAlreadyExistsException.class)
    public ProblemDetail handleException(NotificationAlreadyExistsException ex) {
        log.error(ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        problem.setTitle("Notification Already Exists");

        return problem;
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
