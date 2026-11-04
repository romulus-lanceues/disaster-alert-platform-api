package dev.romulus_lanceues.tanaw_api.ops;

import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRule;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleNotFoundException;
import dev.romulus_lanceues.tanaw_api.alert.rule.AlertRuleRepository;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventNotFoundException;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.ops.dto.SimulateEarthquakeRequest;
import dev.romulus_lanceues.tanaw_api.ops.dto.SimulatedEarthquakeResponse;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpsSimulationService Unit Tests")
class OpsSimulationServiceTest {

    @Mock
    private OpsSimulationRepository opsSimulationRepository;

    @Mock
    private AlertRuleRepository alertRuleRepository;

    private final Instant fixedNow = Instant.parse("2026-11-04T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    private OpsSimulationService opsSimulationService;

    @BeforeEach
    void setUp() {
        opsSimulationService = new OpsSimulationService(opsSimulationRepository, alertRuleRepository, clock);
    }

    private Location createLocation(String name, double lat, double lon) {
        return Location.builder()
                .id(UUID.randomUUID())
                .name(name)
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    private AlertRule createAlertRule(
            UUID id,
            Location location,
            boolean enabled,
            Double minimumMagnitude,
            Double radiusKm,
            String minimumSeverity
    ) {
        return AlertRule.builder()
                .id(id)
                .location(location)
                .disasterType(DisasterType.EARTHQUAKE)
                .enabled(enabled)
                .minimumMagnitude(minimumMagnitude)
                .radiusKm(radiusKm)
                .minimumSeverity(minimumSeverity)
                .build();
    }

    @Test
    @DisplayName("should simulate earthquake and match alert when within threshold and radius")
    void shouldSimulateEarthquakeAndMatchAlert() {
        UUID ruleId = UUID.randomUUID();
        Location location = createLocation("Manila City Hall", 14.5895, 120.9815);
        AlertRule rule = createAlertRule(ruleId, location, true, 5.0, 50.0, "MODERATE");

        UUID generatedAlertId = UUID.randomUUID();
        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 6.2, 22.2);

        given(alertRuleRepository.findById(ruleId)).willReturn(Optional.of(rule));
        given(opsSimulationRepository.createTargetedAlert(any(UUID.class), eq(ruleId), eq(fixedNow)))
                .willReturn(Optional.of(generatedAlertId));

        SimulatedEarthquakeResponse response = opsSimulationService.simulateEarthquake(request);

        assertThat(response).isNotNull();
        assertThat(response.disasterEventId()).isNotNull();
        assertThat(response.externalId()).isEqualTo("sim-eq-" + response.disasterEventId());
        double expectedLat = 14.5895 + (22.2 / 111.0);
        assertThat(response.latitude()).isCloseTo(expectedLat, Offset.offset(0.0001));
        assertThat(response.longitude()).isEqualTo(120.9815);
        assertThat(response.magnitude()).isEqualTo(6.2);
        assertThat(response.distanceKm()).isEqualTo(22.2);
        assertThat(response.matched()).isTrue();
        assertThat(response.alertId()).isEqualTo(generatedAlertId);
        assertThat(response.locationName()).isEqualTo("Manila City Hall");
        assertThat(response.message())
                .contains("Simulated earthquake created and alert triggered for rule " + ruleId);

        then(opsSimulationRepository).should().insertSimulatedDisasterEvent(
                eq(response.disasterEventId()),
                eq(response.externalId()),
                eq(fixedNow),
                eq("[TEST] Simulated earthquake near Manila City Hall"),
                eq("MODERATE"),
                eq("{}")
        );
        then(opsSimulationRepository).should().insertEarthquake(
                eq(response.disasterEventId()),
                eq(response.latitude()),
                eq(response.longitude()),
                eq(6.2),
                eq(10.0)
        );
        then(opsSimulationRepository).should().createTargetedAlert(
                eq(response.disasterEventId()),
                eq(ruleId),
                eq(fixedNow)
        );
    }

    @Test
    @DisplayName("should simulate earthquake when alert does not match")
    void shouldSimulateEarthquakeWhenAlertDoesNotMatch() {
        UUID ruleId = UUID.randomUUID();
        Location location = createLocation("Baguio City Hall", 16.4023, 120.5960);
        AlertRule rule = createAlertRule(ruleId, location, true, 6.0, 30.0, "HIGH");

        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 5.0, 100.0);

        given(alertRuleRepository.findById(ruleId)).willReturn(Optional.of(rule));
        given(opsSimulationRepository.createTargetedAlert(any(UUID.class), eq(ruleId), eq(fixedNow)))
                .willReturn(Optional.empty());

        SimulatedEarthquakeResponse response = opsSimulationService.simulateEarthquake(request);

        assertThat(response).isNotNull();
        assertThat(response.matched()).isFalse();
        assertThat(response.alertId()).isNull();
        assertThat(response.message())
                .contains("Simulated earthquake created but no alert matched for rule " + ruleId);

        then(opsSimulationRepository).should().insertSimulatedDisasterEvent(
                eq(response.disasterEventId()),
                eq(response.externalId()),
                eq(fixedNow),
                eq("[TEST] Simulated earthquake near Baguio City Hall"),
                eq("HIGH"),
                eq("{}")
        );
    }

    @Test
    @DisplayName("should default magnitude to rule minimum magnitude plus 0.5 when request magnitude is null")
    void shouldDefaultMagnitudeToRuleMinimumPlusHalf() {
        UUID ruleId = UUID.randomUUID();
        Location location = createLocation("Cebu City", 10.3157, 123.8854);
        AlertRule rule = createAlertRule(ruleId, location, true, 4.8, 40.0, "LOW");

        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, null, 15.0);

        given(alertRuleRepository.findById(ruleId)).willReturn(Optional.of(rule));
        given(opsSimulationRepository.createTargetedAlert(any(UUID.class), eq(ruleId), eq(fixedNow)))
                .willReturn(Optional.of(UUID.randomUUID()));

        SimulatedEarthquakeResponse response = opsSimulationService.simulateEarthquake(request);

        assertThat(response.magnitude()).isEqualTo(5.3);
        then(opsSimulationRepository).should().insertEarthquake(
                eq(response.disasterEventId()),
                eq(response.latitude()),
                eq(response.longitude()),
                eq(5.3),
                eq(10.0)
        );
    }

    @Test
    @DisplayName("should default magnitude to 5.5 when both request and rule minimum magnitude are null")
    void shouldDefaultMagnitudeToFivePointFive() {
        UUID ruleId = UUID.randomUUID();
        Location location = createLocation("Davao City", 7.1907, 125.4578);
        AlertRule rule = createAlertRule(ruleId, location, true, null, 50.0, null);

        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, null, 10.0);

        given(alertRuleRepository.findById(ruleId)).willReturn(Optional.of(rule));
        given(opsSimulationRepository.createTargetedAlert(any(UUID.class), eq(ruleId), eq(fixedNow)))
                .willReturn(Optional.of(UUID.randomUUID()));

        SimulatedEarthquakeResponse response = opsSimulationService.simulateEarthquake(request);

        assertThat(response.magnitude()).isEqualTo(5.5);
        then(opsSimulationRepository).should().insertSimulatedDisasterEvent(
                eq(response.disasterEventId()),
                eq(response.externalId()),
                eq(fixedNow),
                eq("[TEST] Simulated earthquake near Davao City"),
                eq("MODERATE"),
                eq("{}")
        );
        then(opsSimulationRepository).should().insertEarthquake(
                eq(response.disasterEventId()),
                eq(response.latitude()),
                eq(response.longitude()),
                eq(5.5),
                eq(10.0)
        );
    }

    @Test
    @DisplayName("should default offset to zero when request offsetKm is null")
    void shouldDefaultOffsetToZero() {
        UUID ruleId = UUID.randomUUID();
        Location location = createLocation("Iloilo City", 10.7202, 122.5621);
        AlertRule rule = createAlertRule(ruleId, location, true, 5.0, 50.0, "MODERATE");

        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 5.5, null);

        given(alertRuleRepository.findById(ruleId)).willReturn(Optional.of(rule));
        given(opsSimulationRepository.createTargetedAlert(any(UUID.class), eq(ruleId), eq(fixedNow)))
                .willReturn(Optional.of(UUID.randomUUID()));

        SimulatedEarthquakeResponse response = opsSimulationService.simulateEarthquake(request);

        assertThat(response.distanceKm()).isEqualTo(0.0);
        assertThat(response.latitude()).isEqualTo(10.7202);
        assertThat(response.longitude()).isEqualTo(122.5621);
    }

    @Test
    @DisplayName("should throw IllegalStateException when alert rule is disabled")
    void shouldThrowIllegalStateExceptionWhenRuleDisabled() {
        UUID ruleId = UUID.randomUUID();
        Location location = createLocation("Tagaytay", 14.1153, 120.9621);
        AlertRule rule = createAlertRule(ruleId, location, false, 5.0, 50.0, "MODERATE");

        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 5.5, 0.0);

        given(alertRuleRepository.findById(ruleId)).willReturn(Optional.of(rule));

        assertThatThrownBy(() -> opsSimulationService.simulateEarthquake(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Alert rule is disabled: " + ruleId);

        then(opsSimulationRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should throw AlertRuleNotFoundException when alert rule does not exist")
    void shouldThrowAlertRuleNotFoundExceptionWhenRuleDoesNotExist() {
        UUID ruleId = UUID.randomUUID();
        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(ruleId, 5.5, 0.0);

        given(alertRuleRepository.findById(ruleId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> opsSimulationService.simulateEarthquake(request))
                .isInstanceOf(AlertRuleNotFoundException.class)
                .hasMessage("Alert rule not found: " + ruleId);

        then(opsSimulationRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should cleanup simulated event when repository deletes 1 row")
    void shouldCleanupSimulatedEvent() {
        UUID disasterEventId = UUID.randomUUID();
        given(opsSimulationRepository.deleteSimulation(disasterEventId)).willReturn(1);

        opsSimulationService.cleanup(disasterEventId);

        then(opsSimulationRepository).should().deleteSimulation(disasterEventId);
    }

    @Test
    @DisplayName("should throw DisasterEventNotFoundException when cleanup finds no event")
    void shouldThrowDisasterEventNotFoundExceptionWhenCleanupFindsNoEvent() {
        UUID disasterEventId = UUID.randomUUID();
        given(opsSimulationRepository.deleteSimulation(disasterEventId)).willReturn(0);

        assertThatThrownBy(() -> opsSimulationService.cleanup(disasterEventId))
                .isInstanceOf(DisasterEventNotFoundException.class)
                .hasMessage("Simulated disaster event not found: " + disasterEventId);

        then(opsSimulationRepository).should().deleteSimulation(disasterEventId);
    }
}
