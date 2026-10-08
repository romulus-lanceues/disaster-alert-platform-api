package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UpsertResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DisasterEventUpserter {

    private final NamedParameterJdbcTemplate jdbc;


    Optional<UpsertResult> upsert(UUID newId, String source, String externalId, String type,
                                  String status, Instant occurredAt, Instant sourceUpdatedAt,
                                  String severity, String place, String rawJson){

        String sql = """
                INSERT INTO disaster_events
                    (id, source, external_id, disaster_type, status, occurred_at,
                     source_updated_at, severity, place, raw_payload)
                VALUES
                    (:id, :source, :externalId, :type, :status, :occurredAt,
                    :sourceUpdatedAt, :severity, :place, CAST(:raw AS JSONB))
                ON CONFLICT (source, external_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    occurred_at = EXCLUDED.occurred_at,
                    source_updated_at = EXCLUDED.source_updated_at,
                    severity = EXCLUDED.severity,
                    place = EXCLUDED.place,
                    raw_payload = EXCLUDED.raw_payload
                WHERE disaster_events.source_updated_at < EXCLUDED.source_updated_at
                RETURNING id, (xmax = 0) AS inserted
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", newId)
                .addValue("source", source)
                .addValue("externalId", externalId)
                .addValue("type", type)
                .addValue("status", status)
                .addValue("occurredAt", Timestamp.from(occurredAt))
                .addValue("sourceUpdatedAt", Timestamp.from(sourceUpdatedAt))
                .addValue("severity", severity)
                .addValue("place", place)
                .addValue("raw", rawJson);


        List<UpsertResult> rows = jdbc.query(sql, params,
                (rs, i) -> new UpsertResult(rs.getObject("id", UUID.class), rs.getBoolean("inserted")));

        return rows.stream().findFirst();
    }


    void upsertEarthquake(UUID eventId, Double lat, Double lon, Double mag, Double depthKm) {
        String sql = """
        INSERT INTO earthquake
            (disaster_event_id, latitude, longitude, location, magnitude, depth_km)
        VALUES
            (:id, :lat, :lon,
             ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
             :mag, :depth)
        ON CONFLICT (disaster_event_id) DO UPDATE SET
            latitude  = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude,
            location  = EXCLUDED.location,
            magnitude = EXCLUDED.magnitude,
            depth_km  = EXCLUDED.depth_km
        """;

        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", eventId).addValue("lat", lat).addValue("lon", lon)
                .addValue("mag", mag).addValue("depth", depthKm));
    }

}
