package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UpsertResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisasterEventUpserter Unit Tests")
class DisasterEventUpserterTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    private DisasterEventUpserter disasterEventUpserter;

    @Nested
    @DisplayName("upsert")
    class UpsertTests {

        @Test
        @DisplayName("correctly formats parameters and returns UpsertResult when row is returned")
        void upsertFormatsParametersAndReturnsResult() {
            UUID id = UUID.randomUUID();
            Instant occurredAt = Instant.parse("2026-10-08T10:00:00Z");
            Instant sourceUpdatedAt = Instant.parse("2026-10-08T10:05:00Z");
            UpsertResult expectedResult = new UpsertResult(id, true);

            given(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                    .willReturn(List.of(expectedResult));

            Optional<UpsertResult> result = disasterEventUpserter.upsert(
                    id, "USGS", "us7000eq1", "EARTHQUAKE", "reviewed",
                    occurredAt, sourceUpdatedAt, "HIGH", "Manila", "{\"raw\":1}"
            );

            assertThat(result).contains(expectedResult);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
            then(jdbc).should().query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

            String sql = sqlCaptor.getValue();
            assertThat(sql)
                    .contains("INSERT INTO disaster_events")
                    .contains("ON CONFLICT (source, external_id) DO UPDATE SET")
                    .contains("RETURNING id, (xmax = 0) AS inserted");

            MapSqlParameterSource params = paramsCaptor.getValue();
            assertThat(params.getValue("id")).isEqualTo(id);
            assertThat(params.getValue("source")).isEqualTo("USGS");
            assertThat(params.getValue("externalId")).isEqualTo("us7000eq1");
            assertThat(params.getValue("type")).isEqualTo("EARTHQUAKE");
            assertThat(params.getValue("status")).isEqualTo("reviewed");
            assertThat(params.getValue("occurredAt")).isEqualTo(Timestamp.from(occurredAt));
            assertThat(params.getValue("sourceUpdatedAt")).isEqualTo(Timestamp.from(sourceUpdatedAt));
            assertThat(params.getValue("severity")).isEqualTo("HIGH");
            assertThat(params.getValue("place")).isEqualTo("Manila");
            assertThat(params.getValue("raw")).isEqualTo("{\"raw\":1}");
        }

        @Test
        @DisplayName("returns empty Optional when query returns no rows (e.g. outdated payload skipped by WHERE clause)")
        void returnsEmptyWhenNoRowsReturned() {
            given(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                    .willReturn(List.of());

            Optional<UpsertResult> result = disasterEventUpserter.upsert(
                    UUID.randomUUID(), "USGS", "us7000eq2", "EARTHQUAKE", "automatic",
                    Instant.now(), Instant.now(), "LOW", "Cebu", "{}"
            );

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("correctly maps ResultSet to UpsertResult in RowMapper")
        void rowMapperExtractsIdAndInsertedFlag() throws SQLException {
            UUID id = UUID.randomUUID();
            ResultSet rs = mock(ResultSet.class);
            given(rs.getObject("id", UUID.class)).willReturn(id);
            given(rs.getBoolean("inserted")).willReturn(false);

            ArgumentCaptor<RowMapper<UpsertResult>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
            given(jdbc.query(anyString(), any(MapSqlParameterSource.class), mapperCaptor.capture()))
                    .willAnswer(invocation -> {
                        RowMapper<UpsertResult> mapper = mapperCaptor.getValue();
                        return List.of(mapper.mapRow(rs, 0));
                    });

            Optional<UpsertResult> result = disasterEventUpserter.upsert(
                    id, "USGS", "us7000eq3", "EARTHQUAKE", "reviewed",
                    Instant.now(), Instant.now(), "MODERATE", "Davao", "{}"
            );

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(id);
            assertThat(result.get().inserted()).isFalse();
        }
    }

    @Nested
    @DisplayName("upsertEarthquake")
    class UpsertEarthquakeTests {

        @Test
        @DisplayName("executes update with correct SQL and spatial parameters")
        void upsertEarthquakeExecutesUpdate() {
            UUID eventId = UUID.randomUUID();
            Double lat = 14.5995;
            Double lon = 120.9842;
            Double mag = 5.6;
            Double depthKm = 15.2;

            disasterEventUpserter.upsertEarthquake(eventId, lat, lon, mag, depthKm);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
            then(jdbc).should().update(sqlCaptor.capture(), paramsCaptor.capture());

            String sql = sqlCaptor.getValue();
            assertThat(sql)
                    .contains("INSERT INTO earthquake")
                    .contains("ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography")
                    .contains("ON CONFLICT (disaster_event_id) DO UPDATE SET");

            MapSqlParameterSource params = paramsCaptor.getValue();
            assertThat(params.getValue("id")).isEqualTo(eventId);
            assertThat(params.getValue("lat")).isEqualTo(lat);
            assertThat(params.getValue("lon")).isEqualTo(lon);
            assertThat(params.getValue("mag")).isEqualTo(mag);
            assertThat(params.getValue("depth")).isEqualTo(depthKm);
        }
    }
}
