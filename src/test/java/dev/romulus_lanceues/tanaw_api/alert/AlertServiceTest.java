package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService Tests")
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertService alertService;


    private User buildUser(UUID id) {
        return User.builder()
                .id(id)
                .email("user@example.com")
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private GeographicArea buildArea() {
        return GeographicArea.builder()
                .id(UUID.randomUUID())
                .psgcCode("137600000")
                .name("Manila")
                .type(GeographicAreaType.MUNICIPALITY)
                .active(true)
                .build();
    }

    private Location buildLocation(UUID id, User user) {
        return Location.builder()
                .id(id)
                .user(user)
                .name("Home")
                .address("123 Rizal St")
                .geographicArea(buildArea())
                .latitude(14.5995)
                .longitude(120.9842)
                .build();
    }

    private AlertRule buildAlertRule(UUID id, Location location) {
        return AlertRule.builder()
                .id(id)
                .location(location)
                .disasterType(DisasterType.EARTHQUAKE)
                .enabled(true)
                .minimumMagnitude(5.0)
                .radiusKm(50.0)
                .minimumSeverity("MODERATE")
                .build();
    }

    private DisasterEvent buildDisasterEvent(UUID id, Double lat, Double lon) {
        return DisasterEvent.builder()
                .id(id)
                .source("USGS")
                .externalId("usgs-001")
                .disasterType(DisasterType.EARTHQUAKE)
                .occurredAt(Instant.now())
                .latitude(lat)
                .longitude(lon)
                .magnitude(6.2)
                .depthKm(10.0)
                .severity("HIGH")
                .build();
    }

    private Alert buildAlert(UUID id, DisasterEvent event, AlertRule rule, AlertStatus status) {
        return Alert.builder()
                .id(id)
                .disasterEvent(event)
                .alertRule(rule)
                .status(status)
                .triggeredAt(Instant.now())
                .build();
    }


    @Nested
    @DisplayName("createAlert")
    class CreateAlert {

        @Test
        @DisplayName("should create and persist alert when it does not already exist")
        void shouldCreateAlertSuccessfully() {
            UUID eventId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            User user = buildUser(UUID.randomUUID());
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule rule = buildAlertRule(ruleId, location);
            DisasterEvent event = buildDisasterEvent(eventId, 14.6, 120.9);

            given(alertRepository.existsByDisasterEventIdAndAlertRuleId(eventId, ruleId))
                    .willReturn(false);

            Alert saved = buildAlert(UUID.randomUUID(), event, rule, AlertStatus.PENDING);
            given(alertRepository.save(any(Alert.class)))
                    .willReturn(saved);

            Alert result = alertService.createAlert(event, rule);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getStatus()).isEqualTo(AlertStatus.PENDING);

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            then(alertRepository).should().save(captor.capture());
            Alert captured = captor.getValue();
            assertThat(captured.getDisasterEvent()).isEqualTo(event);
            assertThat(captured.getAlertRule()).isEqualTo(rule);
            assertThat(captured.getStatus()).isEqualTo(AlertStatus.PENDING);
            assertThat(captured.getTriggeredAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw AlertAlreadyExistsException when alert already exists")
        void shouldThrowAlertAlreadyExistsException_whenAlertAlreadyExists() {
            UUID eventId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();
            User user = buildUser(UUID.randomUUID());
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule rule = buildAlertRule(ruleId, location);
            DisasterEvent event = buildDisasterEvent(eventId, 14.6, 120.9);

            given(alertRepository.existsByDisasterEventIdAndAlertRuleId(eventId, ruleId))
                    .willReturn(true);

            assertThatThrownBy(() -> alertService.createAlert(event, rule))
                    .isInstanceOf(AlertAlreadyExistsException.class)
                    .hasMessageContaining(eventId.toString())
                    .hasMessageContaining(ruleId.toString());

            then(alertRepository).should(never()).save(any(Alert.class));
        }
    }



    @Nested
    @DisplayName("getAlert")
    class GetAlert {

        @Test
        @DisplayName("should return alert when it exists")
        void shouldReturnAlert_whenExists() {
            UUID alertId = UUID.randomUUID();
            Alert alert = buildAlert(alertId, null, null, AlertStatus.PENDING);

            given(alertRepository.findById(alertId))
                    .willReturn(Optional.of(alert));

            Alert result = alertService.getAlert(alertId);

            assertThat(result).isEqualTo(alert);
        }

        @Test
        @DisplayName("should throw AlertNotFoundException when alert does not exist")
        void shouldThrowAlertNotFoundException_whenAlertDoesNotExist() {
            UUID alertId = UUID.randomUUID();

            given(alertRepository.findById(alertId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.getAlert(alertId))
                    .isInstanceOf(AlertNotFoundException.class)
                    .hasMessageContaining(alertId.toString());
        }
    }

    @Nested
    @DisplayName("getAlertWithDetails")
    class GetAlertWithDetails {

        @Test
        @DisplayName("should return alert with eager-loaded details")
        void shouldReturnAlertWithDetails_whenExists() {
            UUID alertId = UUID.randomUUID();
            Alert alert = buildAlert(alertId, null, null, AlertStatus.PENDING);

            given(alertRepository.findByIdWithDetails(alertId))
                    .willReturn(Optional.of(alert));

            Alert result = alertService.getAlertWithDetails(alertId);

            assertThat(result).isEqualTo(alert);
        }

        @Test
        @DisplayName("should throw AlertNotFoundException when alert does not exist")
        void shouldThrowAlertNotFoundException_whenAlertDoesNotExist() {
            UUID alertId = UUID.randomUUID();

            given(alertRepository.findByIdWithDetails(alertId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.getAlertWithDetails(alertId))
                    .isInstanceOf(AlertNotFoundException.class)
                    .hasMessageContaining(alertId.toString());
        }
    }

    @Nested
    @DisplayName("getAlertForUser")
    class GetAlertForUser {

        @Test
        @DisplayName("should return alert when it belongs to the user")
        void shouldReturnAlert_whenBelongsToUser() {
            UUID userId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule rule = buildAlertRule(UUID.randomUUID(), location);
            UUID alertId = UUID.randomUUID();
            Alert alert = buildAlert(alertId, null, rule, AlertStatus.PENDING);

            given(alertRepository.findByIdWithDetails(alertId))
                    .willReturn(Optional.of(alert));

            Alert result = alertService.getAlertForUser(alertId, userId);

            assertThat(result).isEqualTo(alert);
        }

        @Test
        @DisplayName("should throw AlertNotFoundException when alert belongs to a different user")
        void shouldThrowAlertNotFoundException_whenBelongsToDifferentUser() {
            UUID ownerId = UUID.randomUUID();
            UUID requestingUserId = UUID.randomUUID();

            User owner = buildUser(ownerId);
            Location location = buildLocation(UUID.randomUUID(), owner);
            AlertRule rule = buildAlertRule(UUID.randomUUID(), location);
            UUID alertId = UUID.randomUUID();
            Alert alert = buildAlert(alertId, null, rule, AlertStatus.PENDING);

            given(alertRepository.findByIdWithDetails(alertId))
                    .willReturn(Optional.of(alert));

            assertThatThrownBy(() -> alertService.getAlertForUser(alertId, requestingUserId))
                    .isInstanceOf(AlertNotFoundException.class)
                    .hasMessageContaining(alertId.toString());
        }

        @Test
        @DisplayName("should throw AlertNotFoundException when alert ID does not exist")
        void shouldThrowAlertNotFoundException_whenAlertDoesNotExist() {
            UUID alertId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(alertRepository.findByIdWithDetails(alertId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.getAlertForUser(alertId, userId))
                    .isInstanceOf(AlertNotFoundException.class)
                    .hasMessageContaining(alertId.toString());
        }
    }

    @Nested
    @DisplayName("Paginated and Filtered Queries")
    class Queries {

        @Test
        @DisplayName("should get alerts by user")
        void shouldGetAlertsByUser() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            Page<Alert> page = new PageImpl<>(List.of(buildAlert(UUID.randomUUID(), null, null, AlertStatus.PENDING)));

            given(alertRepository.findByAlertRuleLocationUserId(userId, pageable))
                    .willReturn(page);

            Page<Alert> result = alertService.getAlertsByUser(userId, pageable);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should get alerts by user and status")
        void shouldGetAlertsByUserAndStatus() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            Page<Alert> page = new PageImpl<>(List.of(buildAlert(UUID.randomUUID(), null, null, AlertStatus.PROCESSED)));

            given(alertRepository.findByAlertRuleLocationUserIdAndStatus(userId, AlertStatus.PROCESSED, pageable))
                    .willReturn(page);

            Page<Alert> result = alertService.getAlertsByUserAndStatus(userId, AlertStatus.PROCESSED, pageable);

            assertThat(result).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(AlertStatus.PROCESSED);
        }

        @Test
        @DisplayName("should get alerts by disaster event")
        void shouldGetAlertsByDisasterEvent() {
            UUID eventId = UUID.randomUUID();
            List<Alert> alerts = List.of(buildAlert(UUID.randomUUID(), null, null, AlertStatus.PENDING));

            given(alertRepository.findByDisasterEventId(eventId))
                    .willReturn(alerts);

            List<Alert> result = alertService.getAlertsByDisasterEvent(eventId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should get alerts by alert rule")
        void shouldGetAlertsByAlertRule() {
            UUID ruleId = UUID.randomUUID();
            List<Alert> alerts = List.of(buildAlert(UUID.randomUUID(), null, null, AlertStatus.PENDING));

            given(alertRepository.findByAlertRuleId(ruleId))
                    .willReturn(alerts);

            List<Alert> result = alertService.getAlertsByAlertRule(ruleId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should get alerts by status")
        void shouldGetAlertsByStatus() {
            List<Alert> alerts = List.of(
                    buildAlert(UUID.randomUUID(), null, null, AlertStatus.PENDING),
                    buildAlert(UUID.randomUUID(), null, null, AlertStatus.PENDING)
            );

            given(alertRepository.findByStatus(AlertStatus.PENDING))
                    .willReturn(alerts);

            List<Alert> result = alertService.getAlertsByStatus(AlertStatus.PENDING);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Status Transitions")
    class StatusTransitions {

        @Test
        @DisplayName("should update alert status")
        void shouldUpdateAlertStatus() {
            UUID alertId = UUID.randomUUID();
            Alert alert = buildAlert(alertId, null, null, AlertStatus.PENDING);

            given(alertRepository.findById(alertId))
                    .willReturn(Optional.of(alert));
            given(alertRepository.save(alert))
                    .willReturn(alert);

            Alert result = alertService.updateAlertStatus(alertId, AlertStatus.PROCESSED);

            assertThat(result.getStatus()).isEqualTo(AlertStatus.PROCESSED);
            then(alertRepository).should().save(alert);
        }

        @Test
        @DisplayName("should mark alert as processed")
        void shouldMarkAlertAsProcessed() {
            UUID alertId = UUID.randomUUID();
            Alert alert = buildAlert(alertId, null, null, AlertStatus.PENDING);

            given(alertRepository.findById(alertId))
                    .willReturn(Optional.of(alert));
            given(alertRepository.save(alert))
                    .willReturn(alert);

            Alert result = alertService.markAsProcessed(alertId);

            assertThat(result.getStatus()).isEqualTo(AlertStatus.PROCESSED);
            then(alertRepository).should().save(alert);
        }

        @Test
        @DisplayName("should mark alert as cancelled")
        void shouldMarkAlertAsCancelled() {
            UUID alertId = UUID.randomUUID();
            Alert alert = buildAlert(alertId, null, null, AlertStatus.PENDING);

            given(alertRepository.findById(alertId))
                    .willReturn(Optional.of(alert));
            given(alertRepository.save(alert))
                    .willReturn(alert);

            Alert result = alertService.markAsCancelled(alertId);

            assertThat(result.getStatus()).isEqualTo(AlertStatus.CANCELLED);
            then(alertRepository).should().save(alert);
        }

        @Test
        @DisplayName("should throw AlertNotFoundException when updating non-existent alert")
        void shouldThrowAlertNotFoundException_whenUpdatingNonExistentAlert() {
            UUID alertId = UUID.randomUUID();

            given(alertRepository.findById(alertId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.updateAlertStatus(alertId, AlertStatus.PROCESSED))
                    .isInstanceOf(AlertNotFoundException.class)
                    .hasMessageContaining(alertId.toString());

            then(alertRepository).should(never()).save(any(Alert.class));
        }
    }
}
