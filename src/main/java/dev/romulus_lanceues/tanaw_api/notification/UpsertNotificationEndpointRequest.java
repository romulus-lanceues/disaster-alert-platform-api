package dev.romulus_lanceues.tanaw_api.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload to create or update a notification endpoint")
public record UpsertNotificationEndpointRequest(
        @Schema(description = "Notification delivery channel", example = "DISCORD")
        @NotNull(message = "Channel is required")
        NotificationChannel channel,

        @Schema(description = "Destination webhook URL, handle, or address", example = "https://discord.com/api/webhooks/123456789/abcdef-1234")
        @NotBlank(message = "Destination is required")
        @Size(max = 2048, message = "Destination must not exceed 2048 characters")
        String destination
) {
}
