package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.user.User;

import java.util.Objects;

public record RefreshTokenRotationResult(String newRawToken, User user) {

    public RefreshTokenRotationResult {
        Objects.requireNonNull(newRawToken, "newRawToken must not be null");
        Objects.requireNonNull(user, "user must not be null");
    }

}
