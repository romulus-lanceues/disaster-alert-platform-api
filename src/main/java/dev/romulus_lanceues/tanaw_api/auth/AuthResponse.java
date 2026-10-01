package dev.romulus_lanceues.tanaw_api.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response payload containing access token and expiry")
public record AuthResponse(
        @Schema(
                description = "JWT access token",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        )
        String accessToken,

        @Schema(
                description = "Access token expiry duration in seconds",
                example = "900"
        )
        long expiresIn
) {
}
