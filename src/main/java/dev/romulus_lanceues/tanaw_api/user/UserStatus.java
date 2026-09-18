package dev.romulus_lanceues.tanaw_api.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Account status indicating whether the user is active or disabled")
public enum UserStatus {
    @Schema(description = "User account is active and can receive alerts")
    ACTIVE,

    @Schema(description = "User account has been deactivated")
    DISABLED
}
