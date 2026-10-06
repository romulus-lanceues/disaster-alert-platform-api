package dev.romulus_lanceues.tanaw_api.disaster;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "disaster_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_disaster_source_external",
                        columnNames = {"source", "external_id"}
                ),
                @UniqueConstraint(
                        name = "uk_disaster_id_type",
                        columnNames = {"id", "disaster_type"}
                )
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DisasterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "disaster_type", nullable = false, length = 30)
    private DisasterType disasterType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 20)
    private String status;

    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    @Column(columnDefinition = "TEXT")
    private String place;

    @Column(length = 30)
    private String severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private JsonNode rawPayload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

}
