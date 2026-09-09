package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.enums.GeographicAreaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeographicAreaRepository extends JpaRepository<GeographicArea, UUID> {

    Optional<GeographicArea> findByPsgcCode(String psgcCode);

    boolean existsByPsgcCode(String psgcCode);

    List<GeographicArea> findByType(GeographicAreaType type);

    List<GeographicArea> findByTypeAndActiveTrue(GeographicAreaType type);

    List<GeographicArea> findByParentId(UUID parentId);

    List<GeographicArea> findByParentIdAndActiveTrue(UUID parentId);

    List<GeographicArea> findByActiveTrue();
}
