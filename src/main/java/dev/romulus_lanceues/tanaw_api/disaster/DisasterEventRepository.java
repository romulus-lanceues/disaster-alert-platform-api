package dev.romulus_lanceues.tanaw_api.disaster;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisasterEventRepository extends JpaRepository<DisasterEvent, UUID>, JpaSpecificationExecutor<DisasterEvent> {

    Optional<DisasterEvent> findBySourceAndExternalId(String source, String externalId);

    boolean existsBySourceAndExternalId(String source, String externalId);

    Page<DisasterEvent> findByDisasterTypeOrderByOccurredAtDesc(DisasterType disasterType, Pageable pageable);

    List<DisasterEvent> findByOccurredAtBetweenOrderByOccurredAtDesc(Instant start, Instant end);

    Page<DisasterEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Optional<DisasterEvent> findTopByDisasterTypeOrderByOccurredAtDesc(DisasterType disasterType);
}
