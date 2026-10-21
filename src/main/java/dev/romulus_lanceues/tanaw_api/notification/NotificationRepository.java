package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    boolean existsByAlertIdAndChannel(UUID alertId, NotificationChannel channel);


    String PROJECTION = """
            new dev.romulus_lanceues.tanaw_api.notification.NotificationResponse(
                n.id,
                a.id,
                n.channel,
                n.destination,
                n.status,
                n.attemptCount,
                n.sentAt,
                n.failureReason,
                n.createdAt,
                a.triggeredAt,
                a.status,
                l.id,
                l.name,
                de.disasterType,
                de.id,
                de.occurredAt,
                de.severity
            )
            """;

    String JOINS = """
            JOIN n.alert a
            JOIN a.disasterEvent de
            JOIN a.alertRule ar
            JOIN ar.location l
            """;


    @Query("SELECT " + PROJECTION + " FROM Notification n " + JOINS
            + " WHERE n.id = :id AND l.user.id = :userId")
    Optional<NotificationResponse> findByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );


    @Query("SELECT " + PROJECTION + " FROM Notification n " + JOINS
            + " WHERE l.user.id = :userId"
            + " AND (:status IS NULL OR n.status = :status)")
    Page<NotificationResponse> findByUserId(
            @Param("userId") UUID userId,
            @Param("status") NotificationStatus status,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.status = dev.romulus_lanceues.tanaw_api.notification.NotificationStatus.CANCELLED,
                n.failureReason = :reason
            WHERE n.endpoint.id = :endpointId
              AND n.status = dev.romulus_lanceues.tanaw_api.notification.NotificationStatus.PENDING
            """)
    int cancelPendingByEndpointId(
            @Param("endpointId") UUID endpointId,
            @Param("reason") String reason
    );

    default int cancelPendingByEndpointId(UUID endpointId) {
        return cancelPendingByEndpointId(endpointId, "Endpoint disabled or deleted");
    }

}
