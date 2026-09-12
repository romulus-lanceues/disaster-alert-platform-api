package dev.romulus_lanceues.tanaw_api;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEvent;
import dev.romulus_lanceues.tanaw_api.disaster.DisasterEventRepository;
import dev.romulus_lanceues.tanaw_api.enums.DisasterType;
import dev.romulus_lanceues.tanaw_api.jts.GeoPointFactory;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
public class DisasterEventRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private DisasterEventRepository disasterEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeoPointFactory geoPointFactory = new GeoPointFactory();

    private DisasterEvent primaryDisasterEvent;

    @BeforeEach
    void setUp() {
        primaryDisasterEvent = persistDisasterEvent("USGS", "usgs-event-001",
                DisasterType.EARTHQUAKE, 14.5995, 120.9842, 5.2);
    }


    @Nested
    @DisplayName("Entity persistence and constraint")
    class EntityPersistenceAndConstraint{

        @Test
        @DisplayName("should persist disaster event with all fields")
        void shouldPersistDisasterEventWithAllFields() throws Exception{

            Instant occurredAt = Instant.parse("2026-09-12T02:45:00Z");

            JsonNode rawPayload = objectMapper.readTree("""
                    {
                        "bulletin_number": "1",
                        "event_id": "phi-event-021",
                        "datetime": "12 Sep 2026 - 10:45 AM",
                        "latitude": 14.28,
                        "longitude": 121.24,
                        "depth_km": 10.0,
                        "magnitude": 5.3,
                        "location": "012 km N 45° E of Calamba (Laguna)",
                        "reported_intensities": {
                            "Intensity V": ["Calamba, Laguna"],
                            "Intensity IV": ["Tagaytay City", "Makati City"],
                            "Intensity III": ["Pasig City", "Quezon City"]
                        },
                        "instrumental_intensities": {
                            "Intensity IV": ["Carmona, Cavite"]
                        },
                        "expecting_damage": false,
                        "expecting_aftershocks": true
                    }
                    """);

            DisasterEvent disasterEvent = DisasterEvent.builder()
                    .source("PHIVOLCS")
                    .externalId("phi-event-021")
                    .disasterType(DisasterType.EARTHQUAKE)
                    .occurredAt(occurredAt)
                    .latitude(14.28)
                    .longitude(121.24)
                    .location(geoPointFactory.create(14.28, 121.24))
                    .magnitude(5.3)
                    .depthKm(10.0)
                    .severity("MODERATE")
                    .rawPayload(rawPayload)
                    .build();

            DisasterEvent savedDisasterEvent = disasterEventRepository.saveAndFlush(disasterEvent);

            entityManager.clear();

            Optional<DisasterEvent> found = disasterEventRepository.findById(savedDisasterEvent.getId());

            assertThat(found).isPresent().hasValueSatisfying( saved -> {
                assertThat(saved.getId()).isNotNull();
                assertThat(saved.getSource()).isEqualTo("PHIVOLCS");
                assertThat(saved.getExternalId()).isEqualTo("phi-event-021");
                assertThat(saved.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
                assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
                assertThat(saved.getLatitude()).isEqualTo(14.28);
                assertThat(saved.getLongitude()).isEqualTo(121.24);
                assertThat(saved.getLocation()).isNotNull();
                assertThat(saved.getLocation().getY()).isEqualTo(14.28);
                assertThat(saved.getLocation().getX()).isEqualTo(121.24);
                assertThat(saved.getMagnitude()).isEqualTo(5.3);
                assertThat(saved.getDepthKm()).isEqualTo(10.0);
                assertThat(saved.getSeverity()).isEqualTo("MODERATE");
                assertThat(saved.getRawPayload()).isEqualTo(rawPayload);
                assertThat(saved.getRawPayload().path("event_id").asString()).isEqualTo("phi-event-021");
                assertThat(saved.getRawPayload().path("reported_intensities").path("Intensity V").get(0).asText())
                        .isEqualTo("Calamba, Laguna");
                assertThat(saved.getCreatedAt()).isNotNull();
            });

        }

        @Test
        @DisplayName("should fail when violating unique constraint on source and external id")
        void shouldFailWhenViolatingUniqueConstraintOnSourceAndExternalId() throws Exception {

            assertThatThrownBy(() -> persistDisasterEvent(
                    primaryDisasterEvent.getSource(),
                    primaryDisasterEvent.getExternalId(),
                    DisasterType.EARTHQUAKE, 14.4, 121.0, 5.0))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_disaster_source_external");

        }

    }

    @Nested
    @DisplayName("findBySourceAndExternalId")
    class FindBySourceAndExternalId {

        @Test
        @DisplayName("should find disaster event using source and external id")
        void shouldFindDisasterEventUsingSourceAndExternalId(){
            entityManager.clear();

            Optional<DisasterEvent> found = disasterEventRepository.findBySourceAndExternalId("USGS", "usgs-event-001");

            assertThat(found).isPresent().hasValueSatisfying( saved -> {
                assertThat(saved.getId()).isEqualTo(primaryDisasterEvent.getId());
                assertThat(saved.getSource()).isEqualTo("USGS");
                assertThat(saved.getExternalId()).isEqualTo("usgs-event-001");
                assertThat(saved.getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
                assertThat(saved.getLatitude()).isEqualTo(14.5995);
                assertThat(saved.getLongitude()).isEqualTo(120.9842);
                assertThat(saved.getLocation()).isNotNull();
                assertThat(saved.getLocation().getY()).isEqualTo(14.5995);
                assertThat(saved.getLocation().getX()).isEqualTo(120.9842);
                assertThat(saved.getMagnitude()).isEqualTo(5.2);
            });
        }

        @Test
        @DisplayName("should return empty when no match")
        void shouldReturnEmptyWhenNoMatch(){
            Optional<DisasterEvent> found = disasterEventRepository.findBySourceAndExternalId("USGS", "non-existent-event");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsBySourceAndExternalId")
    class ExistsBySourceAndExternalId{

        @Test
        @DisplayName("should return true using source and external id")
        void shouldReturnTrueUsingSourceAndExternalId(){
            DisasterEvent disasterEvent = persistDisasterEvent("PHIVOLCS", "phi-event-031",
                    DisasterType.EARTHQUAKE, 14.5995, 120.9842, 5.0 );

            entityManager.clear();

            boolean exists = disasterEventRepository.existsBySourceAndExternalId("PHIVOLCS", "phi-event-031");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false using source and external id")
        void shouldReturnFalseUsingSourceAndExternalId(){
            boolean exists = disasterEventRepository.existsBySourceAndExternalId("PHIVOLCS", "phi-event-031");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByDisasterTypeOrderByOccurredAtDesc")
    class FindDisasterTypeOrderByOccurredAtDesc{

        @Test
        @DisplayName("should find paginated disaster events by type and occurred at on a descending manner")
        void shouldFindPagedDisasterEventsByType(){
            Instant now = Instant.now();
            Instant oldest = now.minusSeconds(7200);
            Instant middle = now.minusSeconds(3600);
            Instant newest = now;

            DisasterEvent typhoon1 = persistDisasterEvent("PAGASA", "pagasa-typhoon-001",
                    DisasterType.TYPHOON, oldest, 11.25, 125.0, null);
            DisasterEvent typhoon2 = persistDisasterEvent("PAGASA", "pagasa-typhoon-002",
                    DisasterType.TYPHOON, middle, 13.5, 123.5, null);
            DisasterEvent typhoon3 = persistDisasterEvent("PAGASA", "pagasa-typhoon-003",
                    DisasterType.TYPHOON, newest, 16.75, 122.0, null);

            entityManager.clear();

            Page<DisasterEvent> page0 = disasterEventRepository
                    .findByDisasterTypeOrderByOccurredAtDesc(DisasterType.TYPHOON, PageRequest.of(0, 2));

            assertThat(page0.getTotalElements()).isEqualTo(3);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.getNumber()).isEqualTo(0);
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getContent())
                    .extracting(DisasterEvent::getId)
                    .containsExactly(typhoon3.getId(), typhoon2.getId());

            Page<DisasterEvent> page1 = disasterEventRepository
                    .findByDisasterTypeOrderByOccurredAtDesc(DisasterType.TYPHOON, PageRequest.of(1, 2));

            assertThat(page1.getNumber()).isEqualTo(1);
            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.getContent())
                    .extracting(DisasterEvent::getId)
                    .containsExactly(typhoon1.getId());
        }

        @Test
        @DisplayName("should return empty page when no disaster events match disaster type")
        void shouldReturnEmptyPageWhenNoDisasterEventsMatchDisasterType(){
            Page<DisasterEvent> page = disasterEventRepository
                    .findByDisasterTypeOrderByOccurredAtDesc(DisasterType.TYPHOON, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByOccurredAtBetweenOrderByOccurredAtDesc")
    class FindByOccurredAtBetweenOrderByOccurredAtDesc {

        @Test
        @DisplayName("should find disaster events within date range ordered by occurred at descending")
        void shouldFindEventsWithinDateRangeOrderedByOccurredAtDesc() {
            Instant baseTime = Instant.parse("2025-06-15T12:00:00Z");

            DisasterEvent beforeWindow = persistDisasterEvent("USGS", "event-before-range",
                    DisasterType.EARTHQUAKE, baseTime.minus(2, ChronoUnit.HOURS), 14.5, 121.0, 4.5);
            DisasterEvent event1 = persistDisasterEvent("USGS", "event-in-range-1",
                    DisasterType.EARTHQUAKE, baseTime.minus(1, ChronoUnit.HOURS), 14.6, 121.1, 5.0);
            DisasterEvent event2 = persistDisasterEvent("PAGASA", "event-in-range-2",
                    DisasterType.TYPHOON, baseTime, 13.0, 124.0, null);
            DisasterEvent event3 = persistDisasterEvent("USGS", "event-in-range-3",
                    DisasterType.EARTHQUAKE, baseTime.plus(1, ChronoUnit.HOURS), 14.7, 121.2, 5.5);
            DisasterEvent afterWindow = persistDisasterEvent("USGS", "event-after-range",
                    DisasterType.EARTHQUAKE, baseTime.plus(2, ChronoUnit.HOURS), 14.8, 121.3, 4.0);

            entityManager.clear();

            Instant start = baseTime.minus(1, ChronoUnit.HOURS);
            Instant end = baseTime.plus(1, ChronoUnit.HOURS);

            List<DisasterEvent> results = disasterEventRepository
                    .findByOccurredAtBetweenOrderByOccurredAtDesc(start, end);

            assertThat(results)
                    .hasSize(3)
                    .extracting(DisasterEvent::getId)
                    .containsExactly(event3.getId(), event2.getId(), event1.getId());
        }

        @Test
        @DisplayName("should return empty list when no disaster events occur within date range")
        void shouldReturnEmptyListWhenNoEventsWithinDateRange() {
            Instant start = Instant.parse("2020-01-01T00:00:00Z");
            Instant end = Instant.parse("2020-01-02T00:00:00Z");

            List<DisasterEvent> results = disasterEventRepository
                    .findByOccurredAtBetweenOrderByOccurredAtDesc(start, end);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByOrderByOccurredAtDesc")
    class FindAllByOrderByOccurredAtDesc {

        @Test
        @DisplayName("should find all disaster events paged and ordered by occurred at descending")
        void shouldFindAllDisasterEventsPagedAndOrderedByOccurredAtDesc() {
            Instant now = Instant.now();
            DisasterEvent older = persistDisasterEvent("USGS", "all-paged-event-old",
                    DisasterType.EARTHQUAKE, now.minusSeconds(7200), 14.5, 121.0, 4.0);
            DisasterEvent newer = persistDisasterEvent("PAGASA", "all-paged-event-new",
                    DisasterType.TYPHOON, now.plusSeconds(3600), 13.0, 124.0, null);

            entityManager.clear();

            Page<DisasterEvent> page0 = disasterEventRepository
                    .findAllByOrderByOccurredAtDesc(PageRequest.of(0, 2));

            assertThat(page0.getTotalElements()).isEqualTo(3);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.getNumber()).isEqualTo(0);
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getContent())
                    .extracting(DisasterEvent::getId)
                    .containsExactly(newer.getId(), primaryDisasterEvent.getId());

            Page<DisasterEvent> page1 = disasterEventRepository
                    .findAllByOrderByOccurredAtDesc(PageRequest.of(1, 2));

            assertThat(page1.getNumber()).isEqualTo(1);
            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.getContent())
                    .extracting(DisasterEvent::getId)
                    .containsExactly(older.getId());
        }

        @Test
        @DisplayName("should return empty content when page index is out of bounds")
        void shouldReturnEmptyContentWhenPageIndexOutOfBounds() {
            Page<DisasterEvent> page = disasterEventRepository
                    .findAllByOrderByOccurredAtDesc(PageRequest.of(50, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findTopByDisasterTypeOrderByOccurredAtDesc")
    class FindTopByDisasterTypeOrderByOccurredAtDesc {

        @Test
        @DisplayName("should find top most recent disaster event for given disaster type")
        void shouldFindMostRecentDisasterEventByType() {
            Instant now = Instant.parse("2026-09-12T12:00:00Z");
            DisasterEvent oldTyphoon = persistDisasterEvent("PAGASA", "typhoon-top-old",
                    DisasterType.TYPHOON, now.minusSeconds(7200), 13.0, 124.0, null);
            DisasterEvent newTyphoon = persistDisasterEvent("PAGASA", "typhoon-top-new",
                    DisasterType.TYPHOON, now.minusSeconds(1800), 14.0, 123.0, null);

            entityManager.clear();

            Optional<DisasterEvent> found = disasterEventRepository
                    .findTopByDisasterTypeOrderByOccurredAtDesc(DisasterType.TYPHOON);

            assertThat(found).isPresent().hasValueSatisfying(event -> {
                assertThat(event.getId()).isEqualTo(newTyphoon.getId());
                assertThat(event.getSource()).isEqualTo("PAGASA");
                assertThat(event.getExternalId()).isEqualTo("typhoon-top-new");
                assertThat(event.getDisasterType()).isEqualTo(DisasterType.TYPHOON);
                assertThat(event.getOccurredAt()).isEqualTo(newTyphoon.getOccurredAt());
            });
        }

        @Test
        @DisplayName("should return empty when no disaster event matches type")
        void shouldReturnEmptyWhenNoDisasterEventMatchesType() {
            Optional<DisasterEvent> found = disasterEventRepository
                    .findTopByDisasterTypeOrderByOccurredAtDesc(DisasterType.TYPHOON);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findWithinRadius")
    class FindWithinRadius {

        @Test
        @DisplayName("should find disaster events within radius ordered by occurred at descending")
        void shouldFindEventsWithinRadiusOrderedByOccurredAtDesc() {
            // Reference center: Cebu City Hall (10.2930, 123.9015)
            double centerLat = 10.2930;
            double centerLon = 123.9015;

            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(3600);
            Instant newerTime = now;

            // Inside radius: Mandaue City (~5.6 km from Cebu City center) - older
            DisasterEvent mandaueEvent = persistDisasterEvent("PHIVOLCS", "quake-cebu-mandaue",
                    DisasterType.EARTHQUAKE, olderTime, 10.3333, 123.9333, 4.2);

            // Inside radius: Talisay City (~7.8 km from Cebu City center) - newer
            DisasterEvent talisayEvent = persistDisasterEvent("PHIVOLCS", "quake-cebu-talisay",
                    DisasterType.EARTHQUAKE, newerTime, 10.2447, 123.8494, 4.8);

            // Outside radius: Tagbilaran, Bohol (~72 km from Cebu City center)
            DisasterEvent boholEvent = persistDisasterEvent("PHIVOLCS", "quake-bohol-tagbilaran",
                    DisasterType.EARTHQUAKE, newerTime, 9.6729, 123.8730, 5.5);

            entityManager.clear();

            List<DisasterEvent> eventsWithin15Km = disasterEventRepository
                    .findWithinRadius(centerLat, centerLon, 15.0);

            assertThat(eventsWithin15Km)
                    .hasSize(2)
                    .extracting(DisasterEvent::getId)
                    .containsExactly(talisayEvent.getId(), mandaueEvent.getId());
        }

        @Test
        @DisplayName("should return empty list when no disaster events fall within radius")
        void shouldReturnEmptyListWhenNoEventsWithinRadius() {
            // Reference center in Davao City (7.1907, 125.4553), with 10 km radius
            List<DisasterEvent> events = disasterEventRepository
                    .findWithinRadius(7.1907, 125.4553, 10.0);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("should respect tighter radius boundary")
        void shouldRespectTighterRadiusBoundary() {
            // Reference center: Cebu City Hall (10.2930, 123.9015)
            double centerLat = 10.2930;
            double centerLon = 123.9015;

            // Mandaue City (~5.6 km from Cebu City center)
            DisasterEvent mandaueEvent = persistDisasterEvent("PHIVOLCS", "quake-mandaue-boundary",
                    DisasterType.EARTHQUAKE, 10.3333, 123.9333, 4.2);

            // Talisay City (~7.8 km from Cebu City center)
            DisasterEvent talisayEvent = persistDisasterEvent("PHIVOLCS", "quake-talisay-boundary",
                    DisasterType.EARTHQUAKE, 10.2447, 123.8494, 4.8);

            entityManager.clear();

            // 6.5 km radius includes Mandaue (~5.6 km) but excludes Talisay (~7.8 km)
            List<DisasterEvent> eventsWithin6Point5Km = disasterEventRepository
                    .findWithinRadius(centerLat, centerLon, 6.5);

            assertThat(eventsWithin6Point5Km)
                    .hasSize(1)
                    .extracting(DisasterEvent::getId)
                    .containsExactly(mandaueEvent.getId());
        }
    }


    private DisasterEvent persistDisasterEvent(String source, String externalId,
                                               DisasterType disasterType, Instant occurredAt,
                                               double latitude, double longitude, Double magnitude) {

        DisasterEvent disasterEvent = DisasterEvent.builder()
                .source(source)
                .externalId(externalId)
                .disasterType(disasterType)
                .occurredAt(occurredAt != null ? occurredAt : Instant.now())
                .latitude(latitude)
                .longitude(longitude)
                .location(geoPointFactory.create(latitude, longitude))
                .magnitude(magnitude)
                .rawPayload(null)
                .build();

        return disasterEventRepository.saveAndFlush(disasterEvent);
    }

    private DisasterEvent persistDisasterEvent(String source, String externalId,
                                               DisasterType disasterType, double latitude,
                                               double longitude, Double magnitude) {
        return persistDisasterEvent(source, externalId, disasterType, Instant.now(), latitude, longitude, magnitude);
    }


}
