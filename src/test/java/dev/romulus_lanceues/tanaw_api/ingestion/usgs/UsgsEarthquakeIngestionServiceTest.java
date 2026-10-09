package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.alert.AlertMatchingRepository;
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

    @Mock
    private AlertMatchingRepository alertMatchingRepository;

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

    private UsgsFeature createSampleFeature(String id) {
        UsgsFeatureProperties properties = new UsgsFeatureProperties(
                5.0, "Near Leyte", 1712495000000L, 1712499000000L,
                "reviewed", 0, "green", "https://earthquake.usgs.gov/" + id,
                "M 5.0 - Leyte", "earthquake"
        );
        UsgsGeometry geometry = new UsgsGeometry("Point", List.of(124.8, 10.5, 15.0));
        return new UsgsFeature(id, properties, geometry, "{}");
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-08T12:00:00Z"));
        service = new UsgsEarthquakeIngestionService(usgsClient, clock, disasterEventProcessor, alertMatchingRepository);
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
            UsgsFeature f1 = createSampleFeature("us1");
            UsgsFeature f2 = createSampleFeature("us2");
            given(usgsClient.fetchUpdatedAfter(expectedQuerySince))
                    .willReturn(new UsgsResponse(List.of(f1, f2)));

            service.ingest();

            then(usgsClient).should().fetchUpdatedAfter(expectedQuerySince);
            then(disasterEventProcessor).should().persistDisasterEvent(f1);
            then(disasterEventProcessor).should().persistDisasterEvent(f2);
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
            UsgsFeature f3 = createSampleFeature("us3");
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
            UsgsFeature recoveredFeature = createSampleFeature("us-recovered");
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
        @DisplayName("when processor throws exception, lastSync is not advanced")
        void processorExceptionDoesNotAdvanceLastSync() {
            // First run at 12:00:00: client succeeds with 1 feature, but processor throws
            Instant firstQuery = Instant.parse("2026-10-08T10:58:00Z");
            UsgsFeature f1 = createSampleFeature("us-fail");
            given(usgsClient.fetchUpdatedAfter(firstQuery))
                    .willReturn(new UsgsResponse(List.of(f1)));
            doThrow(new RuntimeException("Simulated database failure"))
                    .when(disasterEventProcessor).persistDisasterEvent(f1);

            service.ingest(); // catches exception

            // Advance clock to 12:05:00
            // Since first run threw inside try-block before 'lastSync = started',
            // lastSync was set to 11:00:00, NOT 12:00:00!
            // Next run still queries 11:00:00 - 2min = 10:58:00
            clock.advance(Duration.ofMinutes(5));
            service.ingest();

            // fetchUpdatedAfter(10:58:00) was called twice
            then(usgsClient).should(times(2)).fetchUpdatedAfter(firstQuery);
        }
    }

    @Nested
    @DisplayName("ingest - empty or null response handling")
    class EmptyResponseTests {

        @Test
        @DisplayName("handles null response gracefully and advances lastSync")
        void handlesNullResponseGracefully() {
            given(usgsClient.fetchUpdatedAfter(any())).willReturn(null);

            service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());

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

            service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
        }

        @Test
        @DisplayName("handles empty features list in response gracefully")
        void handlesEmptyFeaturesInResponse() {
            given(usgsClient.fetchUpdatedAfter(any())).willReturn(new UsgsResponse(List.of()));

            service.ingest();

            then(disasterEventProcessor).should(never()).persistDisasterEvent(any());
        }
    }
}
