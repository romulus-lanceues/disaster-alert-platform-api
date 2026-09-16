package dev.romulus_lanceues.tanaw_api.disaster;

import dev.romulus_lanceues.tanaw_api.location.GeoPointFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisasterEventService {

    private final DisasterEventRepository disasterEventRepository;
    private final GeoPointFactory geoPointFactory;

    @Transactional
    public DisasterEvent createDisasterEvent(DisasterEventRequest request) {
        log.info("Creating disaster event from source '{}' with external ID '{}'",
                request.source(), request.externalId());

        if (disasterEventRepository.existsBySourceAndExternalId(request.source(), request.externalId())) {
            throw new DisasterEventAlreadyExistsException(
                    "Disaster event already exists for source '%s' with external ID '%s'"
                            .formatted(request.source(), request.externalId()));
        }

        Point location = (request.latitude() != null && request.longitude() != null)
                ? geoPointFactory.create(request.latitude(), request.longitude())
                : null;

        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : Instant.now();

        DisasterEvent disasterEvent = DisasterEvent.builder()
                .source(request.source())
                .externalId(request.externalId())
                .disasterType(request.disasterType())
                .occurredAt(occurredAt)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .location(location)
                .magnitude(request.magnitude())
                .depthKm(request.depthKm())
                .severity(request.severity())
                .rawPayload(request.rawPayload())
                .build();

        return disasterEventRepository.save(disasterEvent);
    }

    public DisasterEvent getDisasterEvent(UUID id) {
        log.info("Fetching disaster event with ID {}", id);

        return disasterEventRepository.findById(id)
                .orElseThrow(() -> new DisasterEventNotFoundException(
                        "Disaster event not found: " + id));
    }

    public DisasterEvent getDisasterEventBySourceAndExternalId(String source, String externalId) {
        log.info("Fetching disaster event for source '{}' with external ID '{}'", source, externalId);

        return disasterEventRepository.findBySourceAndExternalId(source, externalId)
                .orElseThrow(() -> new DisasterEventNotFoundException(
                        "Disaster event not found for source: " + source + ", externalId: " + externalId));
    }

    public Page<DisasterEvent> getDisasterEvents(Pageable pageable) {
        log.info("Fetching paged disaster events, page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        return disasterEventRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    public Page<DisasterEvent> getDisasterEventsByType(DisasterType disasterType, Pageable pageable) {
        log.info("Fetching paged disaster events for type {}, page: {}, size: {}",
                disasterType, pageable.getPageNumber(), pageable.getPageSize());

        return disasterEventRepository.findByDisasterTypeOrderByOccurredAtDesc(disasterType, pageable);
    }

    public Optional<DisasterEvent> getLatestDisasterEventByType(DisasterType disasterType) {
        log.info("Fetching latest disaster event for type {}", disasterType);

        return disasterEventRepository.findTopByDisasterTypeOrderByOccurredAtDesc(disasterType);
    }

    public List<DisasterEvent> getDisasterEventsWithinRadius(double latitude, double longitude, double radiusInKm) {
        log.info("Fetching disaster events within {} km of ({}, {})", radiusInKm, latitude, longitude);

        return disasterEventRepository.findWithinRadius(latitude, longitude, radiusInKm);
    }

    public List<DisasterEvent> getDisasterEventsBetween(Instant start, Instant end) {
        log.info("Fetching disaster events occurred between {} and {}", start, end);

        return disasterEventRepository.findByOccurredAtBetweenOrderByOccurredAtDesc(start, end);
    }
}
