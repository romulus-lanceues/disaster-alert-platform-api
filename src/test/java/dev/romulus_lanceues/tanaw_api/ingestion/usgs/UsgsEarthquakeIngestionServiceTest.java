package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeatureProperties;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsGeometry;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsgsEarthquakeIngestionService Tests")
class UsgsEarthquakeIngestionServiceTest {

    @Mock
    private UsgsClient usgsClient;

    @Mock
    private DisasterEventProcessor disasterEventProcessor;

    private MutableClock clock;
    private UsgsEarthquakeIngestionService service;

    private static class MutableClock extends Clock {
        private Instant current;
        private final ZoneId zone = ZoneOffset.UTC;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private UsgsFeature createEarthquakeFeature(String id) {
        UsgsFeatureProperties properties = new UsgsFeatureProperties(
                5.0, "Near Leyte", 1712495000000L, 1712499000000L,
                "reviewed", 0, "green", "https://earthquake.usgs.gov/" + id,
                "M 5.0 - Leyte", "earthquake"
        );
        UsgsGeometry geometry = new UsgsGeometry("Point", List.of(124.8, 10.5, 15.0));
        return new UsgsFeature(id, properties, geometry, "{}");
    }

    private UsgsFeature createNonEarthquakeFeature(String id, String type) {
        UsgsFeatureProperties properties = new UsgsFeatureProperties(
                3.0, "Some Place", 1712495000000L, 1712499000000L,
                "reviewed", 0, null, "https://earthquake.usgs.gov/" + id,
                "Title", type
        );
        UsgsGeometry geometry = new UsgsGeometry("Point", List.of(124.8, 10.5, 15.0));
        return new UsgsFeature(id, properties, geometry, "{}");
    }

    private UsgsFeature createFeatureWithNullProperties(String id) {
        UsgsGeometry geometry = new UsgsGeometry("Point", List.of(124.8, 10.5, 15.0));
        return new UsgsFeature(id, null, geometry, "{}");
    }

    private UsgsFeature createFeatureWithNullGeometry(String id) {
        UsgsFeatureProperties properties = new UsgsFeatureProperties(
                5.0, "Near Leyte", 1712495000000L, 1712499000000L,
                "reviewed", 0, "green", "https://earthquake.usgs.gov/" + id,
                "M 5.0 - Leyte", "earthquake"
        );
        return new UsgsFeature(id, properties, null, "{}");
    }

    private UsgsFeature createFeatureWithMissingCoordinates(String id) {
        UsgsFeatureProperties properties = new UsgsFeatureProperties(
                5.0, "Near Leyte", 1712495000000L, 1712499000000L,
                "reviewed", 0, "green", "https://earthquake.usgs.gov/" + id,
                "M 5.0 - Leyte", "earthquake"
        );
        UsgsGeometry geometry = new UsgsGeometry("Point", List.of());
        return new UsgsFeature(id, properties, geometry, "{}");
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-08T12:00:00Z"));
        service = new UsgsEarthquakeIngestionService(usgsClient, clock, disasterEventProcessor);
    }

    @Nested
    @DisplayName("ingest - initial execution")
    class InitialExecutionTests {

        @Test
        @DisplayName("initializes lastSync to now minus 1 hour and queries with 2 minute overlap buffer")
        void initializesLastSyncToMinusOneHourAndQueriesWithBuffer() {
            // Clock is 12:00:00
            // Initial lastSync = 12:00:00 - 1h = 11:00:00
            // Query since = 11:00:00 - 2min = 10:58:00
            Instant expectedQuerySince = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature f1 = createEarthquakeFeature("us1");
            UsgsFeature f2 = createEarthquakeFeature("us2");
            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(f1, f2)));

            List<UUID> result = service.ingest();

            then(usgsClient).should().fetchUpdatedAfter(expectedQuerySince);
            then(disasterEventProcessor).should().persistDisasterEvent(f1);
            then(disasterEventProcessor).should().persistDisasterEvent(f2);
        }

        @Test
        @DisplayName("returns persisted event IDs from processor")
        void returnsPersistedEventIds() {
            Instant expectedQuerySince = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature f1 = createEarthquakeFeature("us1");
            UUID id1 = UUID.randomUUID();
            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(f1)));
            given(disasterEventProcessor.persistDisasterEvent(f1)).willReturn(id1);

            List<UUID> result = service.ingest();

            assertThat(result).containsExactly(id1);
        }

        @Test
        @DisplayName("excludes null IDs from persisted results")
        void excludesNullIdsFromResults() {
            Instant expectedQuerySince = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature f1 = createEarthquakeFeature("us1");
            UsgsFeature f2 = createEarthquakeFeature("us2");
            UUID id1 = UUID.randomUUID();
            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(f1, f2)));
            given(disasterEventProcessor.persistDisasterEvent(f1)).willReturn(id1);
            given(disasterEventProcessor.persistDisasterEvent(f2)).willReturn(null);

            List<UUID> result = service.ingest();

            assertThat(result).containsExactly(id1);
        }
    }

    @Nested
    @DisplayName("ingest - feature filtering")
    class FeatureFilteringTests {

        @Test
        @DisplayName("skips features with non-earthquake type")
        void skipsNonEarthquakeType() {
            Instant expectedQuerySince = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature nonEq = createNonEarthquakeFeature("us-explosion", "explosion");
            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(nonEq)));

            List<UUID> result = service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips features with null properties")
        void skipsNullProperties() {
            Instant expectedQuerySince = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature nullProps = createFeatureWithNullProperties("us-null-props");
            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(nullProps)));

            List<UUID> result = service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips features with null geometry")
        void skipsNullGeometry() {
            Instant expectedQuerySince = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature nullGeo = createFeatureWithNullGeometry("us-null-geo");
            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(nullGeo)));

            List<UUID> result = service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips features with missing coordinates")
        void skipsMissingCoordinates() {
            Instant expectedQuerySince = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature emptyCoords = createFeatureWithMissingCoordinates("us-empty-coords");
            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(emptyCoords)));

            List<UUID> result = service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("processes only earthquake features from a mixed batch")
        void processesOnlyEarthquakesFromMixedBatch() {
            Instant expectedQuerySince = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature earthquake = createEarthquakeFeature("us-eq");
            UsgsFeature explosion = createNonEarthquakeFeature("us-explosion", "explosion");
            UsgsFeature nullProps = createFeatureWithNullProperties("us-null-props");
            UsgsFeature nullGeo = createFeatureWithNullGeometry("us-null-geo");
            UUID eqId = UUID.randomUUID();

            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(earthquake, explosion, nullProps, nullGeo)));
            given(disasterEventProcessor.persistDisasterEvent(earthquake)).willReturn(eqId);

            List<UUID> result = service.ingest();

            then(disasterEventProcessor).should().persistDisasterEvent(earthquake);
            then(disasterEventProcessor).should(never()).persistDisasterEvent(explosion);
            then(disasterEventProcessor).should(never()).persistDisasterEvent(nullProps);
            then(disasterEventProcessor).should(never()).persistDisasterEvent(nullGeo);
            assertThat(result).containsExactly(eqId);
        }
    }

    @Nested
    @DisplayName("ingest - subsequent executions")
    class SubsequentExecutionTests {

        @Test
        @DisplayName("subsequent run uses previous started timestamp minus 2 minutes buffer")
        void subsequentRunUsesPreviousStartedTime() {
            // First run at 12:00:00
            Instant firstQuery = Instant.parse("2026-10-08T10:58:00Z");
            given(usgsClient.fetchUpdatedAfter(firstQuery))
                    .willReturn(new UsgsResponse(List.of()));

            service.ingest();

            // Advance clock by 5 minutes to 12:05:00
            clock.advance(Duration.ofMinutes(5));

            // Second run should use previous started time (12:00:00) minus 2 minutes = 11:58:00
            Instant expectedSecondQuery = Instant.parse("2026-10-08T11:58:00Z");
            UsgsFeature f3 = createEarthquakeFeature("us3");
            given(usgsClient.fetchUpdatedAfter(expectedSecondQuery))
                    .willReturn(new UsgsResponse(List.of(f3)));

            service.ingest();

            then(usgsClient).should().fetchUpdatedAfter(expectedSecondQuery);
            then(disasterEventProcessor).should().persistDisasterEvent(f3);
        }
    }

    @Nested
    @DisplayName("ingest - failure and retry behavior")
    class FailureAndRetryTests {

        @Test
        @DisplayName("when client throws exception, lastSync is not advanced and next run retries from same time")
        void clientExceptionDoesNotAdvanceLastSync() {
            // First run succeeds at 12:00:00 -> lastSync updated to 12:00:00
            Instant firstQuery = Instant.parse("2026-10-08T10:58:00Z");
            given(usgsClient.fetchUpdatedAfter(firstQuery))
                    .willReturn(new UsgsResponse(List.of()));
            service.ingest();

            // Second run at 12:05:00 fails; third run at 12:10:00 succeeds
            clock.advance(Duration.ofMinutes(5));
            Instant secondQuery = Instant.parse("2026-10-08T11:58:00Z");
            UsgsFeature recoveredFeature = createEarthquakeFeature("us-recovered");
            given(usgsClient.fetchUpdatedAfter(secondQuery))
                    .willThrow(new RuntimeException("Simulated network timeout"))
                    .willReturn(new UsgsResponse(List.of(recoveredFeature)));

            service.ingest(); // catches exception, logs error

            // Third run at 12:10:00: lastSync must STILL be 12:00:00, so query is still 11:58:00
            clock.advance(Duration.ofMinutes(5));
            service.ingest();

            // Verify secondQuery was attempted twice (the failed run + the retry)
            then(usgsClient).should(times(2)).fetchUpdatedAfter(secondQuery);
            then(disasterEventProcessor).should().persistDisasterEvent(recoveredFeature);
        }

        @Test
        @DisplayName("when processor throws for one feature, lastSync still advances and other features are processed")
        void processorExceptionIsIsolatedPerFeatureAndLastSyncAdvances() {
            // Per-feature try/catch means a single processor failure does NOT prevent
            // lastSync from advancing — only usgsClient failures do.
            Instant firstQuery = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature failingFeature = createEarthquakeFeature("us-fail");
            UsgsFeature successFeature = createEarthquakeFeature("us-ok");
            UUID successId = UUID.randomUUID();

            given(usgsClient.fetchUpdatedAfter(firstQuery))
                    .willReturn(new UsgsResponse(List.of(failingFeature, successFeature)));
            doThrow(new RuntimeException("Simulated database failure"))
                    .when(disasterEventProcessor).persistDisasterEvent(failingFeature);
            given(disasterEventProcessor.persistDisasterEvent(successFeature)).willReturn(successId);

            List<UUID> result = service.ingest();

            // The successful feature's ID should be returned; the failing one is skipped
            assertThat(result).containsExactly(successId);

            // Advance clock to 12:05:00
            // Since lastSync DID advance to 12:00:00 (per-feature catch doesn't prevent it),
            // the next query should use 12:00:00 - 2min = 11:58:00
            clock.advance(Duration.ofMinutes(5));
            Instant secondQuery = Instant.parse("2026-10-08T11:58:00Z");
            given(usgsClient.fetchUpdatedAfter(secondQuery))
                    .willReturn(new UsgsResponse(List.of()));

            service.ingest();

            then(usgsClient).should().fetchUpdatedAfter(secondQuery);
        }
    }

    @Nested
    @DisplayName("ingest - empty or null response handling")
    class EmptyResponseTests {

        @Test
        @DisplayName("handles null response gracefully and advances lastSync")
        void handlesNullResponseGracefully() {
            given(usgsClient.fetchUpdatedAfter(any())).willReturn(null);

            List<UUID> result = service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
            assertThat(result).isEmpty();

            // Check that lastSync advanced to 12:00:00 by verifying next run uses 11:58:00
            clock.advance(Duration.ofMinutes(5));
            Instant nextExpectedQuery = Instant.parse("2026-10-08T11:58:00Z");
            given(usgsClient.fetchUpdatedAfter(nextExpectedQuery)).willReturn(new UsgsResponse(List.of()));

            service.ingest();

            then(usgsClient).should().fetchUpdatedAfter(nextExpectedQuery);
        }

        @Test
        @DisplayName("handles null features list in response gracefully")
        void handlesNullFeaturesInResponse() {
            given(usgsClient.fetchUpdatedAfter(any())).willReturn(new UsgsResponse(null));

            List<UUID> result = service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("handles empty features list in response gracefully")
        void handlesEmptyFeaturesInResponse() {
            given(usgsClient.fetchUpdatedAfter(any())).willReturn(new UsgsResponse(List.of()));

            List<UUID> result = service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
            assertThat(result).isEmpty();
        }
    }
}
