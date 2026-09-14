package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.enums.DisasterType;
import dev.romulus_lanceues.tanaw_api.enums.GeographicAreaType;
import dev.romulus_lanceues.tanaw_api.enums.UserStatus;
import dev.romulus_lanceues.tanaw_api.jts.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.location.GeographicArea;
import dev.romulus_lanceues.tanaw_api.location.Location;
import dev.romulus_lanceues.tanaw_api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
@DisplayName("AlertRuleRepository Tests")
public class AlertRuleRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final GeoPointFactory geoPointFactory = new GeoPointFactory();

    private User primaryUser;
    private GeographicArea primaryGeographicArea;
    private Location primaryLocation;

    @BeforeEach
    void setup(){
        primaryUser = persistUser("alice@example.com", "hash_alice_123");
        primaryGeographicArea = persistGeographicArea("137600000","City of Manila", GeographicAreaType.MUNICIPALITY);
        primaryLocation = persistLocation(primaryUser,"House", "123 Main St.",14.5500, 121.0300, primaryGeographicArea);
    }

    @Nested
    @DisplayName("Entity persistence and relationship")
    class PersistenceAndRelationship {

        @Test
        @DisplayName("should persist alert rule and with all fields")
        void shouldPersistAlertRule(){
            AlertRule alertRule = AlertRule.builder()
                    .location(primaryLocation)
                    .disasterType(DisasterType.EARTHQUAKE)
                    .enabled(true)
                    .minimumMagnitude(5.0)
                    .radiusKm(5.0)
                    .build();

            AlertRule saved = alertRuleRepository.saveAndFlush(alertRule);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getLocation()).isEqualTo(primaryLocation);
            assertThat(saved.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
            assertThat(saved.isEnabled()).isTrue();
            assertThat(saved.getMinimumMagnitude()).isEqualTo(5.0);
            assertThat(saved.getRadiusKm()).isEqualTo(5.0);
            assertThat(saved.getMinimumSeverity()).isNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should persist disabling and enabling a rule")
        void shouldDisableAndEnableAlertRule(){
            AlertRule alertRule = persistAlertRule(primaryLocation, DisasterType.TYPHOON, null, 10.0);

            alertRule.disable();
            alertRuleRepository.saveAndFlush(alertRule);

            entityManager.clear();

            Optional<AlertRule> disabled = alertRuleRepository.findById(alertRule.getId());

            assertThat(disabled)
                    .isPresent()
                    .hasValueSatisfying(retrievedRule -> assertThat(retrievedRule.isEnabled()).isFalse());

            AlertRule disabledAlertRule = disabled.get();

            disabledAlertRule.enable();

            alertRuleRepository.saveAndFlush(disabledAlertRule);

            entityManager.clear();

            Optional<AlertRule> enabled = alertRuleRepository.findById(disabledAlertRule.getId());

            assertThat(enabled)
                    .isPresent()
                    .hasValueSatisfying( retrievedValue -> assertThat(retrievedValue.isEnabled()).isTrue());
        }

        @Test
        @DisplayName("should persist with correct parent value")
        void shouldPersistAlertRuleWithCorrectParentRelationship(){
            AlertRule alertRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 6.0, 10.0);

            entityManager.clear();

            Optional<AlertRule> fetched = alertRuleRepository.findById(alertRule.getId());

            assertThat(fetched).isPresent().hasValueSatisfying(retrievedAlertRule -> {
                assertThat(retrievedAlertRule.getLocation()).isNotNull();
                assertThat(retrievedAlertRule.getLocation().getId()).isEqualTo(primaryLocation.getId());
                assertThat(retrievedAlertRule.getLocation().getName()).isEqualTo("House");
            });

        }

        @Test
        @DisplayName("should update thresholds correctly")
        void shouldUpdateThresholds() {
            AlertRule alertRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 10.0);

            alertRule.updateThresholds(6.5, 25.0, "SEVERE");
            alertRuleRepository.saveAndFlush(alertRule);

            entityManager.clear();

            Optional<AlertRule> updated = alertRuleRepository.findById(alertRule.getId());

            assertThat(updated).isPresent().hasValueSatisfying(retrievedRule -> {
                assertThat(retrievedRule.getMinimumMagnitude()).isEqualTo(6.5);
                assertThat(retrievedRule.getRadiusKm()).isEqualTo(25.0);
                assertThat(retrievedRule.getMinimumSeverity()).isEqualTo("SEVERE");
            });
        }

    }

    @Nested
    @DisplayName("findByLocationId")
    class FindByLocationId{

        @Test
        @DisplayName("should find all alert rules with the same location id")
        void shouldFindAllAlertRuleByLocationId(){

            GeographicArea secondArea = persistGeographicArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY);
            Location secondLocation = persistLocation(primaryUser, "QC House", "Balete Drive", 15.001, 12.0021, secondArea);

            persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.5, 4.5);
            persistAlertRule(primaryLocation, DisasterType.TYPHOON, null, 5.0);
            persistAlertRule(secondLocation, DisasterType.EARTHQUAKE, 4.0, 5.0);


            entityManager.clear();

            List<AlertRule> mainLocationAlertRules = alertRuleRepository.findByLocationId(primaryLocation.getId());
            List<AlertRule> secondLocationAlertRule = alertRuleRepository.findByLocationId(secondLocation.getId());

            assertThat(mainLocationAlertRules)
                    .hasSize(2)
                    .extracting(AlertRule::getMinimumMagnitude).containsExactlyInAnyOrder(5.5, null);
            assertThat(mainLocationAlertRules)
                    .extracting(AlertRule::getDisasterType).containsExactlyInAnyOrder(DisasterType.TYPHOON, DisasterType.EARTHQUAKE);

            assertThat(secondLocationAlertRule)
                    .hasSize(1)
                    .extracting(AlertRule::getMinimumMagnitude).contains(4.0);
        }

        @Test
        @DisplayName("should return empty list if there's no alert rule matching the location id")
        void shouldReturnEmptyListWhenNoAlertRuleForLocationId(){
            persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.5, 4.5);

            entityManager.clear();

            List<AlertRule> fetched = alertRuleRepository.findByLocationId(UUID.randomUUID());

            assertThat(fetched).isEmpty();
        }


    }

    @Nested
    @DisplayName("findByLocationUserId")
    class FindByLocationUserId{

        @Test
        @DisplayName("should find all alert rules with the same location user id")
        void shouldFindAllAlertRuleByLocationUserId(){
            User secondUser = persistUser("bob@example.com", "hash_bob_456");
            GeographicArea secondArea = persistGeographicArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY);
            Location secondLocation = persistLocation(secondUser, "QC House", "Balete Drive", 15.001, 12.0021, secondArea);


            persistAlertRule(primaryLocation, DisasterType.TYPHOON, null, 10.0);
            persistAlertRule(secondLocation, DisasterType.EARTHQUAKE, 4.0, 5.0);
            persistAlertRule(secondLocation, DisasterType.EARTHQUAKE, 7.0, 8.0);
            persistAlertRule(secondLocation, DisasterType.TYPHOON, null, 6.0);

            entityManager.clear();

            List<AlertRule> user1AlertRules = alertRuleRepository.findByLocationUserId(primaryUser.getId());
            List<AlertRule> user2AlertRules = alertRuleRepository.findByLocationUserId(secondUser.getId());

            assertThat(user1AlertRules).hasSize(1).extracting(AlertRule::getDisasterType).contains(DisasterType.TYPHOON);

            assertThat(user2AlertRules).hasSize(3).extracting(AlertRule::getDisasterType).contains(DisasterType.TYPHOON, DisasterType.EARTHQUAKE);

        }

        @Test
        @DisplayName("should return empty list if there's no alert rule matching the user id")
        void shouldReturnEmptyListWhenNoAlertRuleForLocationUserId(){
            List<AlertRule> fetched = alertRuleRepository.findByLocationUserId(UUID.randomUUID());

            assertThat(fetched).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdAndLocationUserId")
    class FindByIdAndLocationUserId{

        @Test
        @DisplayName("should return the alert rule that has the same id and location user id")
        void shouldReturnAlertRuleWithSameIDAndLocationUserId(){
            AlertRule alertRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 6.0);

            entityManager.clear();

            Optional<AlertRule> found = alertRuleRepository.findByIdAndLocationUserId(alertRule.getId(), primaryUser.getId());

            assertThat(found).isPresent().hasValueSatisfying( foundAlertRules -> {
                assertThat(foundAlertRules.getId()).isEqualTo(alertRule.getId());
                assertThat(foundAlertRules.getLocation().getId()).isEqualTo(primaryLocation.getId());
                assertThat(foundAlertRules.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
                assertThat(foundAlertRules.getMinimumMagnitude()).isEqualTo(5.0);
                assertThat(foundAlertRules.getRadiusKm()).isEqualTo(6.0);
            });

        }

        @Test
        @DisplayName("should return empty when alert rule belongs to different user")
        void shouldReturnEmptyWhenAlertRuleBelongsToDifferentUser(){
            User secondUser = persistUser("bob@example.com", "hash_bob_456");
            AlertRule alertRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 6.0);

            entityManager.clear();

            Optional<AlertRule> found = alertRuleRepository.findByIdAndLocationUserId(alertRule.getId(), secondUser.getId());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should return empty optional value")
        void shouldReturnEmptyOptionalValue(){

            Optional<AlertRule> found = alertRuleRepository.findByIdAndLocationUserId(UUID.randomUUID(), primaryUser.getId());

            assertThat(found).isEmpty();

        }
    }

    @Nested
    @DisplayName("findByEnabledTrueAndDisasterType")
    class FindByEnabledTrueAndDisasterType {

        @Test
        @DisplayName("should return only enabled alert rules matching the specified disaster type")
        void shouldReturnOnlyEnabledRulesMatchingDisasterType() {
            AlertRule enabledQuake1 = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 10.0);
            AlertRule enabledQuake2 = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 6.0, 20.0);

            AlertRule disabledQuake = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 4.0, 5.0);
            disabledQuake.disable();
            alertRuleRepository.saveAndFlush(disabledQuake);

            AlertRule enabledTyphoon = persistAlertRule(primaryLocation, DisasterType.TYPHOON, null, 15.0);

            entityManager.clear();

            List<AlertRule> earthquakeRules = alertRuleRepository.findByEnabledTrueAndDisasterType(DisasterType.EARTHQUAKE);

            assertThat(earthquakeRules)
                    .hasSize(2)
                    .extracting(AlertRule::getId)
                    .containsExactlyInAnyOrder(enabledQuake1.getId(), enabledQuake2.getId());
            assertThat(earthquakeRules).allMatch(AlertRule::isEnabled);
            assertThat(earthquakeRules).allMatch(rule -> rule.getDisasterType() == DisasterType.EARTHQUAKE);
        }

        @Test
        @DisplayName("should return empty list when no rules match the disaster type")
        void shouldReturnEmptyListWhenNoRulesMatchDisasterType() {
            persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 10.0);

            entityManager.clear();

            List<AlertRule> typhoonRules = alertRuleRepository.findByEnabledTrueAndDisasterType(DisasterType.TYPHOON);

            assertThat(typhoonRules).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when matching disaster type rules are all disabled")
        void shouldReturnEmptyListWhenMatchingRulesAreAllDisabled() {
            AlertRule disabledQuake = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 10.0);
            disabledQuake.disable();
            alertRuleRepository.saveAndFlush(disabledQuake);

            entityManager.clear();

            List<AlertRule> earthquakeRules = alertRuleRepository.findByEnabledTrueAndDisasterType(DisasterType.EARTHQUAKE);

            assertThat(earthquakeRules).isEmpty();
        }
    }

    @Nested
    @DisplayName("findMatchingRules")
    class FindMatchingRules {

        @Test
        @DisplayName("should match rules within radius and exclude rules outside radius")
        void shouldMatchRulesWithinRadiusAndExcludeRulesOutsideRadius() {
            // primaryLocation is at (14.5500, 121.0300) [Makati area]
            GeographicArea cebuArea = persistGeographicArea("072217000", "Cebu City", GeographicAreaType.MUNICIPALITY);
            Location cebuLocation = persistLocation(primaryUser, "Cebu Office", "Colon St", 10.3157, 123.8854, cebuArea);

            // Rule 1: at Makati with radius 15.0 km
            AlertRule makatiRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 15.0);
            // Rule 2: at Cebu with radius 15.0 km
            AlertRule cebuRule = persistAlertRule(cebuLocation, DisasterType.EARTHQUAKE, 5.0, 15.0);

            entityManager.clear();

            // Disaster at Manila City Hall (14.5895, 120.9815), ~6.8 km from Makati, ~570 km from Cebu
            double disasterLat = 14.5895;
            double disasterLon = 120.9815;
            double disasterMagnitude = 6.0;

            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.EARTHQUAKE,
                    disasterLat,
                    disasterLon,
                    disasterMagnitude
            );

            assertThat(matchedRules)
                    .hasSize(1)
                    .extracting(AlertRule::getId)
                    .containsExactly(makatiRule.getId());
        }

        @Test
        @DisplayName("should filter by radius distance when rules on same location have different radiuses")
        void shouldFilterByRadiusDistanceForSameLocation() {
            // Disaster at Manila City Hall (14.5895, 120.9815), approx 6.8 km from primaryLocation (14.5500, 121.0300)
            AlertRule smallRadiusRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 5.0);
            AlertRule largeRadiusRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 10.0);

            entityManager.clear();

            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.EARTHQUAKE,
                    14.5895,
                    120.9815,
                    6.0
            );

            assertThat(matchedRules)
                    .hasSize(1)
                    .extracting(AlertRule::getId)
                    .containsExactly(largeRadiusRule.getId());
        }

        @Test
        @DisplayName("should filter by minimum magnitude threshold")
        void shouldFilterByMinimumMagnitudeThreshold() {
            // Rules at primaryLocation with 50 km radius
            AlertRule lowMinMagRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 4.0, 50.0);
            AlertRule exactMinMagRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.5, 50.0);
            AlertRule highMinMagRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 6.5, 50.0);

            entityManager.clear();

            // Disaster at primaryLocation with magnitude 5.5
            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.EARTHQUAKE,
                    14.5500,
                    121.0300,
                    5.5
            );

            assertThat(matchedRules)
                    .hasSize(2)
                    .extracting(AlertRule::getId)
                    .containsExactlyInAnyOrder(lowMinMagRule.getId(), exactMinMagRule.getId());
        }

        @Test
        @DisplayName("should match rule when rule has null minimum magnitude regardless of disaster magnitude")
        void shouldMatchWhenRuleHasNullMinimumMagnitude() {
            AlertRule noMinMagRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, null, 50.0);

            entityManager.clear();

            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.EARTHQUAKE,
                    14.5500,
                    121.0300,
                    2.0
            );

            assertThat(matchedRules)
                    .hasSize(1)
                    .extracting(AlertRule::getId)
                    .containsExactly(noMinMagRule.getId());
        }

        @Test
        @DisplayName("should match rules when disaster event magnitude is null")
        void shouldMatchWhenDisasterMagnitudeIsNull() {
            AlertRule ruleWithMinMag = persistAlertRule(primaryLocation, DisasterType.TYPHOON, null, 50.0);
            AlertRule ruleWithoutMinMag = persistAlertRule(primaryLocation, DisasterType.TYPHOON, null, 50.0);

            entityManager.clear();

            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.TYPHOON,
                    14.5500,
                    121.0300,
                    null
            );

            assertThat(matchedRules)
                    .hasSize(2)
                    .extracting(AlertRule::getId)
                    .containsExactlyInAnyOrder(ruleWithMinMag.getId(), ruleWithoutMinMag.getId());
        }

        @Test
        @DisplayName("should match rule when rule has null radius regardless of distance")
        void shouldMatchWhenRuleHasNullRadiusRegardlessOfDistance() {
            // Rule at primaryLocation (Makati) with no radius constraint (null)
            AlertRule globalRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, null);

            entityManager.clear();

            // Disaster event in Cebu City (~570 km away)
            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.EARTHQUAKE,
                    10.3157,
                    123.8854,
                    6.0
            );

            assertThat(matchedRules)
                    .hasSize(1)
                    .extracting(AlertRule::getId)
                    .containsExactly(globalRule.getId());
        }

        @Test
        @DisplayName("should exclude disabled rules even if within radius and magnitude matches")
        void shouldExcludeDisabledRules() {
            AlertRule enabledRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
            AlertRule disabledRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);
            disabledRule.disable();
            alertRuleRepository.saveAndFlush(disabledRule);

            entityManager.clear();

            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.EARTHQUAKE,
                    14.5500,
                    121.0300,
                    6.0
            );

            assertThat(matchedRules)
                    .hasSize(1)
                    .extracting(AlertRule::getId)
                    .containsExactly(enabledRule.getId());
        }

        @Test
        @DisplayName("should exclude rules with different disaster type")
        void shouldExcludeRulesWithDifferentDisasterType() {
            AlertRule typhoonRule = persistAlertRule(primaryLocation, DisasterType.TYPHOON, null, 50.0);

            entityManager.clear();

            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.EARTHQUAKE,
                    14.5500,
                    121.0300,
                    6.0
            );

            assertThat(matchedRules).isEmpty();
        }

        @Test
        @DisplayName("should support querying by String disaster type")
        void shouldSupportQueryingByStringDisasterType() {
            AlertRule earthquakeRule = persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 50.0);

            entityManager.clear();

            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    "EARTHQUAKE",
                    14.5500,
                    121.0300,
                    6.0
            );

            assertThat(matchedRules)
                    .hasSize(1)
                    .extracting(AlertRule::getId)
                    .containsExactly(earthquakeRule.getId());
        }

        @Test
        @DisplayName("should return empty list when no rules match coordinates")
        void shouldReturnEmptyListWhenNoRulesMatchCoordinates() {
            persistAlertRule(primaryLocation, DisasterType.EARTHQUAKE, 5.0, 10.0);

            entityManager.clear();

            // Point (0.0, 0.0) in the Atlantic Ocean, thousands of km away
            List<AlertRule> matchedRules = alertRuleRepository.findMatchingRules(
                    DisasterType.EARTHQUAKE,
                    0.0,
                    0.0,
                    7.0
            );

            assertThat(matchedRules).isEmpty();
        }
    }




    private User persistUser(String email, String hashedPassword){
        User user = User.builder()
                .email(email)
                .passwordHash(hashedPassword)
                .status(UserStatus.ACTIVE)
                .build();

        return entityManager.persistAndFlush(user);
    }

    private GeographicArea persistGeographicArea(String psgcCode, String name, GeographicAreaType type){
        GeographicArea geographicArea = GeographicArea.builder()
                .psgcCode(psgcCode)
                .name(name)
                .type(type)
                .active(true)
                .build();

        return entityManager.persistAndFlush(geographicArea);
    }

    private Location persistLocation(User user, String name, String address, double latitude, double longitude, GeographicArea geographicArea){
        Location location = Location.create(
                user,
                name,
                address,
                geographicArea,
                latitude,
                longitude,
                geoPointFactory);

        return entityManager.persistAndFlush(location);
    }

    private AlertRule persistAlertRule(Location location, DisasterType disasterType, Double minimumMagnitude, Double radiusKm){
        AlertRule alertRule = AlertRule.builder()
                .location(location)
                .disasterType(disasterType)
                .enabled(true)
                .minimumMagnitude(minimumMagnitude)
                .radiusKm(radiusKm)
                .minimumSeverity("MODERATE")
                .build();

        return alertRuleRepository.saveAndFlush(alertRule);
    }

}
