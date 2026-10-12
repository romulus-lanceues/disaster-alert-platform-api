package dev.romulus_lanceues.tanaw_api.alert;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertMatchingService Unit Tests")
class AlertMatchingServiceTest {

    @Mock
    private AlertBatchMatcher batchMatcher;

    @Mock
    private AlertMatchingRepository repository;

    private final Instant fixedNow = Instant.parse("2026-10-11T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    private final AlertMatchingProperties properties = new AlertMatchingProperties(200, Duration.ofMinutes(10), 10);

    private AlertMatchingService alertMatchingService;

    @BeforeEach
    void setUp() {
        alertMatchingService = new AlertMatchingService(batchMatcher, repository, properties, clock);
    }

    @Nested
    @DisplayName("drainPendingEvents")
    class DrainPendingEventsTests {

        @Test
        @DisplayName("stops immediately when no pending events are claimed")
        void stopsImmediatelyWhenNoPendingEvents() {
            given(batchMatcher.matchBatch(200)).willReturn(new BatchMatchResult(0, 0, List.of()));
            given(repository.findOldestUnmatchedEventTime()).willReturn(Optional.empty());

            DrainResult result = alertMatchingService.drainPendingEvents();

            assertThat(result.batchesProcessed()).isEqualTo(0);
            assertThat(result.eventsClaimed()).isEqualTo(0);
            assertThat(result.alertsCreated()).isEqualTo(0);
            assertThat(result.createdAlerts()).isEmpty();

            then(batchMatcher).should(times(1)).matchBatch(200);
            then(repository).should().findOldestUnmatchedEventTime();
        }

        @Test
        @DisplayName("drains multiple batches until a partial batch is encountered")
        void drainsMultipleBatchesUntilPartialBatch() {
            UUID alert1Id = UUID.randomUUID();
            UUID alert2Id = UUID.randomUUID();
            UUID event1Id = UUID.randomUUID();
            UUID event2Id = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            CreatedAlert alert1 = new CreatedAlert(alert1Id, ruleId, event1Id);
            CreatedAlert alert2 = new CreatedAlert(alert2Id, ruleId, event2Id);

            BatchMatchResult batch1 = new BatchMatchResult(200, 1, List.of(alert1));
            BatchMatchResult batch2 = new BatchMatchResult(50, 1, List.of(alert2));

            given(batchMatcher.matchBatch(200)).willReturn(batch1, batch2);
            given(repository.findOldestUnmatchedEventTime()).willReturn(Optional.empty());

            DrainResult result = alertMatchingService.drainPendingEvents();

            assertThat(result.batchesProcessed()).isEqualTo(2);
            assertThat(result.eventsClaimed()).isEqualTo(250);
            assertThat(result.alertsCreated()).isEqualTo(2);
            assertThat(result.createdAlerts()).containsExactly(alert1, alert2);

            then(batchMatcher).should(times(2)).matchBatch(200);
        }

        @Test
        @DisplayName("stops draining when maxBatchesPerDrain cap is reached even if batches are full")
        void stopsDrainingWhenMaxBatchesPerDrainReached() {
            AlertMatchingProperties cappedProperties = new AlertMatchingProperties(200, Duration.ofMinutes(10), 2);
            AlertMatchingService cappedService = new AlertMatchingService(batchMatcher, repository, cappedProperties, clock);

            UUID alert1Id = UUID.randomUUID();
            UUID alert2Id = UUID.randomUUID();
            UUID event1Id = UUID.randomUUID();
            UUID event2Id = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            CreatedAlert alert1 = new CreatedAlert(alert1Id, ruleId, event1Id);
            CreatedAlert alert2 = new CreatedAlert(alert2Id, ruleId, event2Id);

            BatchMatchResult batch1 = new BatchMatchResult(200, 1, List.of(alert1));
            BatchMatchResult batch2 = new BatchMatchResult(200, 1, List.of(alert2));

            given(batchMatcher.matchBatch(200)).willReturn(batch1, batch2);
            given(repository.findOldestUnmatchedEventTime()).willReturn(Optional.empty());

            DrainResult result = cappedService.drainPendingEvents();

            assertThat(result.batchesProcessed()).isEqualTo(2);
            assertThat(result.eventsClaimed()).isEqualTo(400);
            assertThat(result.alertsCreated()).isEqualTo(2);
            assertThat(result.createdAlerts()).containsExactly(alert1, alert2);

            then(batchMatcher).should(times(2)).matchBatch(200);
        }

        @Test
        @DisplayName("drains single batch when claimed count is less than batch size")
        void drainsSingleBatchWhenClaimedLessThanBatchSize() {
            UUID alertId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            CreatedAlert alert = new CreatedAlert(alertId, ruleId, eventId);

            BatchMatchResult batchResult = new BatchMatchResult(5, 1, List.of(alert));

            given(batchMatcher.matchBatch(200)).willReturn(batchResult);
            given(repository.findOldestUnmatchedEventTime()).willReturn(Optional.empty());

            DrainResult result = alertMatchingService.drainPendingEvents();

            assertThat(result.batchesProcessed()).isEqualTo(1);
            assertThat(result.eventsClaimed()).isEqualTo(5);
            assertThat(result.alertsCreated()).isEqualTo(1);
            assertThat(result.createdAlerts()).containsExactly(alert);

            then(batchMatcher).should(times(1)).matchBatch(200);
        }

        @Test
        @DisplayName("propagates exception when batch matching fails so calling orchestrator can handle and transaction rolls back")
        void propagatesExceptionWhenBatchMatchingFails() {
            given(batchMatcher.matchBatch(200)).willThrow(new RuntimeException("Database deadlock"));

            assertThatThrownBy(() -> alertMatchingService.drainPendingEvents())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database deadlock");
        }

        @Test
        @DisplayName("checks backlog lag and evaluates warning condition without error")
        void checksBacklogLagWhenUnmatchedEventsExist() {
            given(batchMatcher.matchBatch(200)).willReturn(new BatchMatchResult(0, 0, List.of()));
            given(repository.findOldestUnmatchedEventTime())
                    .willReturn(Optional.of(fixedNow.minus(Duration.ofMinutes(15))));

            DrainResult result = alertMatchingService.drainPendingEvents();

            assertThat(result.batchesProcessed()).isEqualTo(0);
            then(repository).should().findOldestUnmatchedEventTime();
        }

        @Test
        @DisplayName("survives exceptions in backlog lag check without failing the drain result")
        void survivesExceptionDuringBacklogLagCheck() {
            given(batchMatcher.matchBatch(200)).willReturn(new BatchMatchResult(0, 0, List.of()));
            given(repository.findOldestUnmatchedEventTime())
                    .willThrow(new RuntimeException("Lag query failed"));

            DrainResult result = alertMatchingService.drainPendingEvents();

            assertThat(result.batchesProcessed()).isEqualTo(0);
            assertThat(result.eventsClaimed()).isEqualTo(0);
        }
    }
}
