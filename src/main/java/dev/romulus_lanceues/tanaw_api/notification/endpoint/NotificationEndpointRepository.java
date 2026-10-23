package dev.romulus_lanceues.tanaw_api.notification.endpoint;

import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationEndpointRepository extends JpaRepository<NotificationEndpoint, UUID>, JpaSpecificationExecutor<NotificationEndpoint> {

    Optional<NotificationEndpoint> findByIdAndUserId(UUID id, UUID userId);

    Optional<NotificationEndpoint> findByUserIdAndChannel(UUID userId, NotificationChannel channel);

    List<NotificationEndpoint> findByUserIdOrderByCreatedAtAsc(UUID userId);

    boolean existsByUserIdAndChannel(UUID userId, NotificationChannel channel);
}
