package dev.romulus_lanceues.tanaw_api.auth;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied to {}: {}",
                request != null ? request.getRequestURI() : "unknown",
                accessDeniedException != null ? accessDeniedException.getMessage() : "forbidden");

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        //Check if accessDeniedException properties are not null or empty
        String detail = (accessDeniedException != null && accessDeniedException.getMessage() != null &&
                !accessDeniedException.getMessage().isBlank())
                ? accessDeniedException.getMessage()
                : "Access is denied";

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
        problemDetail.setTitle("Forbidden");

        if (request != null && request.getRequestURI() != null && !request.getRequestURI().isBlank()) {
            problemDetail.setInstance(URI.create(request.getRequestURI()));
        }

        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
