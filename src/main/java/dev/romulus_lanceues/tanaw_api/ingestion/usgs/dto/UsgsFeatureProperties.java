package dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsgsFeatureProperties(Double mag, String place, Long time, Long updated,
                             String status, Integer tsunami, String alert, String url,
                             String title, String type) {}
