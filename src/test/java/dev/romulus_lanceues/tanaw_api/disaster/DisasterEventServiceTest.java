package dev.romulus_lanceues.tanaw_api.disaster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisasterEventService Tests")
class DisasterEventServiceTest {

    @Mock
    private DisasterEventRepository disasterEventRepository;

    @InjectMocks
    private DisasterEventService disasterEventService;


    private DisasterEvent buildDisasterEvent(UUID id, String source, String externalId,
                                             DisasterType disasterType) {
        return DisasterEvent.builder()
                .id(id)
                .source(source)
                .externalId(externalId)
                .disasterType(disasterType)
                .occurredAt(Instant.now())
                .sourceUpdatedAt(Instant.now())
                .status("automatic")
                .place("25 km NW of Manila, Philippines")
                .severity("HIGH")
                .build();
    }


    @Nested
    @DisplayName("getDisasterEvent")
    class GetDisasterEvent {

        @Test
        @DisplayName("should return disaster event when found by id")
        void shouldReturnDisasterEvent_whenFoundById() {
            UUID id = UUID.randomUUID();
            DisasterEvent event = buildDisasterEvent(id, "PHIVOLCS", "phi-01",
                    DisasterType.EARTHQUAKE);

            given(disasterEventRepository.findById(id)).willReturn(Optional.of(event));

            DisasterEvent result = disasterEventService.getDisasterEvent(id);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("should throw DisasterEventNotFoundException when not found by id")
        void shouldThrowDisasterEventNotFoundException_whenNotFoundById() {

            UUID id = UUID.randomUUID();
            given(disasterEventRepository.findById(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> disasterEventService.getDisasterEvent(id))
                    .isInstanceOf(DisasterEventNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }



    @Nested
    @DisplayName("getDisasterEventBySourceAndExternalId")
    class GetDisasterEventBySourceAndExternalId {

        @Test
        @DisplayName("should return disaster event when found by source and externalId")
        void shouldReturnDisasterEvent_whenFoundBySourceAndExternalId() {

            DisasterEvent event = buildDisasterEvent(UUID.randomUUID(), "USGS", "ext-1",
                    DisasterType.EARTHQUAKE);

            given(disasterEventRepository.findBySourceAndExternalId("USGS", "ext-1"))
                    .willReturn(Optional.of(event));

            DisasterEvent result = disasterEventService.getDisasterEventBySourceAndExternalId("USGS", "ext-1");

            assertThat(result).isNotNull();
            assertThat(result.getSource()).isEqualTo("USGS");
            assertThat(result.getExternalId()).isEqualTo("ext-1");
        }

        @Test
        @DisplayName("should throw DisasterEventNotFoundException when not found by source and externalId")
        void shouldThrowDisasterEventNotFoundException_whenNotFoundBySourceAndExternalId() {

            given(disasterEventRepository.findBySourceAndExternalId("USGS", "unknown"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> disasterEventService.getDisasterEventBySourceAndExternalId("USGS", "unknown"))
                    .isInstanceOf(DisasterEventNotFoundException.class)
                    .hasMessageContaining("USGS")
                    .hasMessageContaining("unknown");
        }
    }


    @Nested
    @DisplayName("getDisasterEvents")
    class GetDisasterEvents {

        @Test
        @DisplayName("should return paged disaster events")
        void shouldReturnPagedDisasterEvents() {

            Pageable pageable = PageRequest.of(0, 10);
            List<DisasterEvent> list = List.of(
                    buildDisasterEvent(UUID.randomUUID(), "USGS", "1", DisasterType.EARTHQUAKE),
                    buildDisasterEvent(UUID.randomUUID(), "PAGASA", "2", DisasterType.TYPHOON)
            );
            Page<DisasterEvent> page = new PageImpl<>(list, pageable, 2);

            given(disasterEventRepository.findAllByOrderByOccurredAtDesc(pageable)).willReturn(page);

            Page<DisasterEvent> result = disasterEventService.getDisasterEvents(pageable);

            assertThat(result).hasSize(2);
            assertThat(result.getContent()).isEqualTo(list);
        }
    }


    @Nested
    @DisplayName("getDisasterEventsByType")
    class GetDisasterEventsByType {

        @Test
        @DisplayName("should return paged disaster events filtered by disaster type")
        void shouldReturnPagedDisasterEvents_filteredByType() {

            Pageable pageable = PageRequest.of(0, 5);
            List<DisasterEvent> earthquakes = List.of(
                    buildDisasterEvent(UUID.randomUUID(), "USGS", "1", DisasterType.EARTHQUAKE)
            );
            Page<DisasterEvent> page = new PageImpl<>(earthquakes, pageable, 1);

            given(disasterEventRepository.findByDisasterTypeOrderByOccurredAtDesc(DisasterType.EARTHQUAKE, pageable))
                    .willReturn(page);

            Page<DisasterEvent> result = disasterEventService.getDisasterEventsByType(DisasterType.EARTHQUAKE, pageable);

            assertThat(result).hasSize(1);
            assertThat(result.getContent().getFirst().getDisasterType()).isEqualTo(DisasterType.EARTHQUAKE);
        }
    }


    @Nested
    @DisplayName("getLatestDisasterEventByType")
    class GetLatestDisasterEventByType {

        @Test
        @DisplayName("should return latest disaster event when exists")
        void shouldReturnLatestDisasterEvent_whenExists() {

            DisasterEvent latest = buildDisasterEvent(UUID.randomUUID(), "USGS", "latest-1",
                    DisasterType.EARTHQUAKE);

            given(disasterEventRepository.findTopByDisasterTypeOrderByOccurredAtDesc(DisasterType.EARTHQUAKE))
                    .willReturn(Optional.of(latest));

            Optional<DisasterEvent> result = disasterEventService.getLatestDisasterEventByType(DisasterType.EARTHQUAKE);

            assertThat(result).isPresent().contains(latest);
        }

        @Test
        @DisplayName("should return empty optional when no disaster event exists for type")
        void shouldReturnEmptyOptional_whenNoneExists() {

            given(disasterEventRepository.findTopByDisasterTypeOrderByOccurredAtDesc(DisasterType.TYPHOON))
                    .willReturn(Optional.empty());

            Optional<DisasterEvent> result = disasterEventService.getLatestDisasterEventByType(DisasterType.TYPHOON);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDisasterEventsBetween")
    class GetDisasterEventsBetween {

        @Test
        @DisplayName("should return disaster events occurred within time window")
        void shouldReturnDisasterEvents_withinTimeWindow() {

            Instant start = Instant.now().minusSeconds(3600);
            Instant end = Instant.now();
            List<DisasterEvent> events = List.of(
                    buildDisasterEvent(UUID.randomUUID(), "USGS", "t1", DisasterType.EARTHQUAKE)
            );

            given(disasterEventRepository.findByOccurredAtBetweenOrderByOccurredAtDesc(start, end))
                    .willReturn(events);

            List<DisasterEvent> result = disasterEventService.getDisasterEventsBetween(start, end);

            assertThat(result).hasSize(1);
            assertThat(result).isEqualTo(events);
        }
    }
}
