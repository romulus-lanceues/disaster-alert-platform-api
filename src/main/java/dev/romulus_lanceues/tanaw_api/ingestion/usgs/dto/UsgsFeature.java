package dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record UsgsFeature(String id, UsgsFeatureProperties properties, UsgsGeometry geometry) {}
