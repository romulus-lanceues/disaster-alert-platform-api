package dev.romulus_lanceues.tanaw_api.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
