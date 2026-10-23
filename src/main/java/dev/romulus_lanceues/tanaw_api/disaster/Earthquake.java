package dev.romulus_lanceues.tanaw_api.disaster;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Entity
@Table(name = "earthquake")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Earthquake {

    @Id
    @Column(name = "disaster_event_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "disaster_event_id")
    private DisasterEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "disaster_type", nullable = false, length = 30, insertable = false, updatable = false)
    private DisasterType disasterType;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(nullable = false)
    private Point location;

    private Double magnitude;

    @Column(name = "depth_km")
    private Double depthKm;


}
