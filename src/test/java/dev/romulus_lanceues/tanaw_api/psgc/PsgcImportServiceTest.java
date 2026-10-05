package dev.romulus_lanceues.tanaw_api.psgc;

import dev.romulus_lanceues.tanaw_api.config.JpaAuditingTestConfig;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingTestConfig.class, PsgcImportService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("PsgcImportService Real Database Integration Tests")
class PsgcImportServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private PsgcImportService importService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void cleanDatabase() {
        dropKeepActiveTrigger();
        jdbcTemplate.execute("TRUNCATE TABLE geographic_areas CASCADE");
    }

    private void createKeepActiveTrigger(String psgcCode) {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION keep_active() RETURNS trigger AS $$
                BEGIN
                    NEW.active := TRUE;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;

                DROP TRIGGER IF EXISTS trg_keep_active ON geographic_areas;
                CREATE TRIGGER trg_keep_active
                BEFORE UPDATE ON geographic_areas
                FOR EACH ROW WHEN (NEW.psgc_code = '""" + psgcCode + """
                ')
                EXECUTE FUNCTION keep_active();
                """);
    }

    private void dropKeepActiveTrigger() {
        jdbcTemplate.execute("""
                DROP TRIGGER IF EXISTS trg_keep_active ON geographic_areas;
                DROP FUNCTION IF EXISTS keep_active();
                """);
    }

    @Nested
    @DisplayName("importFile - File and Resolver Preconditions")
    class Preconditions {

        @Test
        @DisplayName("should throw IOException when file does not exist without writing to database")
        void shouldThrowIOException_whenFileDoesNotExist() {
            Path nonExistent = tempDir.resolve("non_existent.xlsx");

            assertThatThrownBy(() -> importService.importFile(nonExistent))
                    .isInstanceOf(IOException.class);

            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM geographic_areas", Integer.class);
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("should throw IllegalStateException when Excel file has parsing errors")
        void shouldThrowIllegalStateException_whenExcelHasErrors() throws IOException {
            Path file = createWorkbook("invalid_level.xlsx", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "InvalidLevel")
            ));

            assertThatThrownBy(() -> importService.importFile(file))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Problems in the Excel file");

            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM geographic_areas", Integer.class);
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("should throw IllegalStateException when parent resolution fails")
        void shouldThrowIllegalStateException_whenParentResolutionFails() throws IOException {
            Path file = createWorkbook("orphan_province.xlsx", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0128000000", "Ilocos Norte", "Prov")
            ));

            assertThatThrownBy(() -> importService.importFile(file))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Problems finding parents")
                    .hasMessageContaining("No parent found for 0128000000 (PROVINCE)");

            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM geographic_areas", Integer.class);
            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("importFile - Database Persistence and Hierarchy Linking")
    class PersistenceAndHierarchy {

        @Test
        @DisplayName("should insert all areas with generated UUIDs and valid parent-child relationships")
        void shouldPersistNewAreasWithCorrectHierarchyAndGenerateIds() throws IOException {
            Path file = createWorkbook("initial_import.xlsx", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg"),
                    List.of("0128000000", "Ilocos Norte", "Prov"),
                    List.of("0128010000", "Adams", "Mun"),
                    List.of("0128010001", "Adams (Poblacion)", "Bgy")
            ));

            PsgcImportService.ImportSummary summary = importService.importFile(file);

            assertThat(summary.rowsInFile()).isEqualTo(4);
            assertThat(summary.deactivated()).isEqualTo(0);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, psgc_code, name, type, parent_id, active FROM geographic_areas ORDER BY psgc_code"
            );
            assertThat(rows).hasSize(4);

            Map<String, Object> region = rows.get(0);
            assertThat(region.get("psgc_code")).isEqualTo("0100000000");
            assertThat(region.get("name")).isEqualTo("Region I");
            assertThat(region.get("type")).isEqualTo("REGION");
            assertThat(region.get("active")).isEqualTo(true);
            assertThat(region.get("parent_id")).isNull();
            UUID regionId = (UUID) region.get("id");
            assertThat(regionId).isNotNull();

            Map<String, Object> province = rows.get(1);
            assertThat(province.get("psgc_code")).isEqualTo("0128000000");
            assertThat(province.get("name")).isEqualTo("Ilocos Norte");
            assertThat(province.get("type")).isEqualTo("PROVINCE");
            assertThat(province.get("active")).isEqualTo(true);
            assertThat(province.get("parent_id")).isEqualTo(regionId);
            UUID provinceId = (UUID) province.get("id");
            assertThat(provinceId).isNotNull();

            Map<String, Object> municipality = rows.get(2);
            assertThat(municipality.get("psgc_code")).isEqualTo("0128010000");
            assertThat(municipality.get("name")).isEqualTo("Adams");
            assertThat(municipality.get("type")).isEqualTo("MUNICIPALITY");
            assertThat(municipality.get("active")).isEqualTo(true);
            assertThat(municipality.get("parent_id")).isEqualTo(provinceId);
            UUID municipalityId = (UUID) municipality.get("id");
            assertThat(municipalityId).isNotNull();

            Map<String, Object> barangay = rows.get(3);
            assertThat(barangay.get("psgc_code")).isEqualTo("0128010001");
            assertThat(barangay.get("name")).isEqualTo("Adams (Poblacion)");
            assertThat(barangay.get("type")).isEqualTo("BARANGAY");
            assertThat(barangay.get("active")).isEqualTo(true);
            assertThat(barangay.get("parent_id")).isEqualTo(municipalityId);
            assertThat(barangay.get("id")).isNotNull();
        }

        @Test
        @DisplayName("should update existing area names, deactivate missing ones, and preserve UUIDs")
        void shouldUpdateExistingAreasDeactivateMissingAndPreserveIds() throws IOException {
            // 1. Initial import of 4 areas
            Path initialFile = createWorkbook("initial_areas.xlsx", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg"),
                    List.of("0128000000", "Ilocos Norte", "Prov"),
                    List.of("0128010000", "Adams", "Mun"),
                    List.of("0128010001", "Adams (Poblacion)", "Bgy")
            ));
            importService.importFile(initialFile);

            Map<String, Object> regionBefore = jdbcTemplate.queryForMap(
                    "SELECT id, name FROM geographic_areas WHERE psgc_code = '0100000000'"
            );
            UUID originalRegionId = (UUID) regionBefore.get("id");
            UUID originalBarangayId = jdbcTemplate.queryForObject(
                    "SELECT id FROM geographic_areas WHERE psgc_code = '0128010001'", UUID.class
            );

            // 2. Second import:
            // - Region I is renamed
            // - Barangay 0128010001 is omitted (should become active = false)
            // - New Barangay 0128010002 is added
            Path updatedFile = createWorkbook("updated_areas.xlsx", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I (Updated Name)", "Reg"),
                    List.of("0128000000", "Ilocos Norte", "Prov"),
                    List.of("0128010000", "Adams", "Mun"),
                    List.of("0128010002", "Adams Bgy 2", "Bgy")
            ));
            PsgcImportService.ImportSummary summary = importService.importFile(updatedFile);

            assertThat(summary.rowsInFile()).isEqualTo(4);
            assertThat(summary.deactivated()).isEqualTo(1);

            // Assert Region updated name and preserved original UUID
            Map<String, Object> regionAfter = jdbcTemplate.queryForMap(
                    "SELECT id, name, active FROM geographic_areas WHERE psgc_code = '0100000000'"
            );
            assertThat(regionAfter.get("id")).isEqualTo(originalRegionId);
            assertThat(regionAfter.get("name")).isEqualTo("Region I (Updated Name)");
            assertThat(regionAfter.get("active")).isEqualTo(true);

            // Assert omitted barangay is inactive and not deleted
            Map<String, Object> omittedBarangay = jdbcTemplate.queryForMap(
                    "SELECT id, active FROM geographic_areas WHERE psgc_code = '0128010001'"
            );
            assertThat(omittedBarangay.get("id")).isEqualTo(originalBarangayId);
            assertThat(omittedBarangay.get("active")).isEqualTo(false);

            // Assert newly added barangay is active
            Boolean newBarangayActive = jdbcTemplate.queryForObject(
                    "SELECT active FROM geographic_areas WHERE psgc_code = '0128010002'", Boolean.class
            );
            assertThat(newBarangayActive).isTrue();

            Integer totalAreas = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM geographic_areas", Integer.class
            );
            assertThat(totalAreas).isEqualTo(5); // 4 active + 1 inactive
        }

        @Test
        @DisplayName("should reactivate a previously deactivated area when re-introduced in the file")
        void shouldReactivatePreviouslyDeactivatedArea() throws IOException {
            // Initial import of 10 areas (Region + Province + Municipality + 7 Barangays)
            Path file1 = createWorkbook("ten_areas.xlsx", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg"),
                    List.of("0128000000", "Ilocos Norte", "Prov"),
                    List.of("0128010000", "Adams", "Mun"),
                    List.of("0128010001", "Adams 1", "Bgy"),
                    List.of("0128010002", "Adams 2", "Bgy"),
                    List.of("0128010003", "Adams 3", "Bgy"),
                    List.of("0128010004", "Adams 4", "Bgy"),
                    List.of("0128010005", "Adams 5", "Bgy"),
                    List.of("0128010006", "Adams 6", "Bgy"),
                    List.of("0128010007", "Adams 7", "Bgy")
            ));
            importService.importFile(file1);

            // Second file omits Adams 7 (9 rows >= 10 * 0.9 = 9.0 -> allowed deactivation)
            Path file2 = createWorkbook("nine_areas.xlsx", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg"),
                    List.of("0128000000", "Ilocos Norte", "Prov"),
                    List.of("0128010000", "Adams", "Mun"),
                    List.of("0128010001", "Adams 1", "Bgy"),
                    List.of("0128010002", "Adams 2", "Bgy"),
                    List.of("0128010003", "Adams 3", "Bgy"),
                    List.of("0128010004", "Adams 4", "Bgy"),
                    List.of("0128010005", "Adams 5", "Bgy"),
                    List.of("0128010006", "Adams 6", "Bgy")
            ));
            PsgcImportService.ImportSummary summary2 = importService.importFile(file2);
            assertThat(summary2.deactivated()).isEqualTo(1);

            Boolean adams7ActiveAfterDeactivation = jdbcTemplate.queryForObject(
                    "SELECT active FROM geographic_areas WHERE psgc_code = '0128010007'", Boolean.class
            );
            assertThat(adams7ActiveAfterDeactivation).isFalse();

            // Third file brings Adams 7 back -> reactivated
            PsgcImportService.ImportSummary summary3 = importService.importFile(file1);
            assertThat(summary3.deactivated()).isEqualTo(0);

            Boolean adams7ActiveAfterReactivation = jdbcTemplate.queryForObject(
                    "SELECT active FROM geographic_areas WHERE psgc_code = '0128010007'", Boolean.class
            );
            assertThat(adams7ActiveAfterReactivation).isTrue();
        }
    }

    @Nested
    @DisplayName("importFile - Safety Check & Rollback")
    class SafetyCheckAndRollback {

        @Test
        @DisplayName("should refuse import and roll back changes when file has < 90% of active database areas")
        void shouldRefuseImportAndRollback_whenRowsAreBelowNinetyPercentThreshold() throws IOException {
            // Seed database with 10 active areas directly
            jdbcTemplate.update("""
                    INSERT INTO geographic_areas (psgc_code, name, type, active) VALUES
                    ('0100000000', 'Region I', 'REGION', TRUE),
                    ('0128000000', 'Ilocos Norte', 'PROVINCE', TRUE),
                    ('0128010000', 'Adams', 'MUNICIPALITY', TRUE),
                    ('0128010001', 'Adams 1', 'BARANGAY', TRUE),
                    ('0128010002', 'Adams 2', 'BARANGAY', TRUE),
                    ('0128010003', 'Adams 3', 'BARANGAY', TRUE),
                    ('0128010004', 'Adams 4', 'BARANGAY', TRUE),
                    ('0128010005', 'Adams 5', 'BARANGAY', TRUE),
                    ('0128010006', 'Adams 6', 'BARANGAY', TRUE),
                    ('0128010007', 'Adams 7', 'BARANGAY', TRUE)
                    """);

            // File has only 2 rows (< 10 * 0.9 = 9)
            Path truncatedFile = createWorkbook("truncated.xlsx", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg"),
                    List.of("0128000000", "Ilocos Norte", "Prov")
            ));

            assertThatThrownBy(() -> importService.importFile(truncatedFile))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("File has 2 areas but the database has 10 active ones; refusing to deactivate that many.");

            // Assert that all 10 areas remain active and no deactivations happened
            Integer activeCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM geographic_areas WHERE active", Integer.class
            );
            assertThat(activeCount).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("importFile - Hierarchy Integrity Validation and Rollback")
    class HierarchyIntegrityValidation {

        @Test
        @DisplayName("should roll back and throw exception when active non-region area lacks a parent")
        void shouldRollbackAndThrow_whenActiveNonRegionAreaLacksParent() throws IOException {
            try {
                // Seed an active province without a parent in Region 2
                jdbcTemplate.update("""
                        INSERT INTO geographic_areas (psgc_code, name, type, parent_id, active)
                        VALUES ('0228000000', 'Quirino Province', 'PROVINCE', NULL, TRUE)
                        """);
                createKeepActiveTrigger("0228000000");

                // Import Region 1
                Path file = createWorkbook("region_one.xlsx", List.of(
                        List.of("10-digit PSGC", "Name", "Geographic Level"),
                        List.of("0100000000", "Region I", "Reg")
                ));

                assertThatThrownBy(() -> importService.importFile(file))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Areas without a parent (first 10): [0228000000]");

                // Verify Region 1 was rolled back completely due to the validation failure
                Integer region1Count = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM geographic_areas WHERE psgc_code = '0100000000'", Integer.class
                );
                assertThat(region1Count).isEqualTo(0);
            } finally {
                dropKeepActiveTrigger();
            }
        }

        @Test
        @DisplayName("should roll back and throw exception when parent is in a different region")
        void shouldRollbackAndThrow_whenParentInDifferentRegionExists() throws IOException {
            try {
                // Seed Region 1 and Province in Region 2 that points to Region 1 as parent
                jdbcTemplate.update("""
                        INSERT INTO geographic_areas (id, psgc_code, name, type, active)
                        VALUES ('11111111-1111-1111-1111-111111111111', '0100000000', 'Region I', 'REGION', TRUE);

                        INSERT INTO geographic_areas (psgc_code, name, type, parent_id, active)
                        VALUES ('0228000000', 'Quirino Province', 'PROVINCE', '11111111-1111-1111-1111-111111111111', TRUE);
                        """);
                createKeepActiveTrigger("0228000000");

                // Import Region 3 with 2 rows so 2 >= 2 * 0.9
                Path file = createWorkbook("region_three.xlsx", List.of(
                        List.of("10-digit PSGC", "Name", "Geographic Level"),
                        List.of("0300000000", "Central Luzon", "Reg"),
                        List.of("0314000000", "Bulacan", "Prov")
                ));

                assertThatThrownBy(() -> importService.importFile(file))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Parent in a different region (first 10): [0228000000]");

                // Verify Region 3 was rolled back completely
                Integer region3Count = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM geographic_areas WHERE psgc_code = '0300000000'", Integer.class
                );
                assertThat(region3Count).isEqualTo(0);
            } finally {
                dropKeepActiveTrigger();
            }
        }

        @Test
        @DisplayName("should roll back and throw exception when active area has an inactive parent")
        void shouldRollbackAndThrow_whenActiveAreaHasInactiveParent() throws IOException {
            try {
                UUID region2Id = UUID.randomUUID();
                // Region 2 is seeded as inactive, while Province is seeded as active (1 active total in DB)
                jdbcTemplate.update("""
                        INSERT INTO geographic_areas (id, psgc_code, name, type, active)
                        VALUES (?, '0200000000', 'Region II', 'REGION', FALSE);

                        INSERT INTO geographic_areas (psgc_code, name, type, parent_id, active)
                        VALUES ('0228000000', 'Quirino Province', 'PROVINCE', ?, TRUE);
                        """, region2Id, region2Id);

                // Province in Region 2 is kept active, while its parent Region 2 is inactive
                createKeepActiveTrigger("0228000000");

                // Import Region 1 (1 row >= 1 * 0.9)
                Path file = createWorkbook("region_one.xlsx", List.of(
                        List.of("10-digit PSGC", "Name", "Geographic Level"),
                        List.of("0100000000", "Region I", "Reg")
                ));

                assertThatThrownBy(() -> importService.importFile(file))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Active area under an inactive parent (first 10): [0228000000]");

                // Verify Region 1 was rolled back completely
                Integer region1Count = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM geographic_areas WHERE psgc_code = '0100000000'", Integer.class
                );
                assertThat(region1Count).isEqualTo(0);
            } finally {
                dropKeepActiveTrigger();
            }
        }
    }

    private Path createWorkbook(String fileName, List<List<Object>> rows) throws IOException {
        Path file = tempDir.resolve(fileName);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PSGC");
            for (int r = 0; r < rows.size(); r++) {
                List<Object> values = rows.get(r);
                if (values == null) continue;
                Row row = sheet.createRow(r);
                for (int c = 0; c < values.size(); c++) {
                    Object val = values.get(c);
                    if (val instanceof String s) {
                        row.createCell(c).setCellValue(s);
                    } else if (val instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    }
                }
            }
            try (OutputStream os = Files.newOutputStream(file)) {
                workbook.write(os);
            }
        }
        return file;
    }
}
