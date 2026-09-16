package dev.romulus_lanceues.tanaw_api.disaster;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record DisasterEventRequest(
        String source,
        String externalId,
        DisasterType disasterType,
        Instant occurredAt,
        Double latitude,
        Double longitude,
        Double magnitude,
        Double depthKm,
        String severity,
        JsonNode rawPayload
) {
}
