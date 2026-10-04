package dev.romulus_lanceues.tanaw_api.psgc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PsgcImportService {

    public record ImportSummary(int rowsInFile, int deactivated){}

    private final JdbcTemplate jdbc;

    @Transactional(rollbackFor = Exception.class)
    public ImportSummary importFile(Path file) throws IOException {


        var result = new PsgcExcelReader().read(file);
        if (result.hasErrors()) {
            throw new IllegalStateException("Problems in the Excel file: " + result.errors());
        }
        Map<String, AreaType> typeByCode = result.rows().stream()
                .collect(Collectors.toMap(PsgcExcelReader.PsgcRow::code, PsgcExcelReader.PsgcRow::areaType));
        var resolution = new PsgcParentResolver().resolve(typeByCode);
        if (!resolution.errors().isEmpty()) {
            throw new IllegalStateException("Problems finding parents: " + resolution.errors());
        }

        // Copy the file's rows into a temporary staging table
        jdbc.execute("""
                CREATE TEMP TABLE psgc_staging (
                    code        VARCHAR(10)  PRIMARY KEY,
                    name        VARCHAR(150) NOT NULL,
                    type        VARCHAR(30)  NOT NULL,
                    parent_code VARCHAR(10)
                ) ON COMMIT DROP
                """);
        jdbc.batchUpdate(
                "INSERT INTO psgc_staging (code, name, type, parent_code) VALUES (?, ?, ?, ?)",
                result.rows(), 1000,
                (ps, row) -> {
                    ps.setString(1, row.code());
                    ps.setString(2, row.name());
                    ps.setString(3, row.areaType().name());
                    ps.setString(4, resolution.parentByCode().get(row.code()));
                });

        // Safety check: a truncated file must not wipe out most of the table
        Integer activeBefore = jdbc.queryForObject(
                "SELECT count(*) FROM geographic_areas WHERE active", Integer.class);
        if (activeBefore != null && activeBefore > 0 && result.rows().size() < activeBefore * 0.9) {
            throw new IllegalStateException("File has " + result.rows().size()
                    + " areas but the database has " + activeBefore
                    + " active ones; refusing to deactivate that many.");
        }

        // Create new areas, update existing ones (matched by code), reactivate
        jdbc.update("""
                INSERT INTO geographic_areas (psgc_code, name, type, active)
                SELECT code, name, type, TRUE FROM psgc_staging
                ON CONFLICT (psgc_code) DO UPDATE
                SET name = EXCLUDED.name, type = EXCLUDED.type, active = TRUE
                """);

        // Link each area to its parent
        jdbc.update("""
                UPDATE geographic_areas a
                SET parent_id = p.id
                FROM psgc_staging s
                LEFT JOIN geographic_areas p ON p.psgc_code = s.parent_code
                WHERE a.psgc_code = s.code
                  AND a.parent_id IS DISTINCT FROM p.id
                """);

        // Areas missing from this file become inactive (never deleted)
        int deactivated = jdbc.update("""
                UPDATE geographic_areas g
                SET active = FALSE
                WHERE g.active
                  AND NOT EXISTS (SELECT 1 FROM psgc_staging s WHERE s.code = g.psgc_code)
                """);

        // Validate the hierarchy; any hit throws and rolls everything back
        validate("Areas without a parent",
                "SELECT psgc_code FROM geographic_areas "
                        + "WHERE active AND type <> 'REGION' AND parent_id IS NULL");
        validate("Parent in a different region", """
                SELECT c.psgc_code FROM geographic_areas c
                JOIN geographic_areas p ON p.id = c.parent_id
                WHERE left(c.psgc_code, 2) <> left(p.psgc_code, 2)
                """);
        validate("Active area under an inactive parent", """
                SELECT c.psgc_code FROM geographic_areas c
                JOIN geographic_areas p ON p.id = c.parent_id
                WHERE c.active AND NOT p.active
                """);

        return new ImportSummary(result.rows().size(), deactivated);
    }

    private void validate(String rule, String sql) {
        List<String> offenders = jdbc.queryForList(sql + " LIMIT 10", String.class);
        if (!offenders.isEmpty()) {
            throw new IllegalStateException(rule + " (first 10): " + offenders);
        }
    }

}
