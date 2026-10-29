package dev.romulus_lanceues.tanaw_api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Endpoints for user authentication, registration, session management, and token refresh")
@RestController
public class CsrfController {

    @Operation(
            summary = "Bootstrap CSRF token cookie",
            description = "Issues a non-HttpOnly XSRF-TOKEN cookie for SPA clients to echo in the X-XSRF-TOKEN header on state-changing auth requests.",
            security = {}
    )
    @ApiResponse(
            responseCode = "200",
            description = "CSRF token cookie issued",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = "XSRF-TOKEN cookie", schema = @Schema(type = "string"))
    )
    @GetMapping("/api/csrf")
    public ResponseEntity<Void> generateCsrfToken() {
        return ResponseEntity.ok().build();
    }
}
