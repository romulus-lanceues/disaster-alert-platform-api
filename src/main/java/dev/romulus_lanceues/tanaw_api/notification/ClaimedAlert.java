package dev.romulus_lanceues.tanaw_api.notification;

import java.time.Instant;
import java.util.UUID;

public record ClaimedAlert(
        UUID id,
        Instant occurredAt
) {
}
