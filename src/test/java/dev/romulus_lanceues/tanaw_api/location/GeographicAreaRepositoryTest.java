package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import dev.romulus_lanceues.tanaw_api.enums.GeographicAreaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
@DisplayName("GeographicAreaRepository Tests")
class GeographicAreaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private GeographicAreaRepository geographicAreaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private GeographicArea createArea(String psgcCode, String name, GeographicAreaType type, GeographicArea parent, boolean active) {
        return GeographicArea.builder()
                .psgcCode(psgcCode)
                .name(name)
                .type(type)
                .parent(parent)
                .active(active)
                .build();
    }

    @Nested
    @DisplayName("Entity Persistence and Relationships")
    class PersistenceAndRelationships {

        @Test
        @DisplayName("should persist geographic area and generate ID")
        void shouldPersistGeographicAreaAndGenerateId() {
            GeographicArea area = createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true);

            GeographicArea saved = geographicAreaRepository.saveAndFlush(area);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getName()).isEqualTo("Quezon City");
            assertThat(saved.getType()).isEqualTo(GeographicAreaType.MUNICIPALITY);
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("should persist geographic area with all fields")
        void shouldPersistGeographicAreaWithAllFields() {
            GeographicArea area = createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true);

            GeographicArea saved = geographicAreaRepository.saveAndFlush(area);

            Optional<GeographicArea> found = geographicAreaRepository.findById(saved.getId());

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(persisted -> {
                        assertThat(persisted.getId()).isEqualTo(saved.getId());
                        assertThat(persisted.getPsgcCode()).isEqualTo("137400000");
                        assertThat(persisted.getName()).isEqualTo("Quezon City");
                        assertThat(persisted.getType()).isEqualTo(GeographicAreaType.MUNICIPALITY);
                        assertThat(persisted.isActive()).isTrue();
                        assertThat(persisted.getParent()).isNull();
                    });
        }

        @Test
        @DisplayName("should persist geographic area without parent")
        void shouldPersistGeographicAreaWithoutParent() {
            GeographicArea area = createArea("130000000", "National Capital Region", GeographicAreaType.REGION, null, true);

            GeographicArea saved = geographicAreaRepository.saveAndFlush(area);

            Optional<GeographicArea> found = geographicAreaRepository.findById(saved.getId());

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(persisted -> assertThat(persisted.getParent()).isNull());
        }

        @Test
        @DisplayName("should persist geographic area with parent relationship")
        void shouldPersistGeographicAreaWithParentRelationship() {
            GeographicArea parent = entityManager.persistAndFlush(
                    createArea("130000000", "National Capital Region", GeographicAreaType.REGION, null, true)
            );
            GeographicArea child = createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, parent, true);
            GeographicArea saved = geographicAreaRepository.saveAndFlush(child);
            entityManager.clear();

            Optional<GeographicArea> found = geographicAreaRepository.findById(saved.getId());

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(persisted -> {
                        assertThat(persisted.getParent()).isNotNull();
                        assertThat(persisted.getParent().getId()).isEqualTo(parent.getId());
                        assertThat(persisted.getParent().getName()).isEqualTo("National Capital Region");
                    });
        }

        @Test
        @DisplayName("should enforce unique constraint on psgcCode")
        void shouldEnforceUniqueConstraintOnPsgcCode() {
            entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );

            GeographicArea duplicateArea = createArea("137400000", "Duplicate City", GeographicAreaType.MUNICIPALITY, null, true);

            assertThatThrownBy(() -> geographicAreaRepository.saveAndFlush(duplicateArea))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should persist status changes when activated or deactivated")
        void shouldPersistStatusChangesWhenActivatedOrDeactivated() {
            GeographicArea area = entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );

            area.deactivate();
            geographicAreaRepository.saveAndFlush(area);

            entityManager.clear();
            GeographicArea deactivated = entityManager.find(GeographicArea.class, area.getId());
            assertThat(deactivated.isActive()).isFalse();

            deactivated.activate();
            geographicAreaRepository.saveAndFlush(deactivated);

            entityManager.clear();
            GeographicArea reactivated = entityManager.find(GeographicArea.class, area.getId());
            assertThat(reactivated.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("findByPsgcCode")
    class FindByPsgcCode {

        @Test
        @DisplayName("should find geographic area by PSGC code")
        void shouldFindGeographicAreaByPsgcCode() {
            GeographicArea area = entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );

            Optional<GeographicArea> found = geographicAreaRepository.findByPsgcCode(area.getPsgcCode());

            assertThat(found)
                    .isPresent()
                    .hasValueSatisfying(persisted -> {
                        assertThat(persisted.getId()).isEqualTo(area.getId());
                        assertThat(persisted.getPsgcCode()).isEqualTo("137400000");
                        assertThat(persisted.getName()).isEqualTo("Quezon City");
                        assertThat(persisted.getType()).isEqualTo(GeographicAreaType.MUNICIPALITY);
                        assertThat(persisted.isActive()).isTrue();
                    });
        }

        @Test
        @DisplayName("should return empty when PSGC code is not registered")
        void shouldReturnEmptyWhenPsgcCodeIsNotRegistered() {
            Optional<GeographicArea> found = geographicAreaRepository.findByPsgcCode("999999999");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByPsgcCode")
    class ExistsByPsgcCode {

        @Test
        @DisplayName("should return true when geographic area exists with given PSGC code")
        void shouldReturnTrueWhenExistsByPsgcCode() {
            entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );

            boolean exists = geographicAreaRepository.existsByPsgcCode("137400000");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when PSGC code does not exist")
        void shouldReturnFalseWhenPsgcCodeDoesNotExist() {
            boolean exists = geographicAreaRepository.existsByPsgcCode("999999999");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByType")
    class FindByType {

        @Test
        @DisplayName("should find all geographic areas matching specified type")
        void shouldFindAllGeographicAreasMatchingSpecifiedType() {
            entityManager.persistAndFlush(createArea("130000000", "National Capital Region", GeographicAreaType.REGION, null, true));
            entityManager.persistAndFlush(createArea("040000000", "CALABARZON", GeographicAreaType.REGION, null, true));
            entityManager.persistAndFlush(createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true));

            List<GeographicArea> regions = geographicAreaRepository.findByType(GeographicAreaType.REGION);

            assertThat(regions)
                    .hasSize(2)
                    .extracting(GeographicArea::getName)
                    .containsExactlyInAnyOrder("National Capital Region", "CALABARZON");
        }

        @Test
        @DisplayName("should return both active and inactive geographic areas matching specified type")
        void shouldReturnBothActiveAndInactiveGeographicAreasMatchingSpecifiedType() {
            entityManager.persistAndFlush(createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true));
            entityManager.persistAndFlush(createArea("137600000", "City of Manila", GeographicAreaType.MUNICIPALITY, null, false));

            List<GeographicArea> municipalities = geographicAreaRepository.findByType(GeographicAreaType.MUNICIPALITY);

            assertThat(municipalities)
                    .hasSize(2)
                    .extracting(GeographicArea::getName)
                    .containsExactlyInAnyOrder("Quezon City", "City of Manila");
        }

        @Test
        @DisplayName("should return empty list when no geographic areas match specified type")
        void shouldReturnEmptyListWhenNoGeographicAreasMatchSpecifiedType() {
            entityManager.persistAndFlush(createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true));

            List<GeographicArea> provinces = geographicAreaRepository.findByType(GeographicAreaType.PROVINCE);

            assertThat(provinces).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByTypeAndActiveTrue")
    class FindByTypeAndActiveTrue {

        @Test
        @DisplayName("should find only active geographic areas matching specified type")
        void shouldFindOnlyActiveGeographicAreasMatchingSpecifiedType() {
            entityManager.persistAndFlush(createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true));
            entityManager.persistAndFlush(createArea("137600000", "City of Manila", GeographicAreaType.MUNICIPALITY, null, false));
            entityManager.persistAndFlush(createArea("130000000", "National Capital Region", GeographicAreaType.REGION, null, true));

            List<GeographicArea> activeMunicipalities = geographicAreaRepository.findByTypeAndActiveTrue(GeographicAreaType.MUNICIPALITY);

            assertThat(activeMunicipalities)
                    .hasSize(1)
                    .extracting(GeographicArea::getName)
                    .containsExactly("Quezon City");
        }

        @Test
        @DisplayName("should return empty list when matching type exists but all are inactive")
        void shouldReturnEmptyListWhenMatchingTypeExistsButAllAreInactive() {
            entityManager.persistAndFlush(createArea("137600000", "City of Manila", GeographicAreaType.MUNICIPALITY, null, false));

            List<GeographicArea> activeMunicipalities = geographicAreaRepository.findByTypeAndActiveTrue(GeographicAreaType.MUNICIPALITY);

            assertThat(activeMunicipalities).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when no geographic areas exist for specified type")
        void shouldReturnEmptyListWhenNoGeographicAreasExistForSpecifiedType() {
            List<GeographicArea> activeBarangays = geographicAreaRepository.findByTypeAndActiveTrue(GeographicAreaType.BARANGAY);

            assertThat(activeBarangays).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByParentId")
    class FindByParentId {

        @Test
        @DisplayName("should find all child geographic areas by parent ID")
        void shouldFindAllChildGeographicAreasByParentId() {
            GeographicArea parent = entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );
            entityManager.persistAndFlush(createArea("137404001", "Batasan Hills", GeographicAreaType.BARANGAY, parent, true));
            entityManager.persistAndFlush(createArea("137404002", "Commonwealth", GeographicAreaType.BARANGAY, parent, false));

            List<GeographicArea> children = geographicAreaRepository.findByParentId(parent.getId());

            assertThat(children)
                    .hasSize(2)
                    .extracting(GeographicArea::getName)
                    .containsExactlyInAnyOrder("Batasan Hills", "Commonwealth");
        }

        @Test
        @DisplayName("should exclude geographic areas belonging to different parent or without parent")
        void shouldExcludeGeographicAreasBelongingToDifferentParentOrWithoutParent() {
            GeographicArea parent1 = entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );
            GeographicArea parent2 = entityManager.persistAndFlush(
                    createArea("137600000", "City of Manila", GeographicAreaType.MUNICIPALITY, null, true)
            );

            entityManager.persistAndFlush(createArea("137404001", "Batasan Hills", GeographicAreaType.BARANGAY, parent1, true));
            entityManager.persistAndFlush(createArea("137601001", "Barangay 1", GeographicAreaType.BARANGAY, parent2, true));
            entityManager.persistAndFlush(createArea("130000000", "National Capital Region", GeographicAreaType.REGION, null, true));

            List<GeographicArea> parent1Children = geographicAreaRepository.findByParentId(parent1.getId());

            assertThat(parent1Children)
                    .hasSize(1)
                    .extracting(GeographicArea::getName)
                    .containsExactly("Batasan Hills");
        }

        @Test
        @DisplayName("should return empty list when parent has no children")
        void shouldReturnEmptyListWhenParentHasNoChildren() {
            GeographicArea parent = entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );

            List<GeographicArea> children = geographicAreaRepository.findByParentId(parent.getId());

            assertThat(children).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when parent ID does not exist")
        void shouldReturnEmptyListWhenParentIdDoesNotExist() {
            List<GeographicArea> children = geographicAreaRepository.findByParentId(UUID.randomUUID());

            assertThat(children).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByParentIdAndActiveTrue")
    class FindByParentIdAndActiveTrue {

        @Test
        @DisplayName("should find only active child geographic areas by parent ID")
        void shouldFindOnlyActiveChildGeographicAreasByParentId() {
            GeographicArea parent = entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );
            entityManager.persistAndFlush(createArea("137404001", "Batasan Hills", GeographicAreaType.BARANGAY, parent, true));
            entityManager.persistAndFlush(createArea("137404002", "Commonwealth", GeographicAreaType.BARANGAY, parent, false));

            List<GeographicArea> activeChildren = geographicAreaRepository.findByParentIdAndActiveTrue(parent.getId());

            assertThat(activeChildren)
                    .hasSize(1)
                    .extracting(GeographicArea::getName)
                    .containsExactly("Batasan Hills");
        }

        @Test
        @DisplayName("should return empty list when parent has children but all are inactive")
        void shouldReturnEmptyListWhenParentHasChildrenButAllAreInactive() {
            GeographicArea parent = entityManager.persistAndFlush(
                    createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true)
            );
            entityManager.persistAndFlush(createArea("137404002", "Commonwealth", GeographicAreaType.BARANGAY, parent, false));

            List<GeographicArea> activeChildren = geographicAreaRepository.findByParentIdAndActiveTrue(parent.getId());

            assertThat(activeChildren).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when parent ID does not exist")
        void shouldReturnEmptyListWhenParentIdDoesNotExist() {
            List<GeographicArea> activeChildren = geographicAreaRepository.findByParentIdAndActiveTrue(UUID.randomUUID());

            assertThat(activeChildren).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByActiveTrue")
    class FindByActiveTrue {

        @Test
        @DisplayName("should find all active geographic areas across all types")
        void shouldFindAllActiveGeographicAreasAcrossAllTypes() {
            entityManager.persistAndFlush(createArea("130000000", "National Capital Region", GeographicAreaType.REGION, null, true));
            entityManager.persistAndFlush(createArea("137400000", "Quezon City", GeographicAreaType.MUNICIPALITY, null, true));
            entityManager.persistAndFlush(createArea("137600000", "City of Manila", GeographicAreaType.MUNICIPALITY, null, false));

            List<GeographicArea> activeAreas = geographicAreaRepository.findByActiveTrue();

            assertThat(activeAreas)
                    .hasSize(2)
                    .extracting(GeographicArea::getName)
                    .containsExactlyInAnyOrder("National Capital Region", "Quezon City");
        }

        @Test
        @DisplayName("should return empty list when no active geographic areas exist")
        void shouldReturnEmptyListWhenNoActiveGeographicAreasExist() {
            entityManager.persistAndFlush(createArea("137600000", "City of Manila", GeographicAreaType.MUNICIPALITY, null, false));

            List<GeographicArea> activeAreas = geographicAreaRepository.findByActiveTrue();

            assertThat(activeAreas).isEmpty();
        }
    }
}
