package dev.romulus_lanceues.tanaw_api.location;

import java.util.UUID;

public record LocationRequest(
        UUID userId,
        String name,
        String address,
        String geographicAreaCode,
        Double latitude,
        Double longitude
) {
}
