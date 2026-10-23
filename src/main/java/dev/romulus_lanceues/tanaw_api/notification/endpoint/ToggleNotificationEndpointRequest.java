package dev.romulus_lanceues.tanaw_api.notification.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload to enable or disable a notification endpoint")
public record ToggleNotificationEndpointRequest(
        @Schema(description = "Whether the endpoint should be enabled or disabled", example = "true")
        @NotNull(message = "Enabled flag is required")
        Boolean enabled
) {
}
