package dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsgsGeometry(String type, List<Double> coordinates) {

    public Double longitude() {
        return coordinates != null && !coordinates.isEmpty() ? coordinates.getFirst() : null;
    }

    public Double latitude() {
        return coordinates != null && coordinates.size() > 1 ? coordinates.get(1) : null;
    }

    public Double depthKm() {
        return coordinates != null && coordinates.size() > 2 ? coordinates.get(2) : null;
    }
}
