package dev.romulus_lanceues.tanaw_api.disaster;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record DisasterEventRequest(
        String source,
        String externalId,
        DisasterType disasterType,
        Instant occurredAt,
        String status,
        Instant sourceUpdatedAt,
        String place,
        String severity,
        JsonNode rawPayload
) {
}
