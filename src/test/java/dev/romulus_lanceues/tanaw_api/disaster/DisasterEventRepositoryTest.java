package dev.romulus_lanceues.tanaw_api.disaster;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
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

    private DisasterEvent primaryDisasterEvent;

    @BeforeEach
    void setUp() {
        primaryDisasterEvent = persistDisasterEvent("USGS", "usgs-event-001",
                DisasterType.EARTHQUAKE, "automatic", "25 km NW of Manila");
    }


    @Nested
    @DisplayName("Entity persistence and constraint")
    class EntityPersistenceAndConstraint{

        @Test
        @DisplayName("should persist disaster event with all fields")
        void shouldPersistDisasterEventWithAllFields() throws Exception {

            Instant occurredAt = Instant.parse("2026-09-12T02:45:00Z");
            Instant sourceUpdatedAt = Instant.parse("2026-09-12T02:50:00Z");

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
                    .sourceUpdatedAt(sourceUpdatedAt)
                    .status("reviewed")
                    .place("012 km N 45° E of Calamba (Laguna)")
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
                assertThat(saved.getSourceUpdatedAt()).isEqualTo(sourceUpdatedAt);
                assertThat(saved.getStatus()).isEqualTo("reviewed");
                assertThat(saved.getPlace()).isEqualTo("012 km N 45° E of Calamba (Laguna)");
                assertThat(saved.getSeverity()).isEqualTo("MODERATE");
                assertThat(saved.getRawPayload()).isNotNull();
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
                    DisasterType.EARTHQUAKE, "automatic", "some place"))
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
                assertThat(saved.getStatus()).isEqualTo("automatic");
                assertThat(saved.getPlace()).isEqualTo("25 km NW of Manila");
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
                    DisasterType.EARTHQUAKE, "reviewed", "Laguna");

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
                    DisasterType.TYPHOON, oldest, "automatic", "Eastern Samar");
            DisasterEvent typhoon2 = persistDisasterEvent("PAGASA", "pagasa-typhoon-002",
                    DisasterType.TYPHOON, middle, "automatic", "Bicol Region");
            DisasterEvent typhoon3 = persistDisasterEvent("PAGASA", "pagasa-typhoon-003",
                    DisasterType.TYPHOON, newest, "automatic", "Aurora");

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
                    DisasterType.EARTHQUAKE, baseTime.minus(2, ChronoUnit.HOURS), "automatic", "Zambales");
            DisasterEvent event1 = persistDisasterEvent("USGS", "event-in-range-1",
                    DisasterType.EARTHQUAKE, baseTime.minus(1, ChronoUnit.HOURS), "reviewed", "Batangas");
            DisasterEvent event2 = persistDisasterEvent("PAGASA", "event-in-range-2",
                    DisasterType.TYPHOON, baseTime, "automatic", "Eastern Samar");
            DisasterEvent event3 = persistDisasterEvent("USGS", "event-in-range-3",
                    DisasterType.EARTHQUAKE, baseTime.plus(1, ChronoUnit.HOURS), "reviewed", "Laguna");
            DisasterEvent afterWindow = persistDisasterEvent("USGS", "event-after-range",
                    DisasterType.EARTHQUAKE, baseTime.plus(2, ChronoUnit.HOURS), "automatic", "Quezon");

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
                    DisasterType.EARTHQUAKE, now.minusSeconds(7200), "automatic", "Batangas");
            DisasterEvent newer = persistDisasterEvent("PAGASA", "all-paged-event-new",
                    DisasterType.TYPHOON, now.plusSeconds(3600), "automatic", "Bicol Region");

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
                    DisasterType.TYPHOON, now.minusSeconds(7200), "automatic", "Eastern Samar");
            DisasterEvent newTyphoon = persistDisasterEvent("PAGASA", "typhoon-top-new",
                    DisasterType.TYPHOON, now.minusSeconds(1800), "automatic", "Bicol Region");

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


    private DisasterEvent persistDisasterEvent(String source, String externalId,
                                               DisasterType disasterType, Instant occurredAt,
                                               String status, String place) {

        DisasterEvent disasterEvent = DisasterEvent.builder()
                .source(source)
                .externalId(externalId)
                .disasterType(disasterType)
                .occurredAt(occurredAt != null ? occurredAt : Instant.now())
                .sourceUpdatedAt(occurredAt != null ? occurredAt : Instant.now())
                .status(status)
                .place(place)
                .rawPayload(null)
                .build();

        return disasterEventRepository.saveAndFlush(disasterEvent);
    }

    private DisasterEvent persistDisasterEvent(String source, String externalId,
                                               DisasterType disasterType, String status,
                                               String place) {
        return persistDisasterEvent(source, externalId, disasterType, Instant.now(), status, place);
    }


}
