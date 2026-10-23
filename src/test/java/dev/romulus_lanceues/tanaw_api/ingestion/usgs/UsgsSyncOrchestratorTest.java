package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.alert.AlertMatchingService;
import dev.romulus_lanceues.tanaw_api.alert.DrainResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsgsSyncOrchestrator Unit Tests")
class UsgsSyncOrchestratorTest {

    @Mock
    private UsgsEarthquakeIngestionService ingestionService;

    @Mock
    private AlertMatchingService alertMatchingService;

    @InjectMocks
    private UsgsSyncOrchestrator orchestrator;

    @Nested
    @DisplayName("sync")
    class SyncTests {

        @Test
        @DisplayName("calls ingestion and alert matching drain when events are persisted")
        void callsIngestionAndDrainWhenEventsExist() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<UUID> eventIds = List.of(id1, id2);

            given(ingestionService.ingest()).willReturn(eventIds);
            given(alertMatchingService.drainPendingEvents()).willReturn(new DrainResult(1, 2, 1, List.of()));

            orchestrator.sync();

            then(ingestionService).should().ingest();
            then(alertMatchingService).should().drainPendingEvents();
        }

        @Test
        @DisplayName("runs alert matching drain even when ingestion returns empty list")
        void runsDrainWhenIngestionReturnsEmpty() {
            given(ingestionService.ingest()).willReturn(List.of());
            given(alertMatchingService.drainPendingEvents()).willReturn(new DrainResult(0, 0, 0, List.of()));

            orchestrator.sync();

            then(ingestionService).should().ingest();
            then(alertMatchingService).should().drainPendingEvents();
        }

        @Test
        @DisplayName("runs alert matching drain even when ingestion fails with an exception")
        void runsDrainWhenIngestionThrowsException() {
            given(ingestionService.ingest()).willThrow(new RuntimeException("USGS API timeout"));
            given(alertMatchingService.drainPendingEvents()).willReturn(new DrainResult(0, 0, 0, List.of()));

            orchestrator.sync();

            then(ingestionService).should().ingest();
            then(alertMatchingService).should().drainPendingEvents();
        }

        @Test
        @DisplayName("catches and logs alert matching drain exception without escaping the scheduled method")
        void catchesMatchingDrainExceptionWithoutThrowing() {
            given(ingestionService.ingest()).willReturn(List.of(UUID.randomUUID()));
            doThrow(new RuntimeException("Database connection lost"))
                    .when(alertMatchingService).drainPendingEvents();

            // Should not throw — the exception is caught and logged
            orchestrator.sync();

            then(ingestionService).should().ingest();
            then(alertMatchingService).should().drainPendingEvents();
        }
    }
}
