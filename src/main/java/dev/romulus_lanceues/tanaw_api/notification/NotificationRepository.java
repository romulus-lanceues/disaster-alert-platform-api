package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.enums.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.enums.NotificationStatus;
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
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByAlertIdAndChannel(UUID alertId, NotificationChannel channel);

    boolean existsByAlertIdAndChannel(UUID alertId, NotificationChannel channel);

    List<Notification> findByAlertId(UUID alertId);

    List<Notification> findByStatus(NotificationStatus status);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    Page<Notification> findByAlertAlertRuleLocationUserId(UUID userId, Pageable pageable);

    /**
     * Finds notifications that are pending / retryable for dispatch workers.
     * Eagerly fetches alert and disaster event details.
     */
    @Query("""
            SELECT n FROM Notification n
            JOIN FETCH n.alert a
            JOIN FETCH a.disasterEvent
            JOIN FETCH a.alertRule ar
            WHERE n.status = :status
              AND n.attemptCount < :maxAttempts
            ORDER BY n.createdAt ASC
            """)
    List<Notification> findPendingForDispatch(
            @Param("status") NotificationStatus status,
            @Param("maxAttempts") int maxAttempts
    );

    /**
     * Eagerly fetches notification with alert, disaster event, alert rule, and location.
     */
    @Query("""
            SELECT n FROM Notification n
            JOIN FETCH n.alert a
            JOIN FETCH a.disasterEvent
            JOIN FETCH a.alertRule ar
            JOIN FETCH ar.location
            WHERE n.id = :id
            """)
    Optional<Notification> findByIdWithDetails(@Param("id") UUID id);
}
