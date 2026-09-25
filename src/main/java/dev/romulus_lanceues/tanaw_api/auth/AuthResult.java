package dev.romulus_lanceues.tanaw_api.auth;

import java.util.Objects;

public record AuthResult(
        String accessToken,
        String rawRefreshToken,
        long expiresIn
) {
    public AuthResult {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(rawRefreshToken, "rawRefreshToken must not be null");
    }
}
