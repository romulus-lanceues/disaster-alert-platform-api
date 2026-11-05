package dev.romulus_lanceues.tanaw_api.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@RequiredArgsConstructor
public class OpsApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String OPS_KEY_HEADER = "X-Ops-Key";

    private final OpsSimulationProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.enabled()) {
            writeProblemDetail(
                    response,
                    HttpStatus.NOT_FOUND,
                    "Ops simulation endpoints are disabled",
                    "Not Found",
                    request.getRequestURI()
            );
            return;
        }

        String providedKey = request.getHeader(OPS_KEY_HEADER);
        if (providedKey == null || providedKey.isBlank()) {
            writeProblemDetail(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Missing or empty X-Ops-Key header",
                    "Unauthorized",
                    request.getRequestURI()
            );
            return;
        }

        String configuredKey = properties.simulationKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            writeProblemDetail(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Ops simulation key is not configured",
                    "Unauthorized",
                    request.getRequestURI()
            );
            return;
        }

        byte[] expectedDigest = sha256(configuredKey);
        byte[] actualDigest = sha256(providedKey);
        if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
            writeProblemDetail(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid X-Ops-Key header",
                    "Unauthorized",
                    request.getRequestURI()
            );
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "ops-runner",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPS"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private void writeProblemDetail(
            HttpServletResponse response,
            HttpStatus status,
            String detail,
            String title,
            String instancePath
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (instancePath != null && !instancePath.isBlank()) {
            problem.setInstance(URI.create(instancePath));
        }

        objectMapper.writeValue(response.getWriter(), problem);
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
