package dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto;

import java.util.UUID;

public record UpsertResult(UUID id, boolean inserted) {}
