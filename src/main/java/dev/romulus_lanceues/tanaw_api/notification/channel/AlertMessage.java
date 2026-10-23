package dev.romulus_lanceues.tanaw_api.notification.channel;

import java.time.Instant;

/**
 * Channel-neutral model representing a formatted earthquake alert message.
 * Provides canonical and pre-formatted presentation attributes ready for
 * channel-specific rendering (Discord embed, Telegram HTML, Email HTML).
 */
public record AlertMessage(
        String title,
        String place,
        Double magnitude,
        String formattedMagnitude,
        Double distanceKm,
        String formattedDistance,
        String locationName,
        Double depthKm,
        String formattedDepth,
        String severity,
        Instant occurredAt,
        String formattedTimeManila,
        String formattedTimeUtc,
        String eventStatus,
        String reviewNotice,
        String usgsUrl,
        String footer
) {
}
