package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.alert.AlertMatchingService;
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
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsgsSyncOrchestrator Tests")
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
        @DisplayName("calls ingestion then alert matching when events are persisted")
        void callsIngestionThenAlertMatchingWhenEventsExist() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<UUID> eventIds = List.of(id1, id2);

            given(ingestionService.ingest()).willReturn(eventIds);

            orchestrator.sync();

            then(ingestionService).should().ingest();
            then(alertMatchingService).should().createAlertForEvents(eventIds);
        }

        @Test
        @DisplayName("skips alert matching when ingestion returns empty list")
        void skipsAlertMatchingWhenNoEvents() {
            given(ingestionService.ingest()).willReturn(List.of());

            orchestrator.sync();

            then(ingestionService).should().ingest();
            then(alertMatchingService).should(never()).createAlertForEvents(List.of());
        }

        @Test
        @DisplayName("catches and logs alert matching exceptions without rethrowing")
        void catchesAlertMatchingException() {
            UUID id1 = UUID.randomUUID();
            List<UUID> eventIds = List.of(id1);

            given(ingestionService.ingest()).willReturn(eventIds);
            doThrow(new RuntimeException("Database connection lost"))
                    .when(alertMatchingService).createAlertForEvents(eventIds);

            // Should not throw — the exception is caught and logged
            orchestrator.sync();

            then(ingestionService).should().ingest();
            then(alertMatchingService).should().createAlertForEvents(eventIds);
        }

        @Test
        @DisplayName("ingestion failure propagates naturally (not caught by orchestrator)")
        void ingestionFailurePropagates() {
            given(ingestionService.ingest()).willThrow(new RuntimeException("USGS API unreachable"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> orchestrator.sync());

            then(alertMatchingService).should(never()).createAlertForEvents(List.of());
        }
    }
}
