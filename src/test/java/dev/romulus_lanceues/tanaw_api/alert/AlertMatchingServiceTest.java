package dev.romulus_lanceues.tanaw_api.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertMatchingService Tests")
class AlertMatchingServiceTest {

    @Mock
    private AlertMatchingRepository repository;

    @InjectMocks
    private AlertMatchingService alertMatchingService;

    @Nested
    @DisplayName("createAlertForEvents")
    class CreateAlertForEventsTests {

        @Test
        @DisplayName("returns empty list when eventIds is null")
        void returnsEmptyListWhenNull() {
            List<CreatedAlert> result = alertMatchingService.createAlertForEvents(null);

            assertThat(result).isEmpty();
            then(repository).should(never()).persistAlert(null);
        }

        @Test
        @DisplayName("returns empty list when eventIds is empty")
        void returnsEmptyListWhenEmpty() {
            List<CreatedAlert> result = alertMatchingService.createAlertForEvents(List.of());

            assertThat(result).isEmpty();
            then(repository).should(never()).persistAlert(List.of());
        }

        @Test
        @DisplayName("delegates to repository and returns created alerts")
        void delegatesToRepositoryAndReturnsAlerts() {
            UUID eventId1 = UUID.randomUUID();
            UUID eventId2 = UUID.randomUUID();
            List<UUID> eventIds = List.of(eventId1, eventId2);

            CreatedAlert alert1 = new CreatedAlert(UUID.randomUUID(), UUID.randomUUID(), eventId1);
            CreatedAlert alert2 = new CreatedAlert(UUID.randomUUID(), UUID.randomUUID(), eventId2);
            List<CreatedAlert> expectedAlerts = List.of(alert1, alert2);

            given(repository.persistAlert(eventIds)).willReturn(expectedAlerts);

            List<CreatedAlert> result = alertMatchingService.createAlertForEvents(eventIds);

            assertThat(result).isEqualTo(expectedAlerts);
            then(repository).should().persistAlert(eventIds);
        }

        @Test
        @DisplayName("returns empty list when no rules match any events")
        void returnsEmptyWhenNoRulesMatch() {
            UUID eventId = UUID.randomUUID();
            List<UUID> eventIds = List.of(eventId);

            given(repository.persistAlert(eventIds)).willReturn(List.of());

            List<CreatedAlert> result = alertMatchingService.createAlertForEvents(eventIds);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("handles single event matching multiple rules")
        void handlesSingleEventMatchingMultipleRules() {
            UUID eventId = UUID.randomUUID();
            List<UUID> eventIds = List.of(eventId);

            UUID ruleId1 = UUID.randomUUID();
            UUID ruleId2 = UUID.randomUUID();
            CreatedAlert alert1 = new CreatedAlert(UUID.randomUUID(), ruleId1, eventId);
            CreatedAlert alert2 = new CreatedAlert(UUID.randomUUID(), ruleId2, eventId);
            List<CreatedAlert> expectedAlerts = List.of(alert1, alert2);

            given(repository.persistAlert(eventIds)).willReturn(expectedAlerts);

            List<CreatedAlert> result = alertMatchingService.createAlertForEvents(eventIds);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(CreatedAlert::disasterEventId)
                    .containsOnly(eventId);
            assertThat(result).extracting(CreatedAlert::alertRuleId)
                    .containsExactlyInAnyOrder(ruleId1, ruleId2);
        }

        @Test
        @DisplayName("handles multiple events where only some match rules")
        void handlesPartialMatches() {
            UUID eventId1 = UUID.randomUUID();
            UUID eventId2 = UUID.randomUUID();
            UUID eventId3 = UUID.randomUUID();
            List<UUID> eventIds = List.of(eventId1, eventId2, eventId3);

            // Only eventId1 matches a rule
            CreatedAlert alert1 = new CreatedAlert(UUID.randomUUID(), UUID.randomUUID(), eventId1);
            given(repository.persistAlert(eventIds)).willReturn(List.of(alert1));

            List<CreatedAlert> result = alertMatchingService.createAlertForEvents(eventIds);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().disasterEventId()).isEqualTo(eventId1);
        }
    }
}
