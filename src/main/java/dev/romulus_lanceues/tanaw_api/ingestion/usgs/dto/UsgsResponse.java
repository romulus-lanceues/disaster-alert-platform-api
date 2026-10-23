package dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsgsResponse(List<UsgsFeature> features) {}
