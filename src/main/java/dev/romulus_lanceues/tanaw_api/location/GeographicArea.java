package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.enums.GeographicAreaType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "geographic_areas",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_geographic_area_psgc_code",
                        columnNames = "psgc_code"
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GeographicArea {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "psgc_code", nullable = false, length = 20)
    private String psgcCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GeographicAreaType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private GeographicArea parent;

    @Column(nullable = false)
    private boolean active;

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
