package dev.romulus_lanceues.tanaw_api.notification.endpoint;

import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationEncryptionConverter;
import dev.romulus_lanceues.tanaw_api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "notification_endpoints",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_endpoints_user_channel",
                        columnNames = {"user_id", "channel"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationChannel channel;

    @Convert(converter = DestinationEncryptionConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String destination;

    @Column(name = "destination_hint", nullable = false, length = 100)
    private String destinationHint;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "disabled_reason", length = 100)
    private String disabledReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void enable() {
        this.enabled = true;
        this.disabledReason = null;
    }

    public void disable(String reason) {
        this.enabled = false;
        this.disabledReason = reason;
    }

    public void markVerified(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public void updateDestination(String destination, String destinationHint) {
        this.destination = destination;
        this.destinationHint = destinationHint;
        this.verifiedAt = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationEndpoint that)) return false;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
