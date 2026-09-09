package dev.romulus_lanceues.tanaw_api.alert;

import dev.romulus_lanceues.tanaw_api.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {

    Optional<Alert> findByDisasterEventIdAndAlertRuleId(UUID disasterEventId, UUID alertRuleId);

    boolean existsByDisasterEventIdAndAlertRuleId(UUID disasterEventId, UUID alertRuleId);

    List<Alert> findByDisasterEventId(UUID disasterEventId);

    List<Alert> findByAlertRuleId(UUID alertRuleId);

    List<Alert> findByStatus(AlertStatus status);

    Page<Alert> findByAlertRuleLocationUserId(UUID userId, Pageable pageable);

    Page<Alert> findByAlertRuleLocationUserIdAndStatus(UUID userId, AlertStatus status, Pageable pageable);

    /**
     * Eagerly fetches alert with its disaster event, alert rule, location, and owner user
     * to avoid N+1 queries.
     */
    @Query("""
            SELECT a FROM Alert a
            JOIN FETCH a.disasterEvent
            JOIN FETCH a.alertRule ar
            JOIN FETCH ar.location l
            JOIN FETCH l.user
            WHERE a.id = :id
            """)
    Optional<Alert> findByIdWithDetails(@Param("id") UUID id);
}
