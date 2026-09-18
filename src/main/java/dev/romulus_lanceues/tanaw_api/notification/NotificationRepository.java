package dev.romulus_lanceues.tanaw_api.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
                de.magnitude,
                de.severity,
                de.depthKm,
                de.latitude,
                de.longitude
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

}
