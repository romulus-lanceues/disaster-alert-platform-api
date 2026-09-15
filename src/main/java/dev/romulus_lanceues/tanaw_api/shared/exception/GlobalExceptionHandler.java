package dev.romulus_lanceues.tanaw_api.shared.exception;

import dev.romulus_lanceues.tanaw_api.location.GeographicAreaNotFoundException;
import dev.romulus_lanceues.tanaw_api.location.LocationNotFoundException;
import dev.romulus_lanceues.tanaw_api.user.UserAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


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

}
