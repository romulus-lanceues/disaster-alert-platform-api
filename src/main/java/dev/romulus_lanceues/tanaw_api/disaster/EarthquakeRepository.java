package dev.romulus_lanceues.tanaw_api.disaster;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EarthquakeRepository extends JpaRepository<Earthquake, UUID> {

}
