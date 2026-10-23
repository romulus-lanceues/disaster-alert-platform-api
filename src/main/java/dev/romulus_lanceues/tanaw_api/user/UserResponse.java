package dev.romulus_lanceues.tanaw_api.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "User profile response data")
public record UserResponse(
        @Schema(
                description = "Unique identifier of the user",
                example = "123e4567-e89b-12d3-a456-426614174000"
        )
        UUID id,

        @Schema(
                description = "Email address of the user",
                example = "user@example.com"
        )
        String email,

        @Schema(
                description = "Current account status of the user"
        )
        UserStatus status,

        @Schema(
                description = "Timestamp when the user account was created",
                example = "2026-09-18T10:00:00Z"
        )
        Instant createdAt,

        @Schema(
                description = "Timestamp when the user account was last updated",
                example = "2026-09-18T10:00:00Z"
        )
        Instant updatedAt
) {

    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
