package dev.romulus_lanceues.tanaw_api.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload required to change a user's password")
public record ChangePasswordRequest(
        @Schema(
                description = "Current account password",
                example = "OldSecurePass123!",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(
                description = "New account password (minimum 8, maximum 100 characters)",
                example = "NewSecurePass456!",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters")
        String newPassword
) {
}
