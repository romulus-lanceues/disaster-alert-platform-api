package dev.romulus_lanceues.tanaw_api.psgc;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PsgcExcelReader Unit Tests")
class PsgcExcelReaderTest {

    private PsgcExcelReader reader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reader = new PsgcExcelReader();
    }

    @Nested
    @DisplayName("read - File and Sheet Validation")
    class FileAndSheetValidation {

        @Test
        @DisplayName("should throw IOException when file does not exist")
        void shouldThrowException_whenFileNotFound() {
            Path nonExistent = tempDir.resolve("non_existent.xlsx");

            assertThatThrownBy(() -> reader.read(nonExistent))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("should throw IllegalStateException when PSGC sheet is missing")
        void shouldThrowException_whenPsgcSheetMissing() throws IOException {
            Path file = createWorkbook("missing_psgc.xlsx", "OtherSheet", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level")
            ));

            assertThatThrownBy(() -> reader.read(file))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Sheet 'PSGC' not found");
        }
    }

    @Nested
    @DisplayName("read - Header Detection")
    class HeaderDetection {

        @Test
        @DisplayName("should find header on the first row")
        void shouldFindHeaderOnFirstRow() throws IOException {
            Path file = createWorkbook("header_row_0.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows()).hasSize(1);
            PsgcExcelReader.PsgcRow row = result.rows().getFirst();
            assertThat(row.code()).isEqualTo("0100000000");
            assertThat(row.name()).isEqualTo("Region I");
            assertThat(row.areaType()).isEqualTo(AreaType.REGION);
            assertThat(row.excelRow()).isEqualTo(2);
        }

        @Test
        @DisplayName("should find header when offset by leading metadata rows")
        void shouldFindHeader_whenOffsetByMetadataRows() throws IOException {
            Path file = createWorkbook("header_offset.xlsx", "PSGC", List.of(
                    List.of("Philippine Standard Geographic Code (PSGC) Publication"),
                    List.of("As of December 2024"),
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().getFirst().excelRow()).isEqualTo(4);
        }

        @Test
        @DisplayName("should support flexible header column order, spacing, and casing")
        void shouldSupportFlexibleHeaderFormatting() throws IOException {
            Path file = createWorkbook("flexible_header.xlsx", "PSGC", List.of(
                    List.of(" NAME ", "10-digit\nPSGC", "  Geographic Level  "),
                    List.of("Ilocos Norte", "0128000000", "Prov")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows()).hasSize(1);
            PsgcExcelReader.PsgcRow row = result.rows().getFirst();
            assertThat(row.code()).isEqualTo("0128000000");
            assertThat(row.name()).isEqualTo("Ilocos Norte");
            assertThat(row.areaType()).isEqualTo(AreaType.PROVINCE);
        }

        @Test
        @DisplayName("should throw IllegalStateException when required header columns are missing")
        void shouldThrowException_whenRequiredHeaderColumnsMissing() throws IOException {
            Path file = createWorkbook("missing_columns.xlsx", "PSGC", List.of(
                    List.of("Code", "Name")
            ));

            assertThatThrownBy(() -> reader.read(file))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Header row not found");
        }

        @Test
        @DisplayName("should throw IllegalStateException when header is beyond scan row limit")
        void shouldThrowException_whenHeaderBeyondScanLimit() throws IOException {
            // Header scan limit is 20 rows
            List<List<Object>> rows = new java.util.ArrayList<>();
            for (int i = 0; i < 22; i++) {
                rows.add(List.of("Notes or blank line " + i));
            }
            rows.add(List.of("10-digit PSGC", "Name", "Geographic Level"));
            rows.add(List.of("0100000000", "Region I", "Reg"));

            Path file = createWorkbook("header_beyond_limit.xlsx", "PSGC", rows);

            assertThatThrownBy(() -> reader.read(file))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Header row not found");
        }
    }

    @Nested
    @DisplayName("read - Data Parsing and Normalization")
    class DataParsingAndNormalization {

        @Test
        @DisplayName("should parse each AreaType from its respective Excel level")
        void shouldParseAllAreaTypes() throws IOException {
            Path file = createWorkbook("all_types.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg"),
                    List.of("0128000000", "Ilocos Norte", "Prov"),
                    List.of("0128010000", "Adams", "Mun"),
                    List.of("0128020000", "Batac City", "City"),
                    List.of("1339010000", "Tondo", "SubMun"),
                    List.of("0128010001", "Adams (Poblacion)", "Bgy")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows()).extracting(PsgcExcelReader.PsgcRow::areaType)
                    .containsExactly(
                            AreaType.REGION,
                            AreaType.PROVINCE,
                            AreaType.MUNICIPALITY,
                            AreaType.CITY,
                            AreaType.SUB_MUNICIPALITY,
                            AreaType.BARANGAY
                    );
        }

        @Test
        @DisplayName("should normalize codes with leading apostrophes, spaces, and pad to 10 digits")
        void shouldNormalizeCodes() throws IOException {
            Path file = createWorkbook("normalized_codes.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("'0100000000", "Apostrophe Prefixed", "Reg"),
                    List.of("’0128000000", "Right Quotation Prefixed", "Prov"),
                    List.of("  0128010000  ", "Whitespace Surrounded", "Mun"),
                    List.of("128020000", "Nine Digit Padded", "City"), // 9 digits -> padded with leading 0
                    List.of(128010001d, "Numeric Formatted Cell", "Bgy") // numeric cell
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows()).extracting(PsgcExcelReader.PsgcRow::code)
                    .containsExactly(
                            "0100000000",
                            "0128000000",
                            "0128010000",
                            "0128020000",
                            "0128010001"
                    );
        }

        @Test
        @DisplayName("should collapse consecutive whitespaces in names")
        void shouldCollapseWhitespacesInNames() throws IOException {
            Path file = createWorkbook("whitespace_names.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0128020000", "City   of   Batac", "City")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows().getFirst().name()).isEqualTo("City of Batac");
        }

        @Test
        @DisplayName("should skip completely empty rows and null rows without errors")
        void shouldSkipCompletelyEmptyRows() throws IOException {
            Path file = createWorkbook("empty_rows.xlsx", "PSGC", Arrays.asList(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg"),
                    List.of("", "", ""),
                    null,
                    List.of("0128000000", "Ilocos Norte", "Prov")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows()).hasSize(2);
            assertThat(result.skippedNonData()).isEqualTo(0);
        }

        @Test
        @DisplayName("should increment skippedNonData for rows with non-code text and blank level")
        void shouldIncrementSkippedNonData_forNonCodeRowsWithBlankLevel() throws IOException {
            Path file = createWorkbook("section_headers.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("REGION I (ILOCOS REGION)", "", ""),
                    List.of("0100000000", "Region I", "Reg")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows()).hasSize(1);
            assertThat(result.skippedNonData()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("read - Special Area Handling")
    class SpecialAreaHandling {

        @Test
        @DisplayName("should parse SPECIAL_AREA when level is blank and code ends with 00000")
        void shouldParseSpecialArea_whenBlankLevelAndProvincePositionCode() throws IOException {
            Path file = createWorkbook("special_area.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("1298000000", "Special Geographic Area", "")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isFalse();
            assertThat(result.rows()).hasSize(1);
            PsgcExcelReader.PsgcRow row = result.rows().getFirst();
            assertThat(row.code()).isEqualTo("1298000000");
            assertThat(row.name()).isEqualTo("Special Geographic Area");
            assertThat(row.areaType()).isEqualTo(AreaType.SPECIAL_AREA);
        }

        @Test
        @DisplayName("should record error when level is blank but code does not end with 00000")
        void shouldRecordError_whenBlankLevelAndCodeNotEndingWithFiveZeros() throws IOException {
            Path file = createWorkbook("invalid_special_area.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0128010001", "Invalid Special Area", "")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).containsExactly(
                    "Row 2: blank level but code 0128010001 is not in province position"
            );
        }
    }

    @Nested
    @DisplayName("read - Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should record error when geographic level is unknown")
        void shouldRecordError_whenLevelIsUnknown() throws IOException {
            Path file = createWorkbook("unknown_level.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Unknown Area", "District")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).containsExactly("Row 2: unknown level 'District'");
        }

        @Test
        @DisplayName("should record error when code is invalid and level is present")
        void shouldRecordError_whenCodeIsInvalid() throws IOException {
            Path file = createWorkbook("invalid_code.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("INVALID_CODE", "Some Place", "Mun")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).containsExactly("Row 2: invalid code 'INVALID_CODE'");
        }

        @Test
        @DisplayName("should record error when name is missing")
        void shouldRecordError_whenNameIsMissing() throws IOException {
            Path file = createWorkbook("missing_name.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "   ", "Reg")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).containsExactly("Row 2: missing name for code 0100000000");
        }

        @Test
        @DisplayName("should record error when duplicate code is encountered")
        void shouldRecordError_whenDuplicateCodeEncountered() throws IOException {
            Path file = createWorkbook("duplicate_code.xlsx", "PSGC", List.of(
                    List.of("10-digit PSGC", "Name", "Geographic Level"),
                    List.of("0100000000", "Region I", "Reg"),
                    List.of("0100000000", "Region I Duplicate", "Reg")
            ));

            PsgcExcelReader.Result result = reader.read(file);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.rows()).hasSize(1);
            assertThat(result.errors()).containsExactly(
                    "Row 3: duplicate code 0100000000 (first seen on row 2)"
            );
        }
    }

    @Nested
    @DisplayName("Result - Helper Methods")
    class ResultHelperMethods {

        @Test
        @DisplayName("should calculate correct counts by AreaType")
        void shouldCalculateCorrectCountsByType() {
            List<PsgcExcelReader.PsgcRow> rows = List.of(
                    new PsgcExcelReader.PsgcRow(2, "0100000000", "Region I", AreaType.REGION),
                    new PsgcExcelReader.PsgcRow(3, "0128000000", "Ilocos Norte", AreaType.PROVINCE),
                    new PsgcExcelReader.PsgcRow(4, "0128010000", "Adams", AreaType.MUNICIPALITY),
                    new PsgcExcelReader.PsgcRow(5, "0128020000", "Batac", AreaType.CITY),
                    new PsgcExcelReader.PsgcRow(6, "0128030000", "Laoag", AreaType.CITY)
            );

            PsgcExcelReader.Result result = new PsgcExcelReader.Result(rows, 0, List.of());

            Map<AreaType, Long> counts = result.countsByType();

            assertThat(counts).containsEntry(AreaType.REGION, 1L)
                    .containsEntry(AreaType.PROVINCE, 1L)
                    .containsEntry(AreaType.MUNICIPALITY, 1L)
                    .containsEntry(AreaType.CITY, 2L);
            assertThat(counts.get(AreaType.BARANGAY)).isNull();
        }
    }

    private Path createWorkbook(String fileName, String sheetName, List<List<Object>> rows) throws IOException {
        Path file = tempDir.resolve(fileName);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);
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
