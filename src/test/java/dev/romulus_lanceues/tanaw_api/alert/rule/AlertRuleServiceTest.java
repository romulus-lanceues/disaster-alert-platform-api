package dev.romulus_lanceues.tanaw_api.alert.rule;

import dev.romulus_lanceues.tanaw_api.disaster.DisasterType;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.location.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.location.LocationNotFoundException;
import dev.romulus_lanceues.tanaw_api.location.LocationRepository;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@DisplayName("AlertRuleService Tests")
class AlertRuleServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private AlertRuleService alertRuleService;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();


    private User buildUser(UUID id) {
        return User.builder()
                .id(id)
                .email("alice@example.com")
                .passwordHash("hash_abc")
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
                .latitude(14.60)
                .longitude(120.98)
                .location(geoPointFactory.create(14.60, 120.98))
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


    @Nested
    @DisplayName("createAlertRule")
    class CreateAlertRule {

        @Test
        @DisplayName("should create and return alert rule response when location exists for user")
        void shouldCreateAndReturnAlertRuleResponse_whenLocationExistsForUser() {

            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(locationId, user);

            AlertRuleRequest request = new AlertRuleRequest(
                    userId, locationId, DisasterType.EARTHQUAKE, 5.0, 50.0, "MODERATE");

            AlertRule savedRule = buildAlertRule(UUID.randomUUID(), location);

            given(locationRepository.findByIdAndUserId(locationId, userId))
                    .willReturn(Optional.of(location));
            given(alertRuleRepository.save(any(AlertRule.class)))
                    .willReturn(savedRule);


            AlertRuleResponse result = alertRuleService.createAlertRule(request);

            assertThat(result).isNotNull();
            assertThat(result.disasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(result.minimumMagnitude()).isEqualTo(5.0);
            assertThat(result.radiusKm()).isEqualTo(50.0);
            assertThat(result.minimumSeverity()).isEqualTo("MODERATE");
            assertThat(result.enabled()).isTrue();
            assertThat(result.locationId()).isEqualTo(locationId);
            assertThat(result.locationName()).isEqualTo("Home");
        }

        @Test
        @DisplayName("should create alert rule response with null optional thresholds")
        void shouldCreateAlertRuleResponse_withNullOptionalThresholds() {

            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(locationId, user);

            AlertRuleRequest request = new AlertRuleRequest(
                    userId, locationId, DisasterType.TYPHOON, null, null, null);

            AlertRule savedRule = AlertRule.builder()
                    .id(UUID.randomUUID())
                    .location(location)
                    .disasterType(DisasterType.TYPHOON)
                    .enabled(true)
                    .build();

            given(locationRepository.findByIdAndUserId(locationId, userId))
                    .willReturn(Optional.of(location));
            given(alertRuleRepository.save(any(AlertRule.class)))
                    .willReturn(savedRule);


            AlertRuleResponse result = alertRuleService.createAlertRule(request);


            assertThat(result).isNotNull();
            assertThat(result.disasterType()).isEqualTo(DisasterType.TYPHOON);
            assertThat(result.minimumMagnitude()).isNull();
            assertThat(result.radiusKm()).isNull();
            assertThat(result.minimumSeverity()).isNull();
        }

        @Test
        @DisplayName("should throw LocationNotFoundException when location does not exist for user")
        void shouldThrowLocationNotFoundException_whenLocationDoesNotExistForUser() {

            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            AlertRuleRequest request = new AlertRuleRequest(
                    userId, locationId, DisasterType.EARTHQUAKE, 5.0, 50.0, "MODERATE");

            given(locationRepository.findByIdAndUserId(locationId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertRuleService.createAlertRule(request))
                    .isInstanceOf(LocationNotFoundException.class)
                    .hasMessageContaining(locationId.toString());

            then(alertRuleRepository).should(never()).save(any(AlertRule.class));
        }
    }



    @Nested
    @DisplayName("getAlertRule")
    class GetAlertRule {

        @Test
        @DisplayName("should return alert rule response when it belongs to the user")
        void shouldReturnAlertRuleResponse_whenItBelongsToUser() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule alertRule = buildAlertRule(alertRuleId, location);

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.of(alertRule));


            AlertRuleResponse result = alertRuleService.getAlertRule(alertRuleId, userId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(alertRuleId);
            assertThat(result.disasterType()).isEqualTo(DisasterType.EARTHQUAKE);
        }

        @Test
        @DisplayName("should throw AlertRuleNotFoundException when alert rule does not exist for user")
        void shouldThrowAlertRuleNotFoundException_whenAlertRuleDoesNotExistForUser() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.empty());


            assertThatThrownBy(() -> alertRuleService.getAlertRule(alertRuleId, userId))
                    .isInstanceOf(AlertRuleNotFoundException.class)
                    .hasMessageContaining(alertRuleId.toString());
        }
    }



    @Nested
    @DisplayName("getAlertRulesByUser")
    class GetAlertRulesByUser {

        @Test
        @DisplayName("should return all alert rule responses for a user")
        void shouldReturnAllAlertRuleResponses_forUser() {

            UUID userId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);

            List<AlertRule> rules = List.of(
                    buildAlertRule(UUID.randomUUID(), location),
                    buildAlertRule(UUID.randomUUID(), location)
            );

            given(alertRuleRepository.findByLocationUserId(userId))
                    .willReturn(rules);


            List<AlertRuleResponse> result = alertRuleService.getAlertRulesByUser(userId);


            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(response ->
                    assertThat(response.locationId()).isEqualTo(location.getId()));
        }

        @Test
        @DisplayName("should return empty list when user has no alert rules")
        void shouldReturnEmptyList_whenUserHasNoAlertRules() {

            UUID userId = UUID.randomUUID();

            given(alertRuleRepository.findByLocationUserId(userId))
                    .willReturn(List.of());

            List<AlertRuleResponse> result = alertRuleService.getAlertRulesByUser(userId);

            assertThat(result).isEmpty();
        }
    }



    @Nested
    @DisplayName("getAlertRulesByLocation")
    class GetAlertRulesByLocation {

        @Test
        @DisplayName("should return all alert rule responses for a location owned by user")
        void shouldReturnAllAlertRuleResponses_forLocationOwnedByUser() {

            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(locationId, user);

            List<AlertRule> rules = List.of(
                    buildAlertRule(UUID.randomUUID(), location),
                    buildAlertRule(UUID.randomUUID(), location)
            );

            given(alertRuleRepository.findByLocationIdAndLocationUserId(locationId, userId))
                    .willReturn(rules);


            List<AlertRuleResponse> result = alertRuleService.getAlertRulesByLocation(locationId, userId);


            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(response ->
                    assertThat(response.locationId()).isEqualTo(locationId));
        }

        @Test
        @DisplayName("should return empty list when location has no alert rules for user")
        void shouldReturnEmptyList_whenLocationHasNoAlertRulesForUser() {

            UUID userId = UUID.randomUUID();
            UUID locationId = UUID.randomUUID();

            given(alertRuleRepository.findByLocationIdAndLocationUserId(locationId, userId))
                    .willReturn(List.of());

            List<AlertRuleResponse> result = alertRuleService.getAlertRulesByLocation(locationId, userId);

            assertThat(result).isEmpty();
        }
    }



    @Nested
    @DisplayName("updateThresholds")
    class UpdateThresholds {

        @Test
        @DisplayName("should update thresholds and return the saved alert rule response")
        void shouldUpdateThresholds_andReturnSavedAlertRuleResponse() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule alertRule = buildAlertRule(alertRuleId, location);

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.of(alertRule));
            given(alertRuleRepository.save(alertRule))
                    .willReturn(alertRule);

            AlertRuleResponse result = alertRuleService.updateThresholds(
                    alertRuleId, userId, 7.0, 100.0, "SEVERE");

            assertThat(result).isNotNull();
            assertThat(result.minimumMagnitude()).isEqualTo(7.0);
            assertThat(result.radiusKm()).isEqualTo(100.0);
            assertThat(result.minimumSeverity()).isEqualTo("SEVERE");
        }

        @Test
        @DisplayName("should allow clearing thresholds by passing null values")
        void shouldAllowClearingThresholds_byPassingNullValues() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule alertRule = buildAlertRule(alertRuleId, location);

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.of(alertRule));
            given(alertRuleRepository.save(alertRule))
                    .willReturn(alertRule);

            AlertRuleResponse result = alertRuleService.updateThresholds(
                    alertRuleId, userId, null, null, null);

            assertThat(result.minimumMagnitude()).isNull();
            assertThat(result.radiusKm()).isNull();
            assertThat(result.minimumSeverity()).isNull();
        }

        @Test
        @DisplayName("should throw AlertRuleNotFoundException when alert rule does not exist for user")
        void shouldThrowAlertRuleNotFoundException_whenAlertRuleDoesNotExist() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertRuleService.updateThresholds(
                    alertRuleId, userId, 7.0, 100.0, "SEVERE"))
                    .isInstanceOf(AlertRuleNotFoundException.class)
                    .hasMessageContaining(alertRuleId.toString());

            then(alertRuleRepository).should(never()).save(any(AlertRule.class));
        }
    }


    @Nested
    @DisplayName("enableAlertRule")
    class EnableAlertRule {

        @Test
        @DisplayName("should enable the alert rule and return the response")
        void shouldEnableAlertRule_andReturnResponse() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);

            AlertRule alertRule = AlertRule.builder()
                    .id(alertRuleId)
                    .location(location)
                    .disasterType(DisasterType.EARTHQUAKE)
                    .enabled(false)
                    .build();

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.of(alertRule));
            given(alertRuleRepository.save(alertRule))
                    .willReturn(alertRule);

            AlertRuleResponse result = alertRuleService.enableAlertRule(alertRuleId, userId);

            assertThat(result).isNotNull();
            assertThat(result.enabled()).isTrue();
        }

        @Test
        @DisplayName("should throw AlertRuleNotFoundException when alert rule does not exist for user")
        void shouldThrowAlertRuleNotFoundException_whenAlertRuleDoesNotExist() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertRuleService.enableAlertRule(alertRuleId, userId))
                    .isInstanceOf(AlertRuleNotFoundException.class)
                    .hasMessageContaining(alertRuleId.toString());

            then(alertRuleRepository).should(never()).save(any(AlertRule.class));
        }
    }



    @Nested
    @DisplayName("disableAlertRule")
    class DisableAlertRule {

        @Test
        @DisplayName("should disable the alert rule and return the response")
        void shouldDisableAlertRule_andReturnResponse() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule alertRule = buildAlertRule(alertRuleId, location);

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.of(alertRule));
            given(alertRuleRepository.save(alertRule))
                    .willReturn(alertRule);

            AlertRuleResponse result = alertRuleService.disableAlertRule(alertRuleId, userId);

            assertThat(result).isNotNull();
            assertThat(result.enabled()).isFalse();
        }

        @Test
        @DisplayName("should throw AlertRuleNotFoundException when alert rule does not exist for user")
        void shouldThrowAlertRuleNotFoundException_whenAlertRuleDoesNotExist() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertRuleService.disableAlertRule(alertRuleId, userId))
                    .isInstanceOf(AlertRuleNotFoundException.class)
                    .hasMessageContaining(alertRuleId.toString());

            then(alertRuleRepository).should(never()).save(any(AlertRule.class));
        }
    }



    @Nested
    @DisplayName("deleteAlertRule")
    class DeleteAlertRule {

        @Test
        @DisplayName("should delete the alert rule when it exists for user")
        void shouldDeleteAlertRule_whenItExistsForUser() {

            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();
            User user = buildUser(userId);
            Location location = buildLocation(UUID.randomUUID(), user);
            AlertRule alertRule = buildAlertRule(alertRuleId, location);

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.of(alertRule));

            alertRuleService.deleteAlertRule(alertRuleId, userId);

            then(alertRuleRepository).should().delete(alertRule);
        }

        @Test
        @DisplayName("should throw AlertRuleNotFoundException when alert rule does not exist for user")
        void shouldThrowAlertRuleNotFoundException_whenAlertRuleDoesNotExist() {
            UUID userId = UUID.randomUUID();
            UUID alertRuleId = UUID.randomUUID();

            given(alertRuleRepository.findByIdAndLocationUserId(alertRuleId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alertRuleService.deleteAlertRule(alertRuleId, userId))
                    .isInstanceOf(AlertRuleNotFoundException.class)
                    .hasMessageContaining(alertRuleId.toString());

            then(alertRuleRepository).should(never()).delete(any(AlertRule.class));
        }
    }
}
