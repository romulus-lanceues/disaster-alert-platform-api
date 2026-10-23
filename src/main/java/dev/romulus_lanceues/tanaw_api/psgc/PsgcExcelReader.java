package dev.romulus_lanceues.tanaw_api.psgc;

import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PsgcExcelReader {

    public record PsgcRow(int excelRow, String code, String name, AreaType areaType){}

    public record Result(List<PsgcRow> rows, int skippedNonData, List<String> errors){
        public boolean hasErrors(){
            return !errors.isEmpty();
        }

        public Map<AreaType, Long> countsByType(){
            Map<AreaType, Long> counts = new EnumMap<>(AreaType.class);
            rows.forEach(r -> counts.merge(r.areaType, 1L, Long::sum));
            return counts;
        }
    }

    private record Header(int rowIndex, int code, int name, int level){}

    private static final String SHEET_NAME = "PSGC";
    private static final int HEADER_SCAN_ROWS = 20;

    private static final Pattern CODE_CELL = Pattern.compile("[\\s'\u2019]*(\\d{1,10})[\\s'\u2019]*");

    private final DataFormatter formatter = new DataFormatter();

    public Result read(Path file) throws IOException {
        try(Workbook workbook = WorkbookFactory.create(file.toFile(), null, true)){

            Sheet sheet = workbook.getSheet(SHEET_NAME);

            if(sheet == null){
                throw new IllegalStateException("Sheet '" + SHEET_NAME + "' not found in " + file);
            }

            Header header = findHeader(sheet);

            List<PsgcRow> rows = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            Map<String, Integer> seen = new HashMap<>();
            int skippedNonData = 0;

            for(int i = header.rowIndex() + 1; i <= sheet.getLastRowNum(); i++){
                Row row = sheet.getRow(i);
                if(row == null) continue;

                int excelRow = i + 1;

                String rawCode = text(row, header.code());
                String name = text(row, header.name()).replaceAll("\\s+", " ");
                String level = text(row, header.level());

                //Empty row
                if(rawCode.isEmpty() && name.isEmpty() && level.isEmpty()) continue;

                String code = normalizedCode(rawCode);
                AreaType areaType;

                if(level.isEmpty()){
                    if(code == null){
                        skippedNonData++;
                        continue;
                    }

                    if(!code.endsWith("00000")){
                        errors.add("Row " + excelRow + ": blank level but code " + code
                                + " is not in province position");
                        continue;
                    }

                    areaType = AreaType.SPECIAL_AREA;
                } else {
                    Optional<AreaType> t = AreaType.fromExcelLevel(level);
                    if(t.isEmpty()){
                        errors.add("Row " + excelRow + ": unknown level '" + level + "'");
                        continue;
                    }
                    if(code == null){
                        errors.add("Row " + excelRow + ": invalid code '" + rawCode + "'");
                        continue;
                    }

                    areaType = t.get();
                }

                if (name.isEmpty()) {
                    errors.add("Row " + excelRow + ": missing name for code " + code);
                    continue;
                }

                Integer firstRow = seen.putIfAbsent(code, excelRow);
                if(firstRow != null){
                    errors.add("Row " + excelRow + ": duplicate code " + code
                            + " (first seen on row " + firstRow + ")");

                    continue;
                }

                rows.add(new PsgcRow(excelRow, code, name, areaType));
            }

            return new Result(rows, skippedNonData, errors);
        }
    }

    private Header findHeader(Sheet sheet){
        int first = sheet.getFirstRowNum();
        int last = Math.min(sheet.getLastRowNum(),  first + HEADER_SCAN_ROWS);

        for(int i = first; i <= last; i++){
            Row row = sheet.getRow(i);
            if(row == null) continue;

            Map<String, Integer> cols = new HashMap<>();

            for (Cell c : row) {
                cols.put(key(formatter.formatCellValue(c)), c.getColumnIndex());
            }
            if (cols.containsKey("10digitpsgc") && cols.containsKey("name")
                    && cols.containsKey("geographiclevel")) {
                return new Header(i, cols.get("10digitpsgc"), cols.get("name"), cols.get("geographiclevel"));
            }
        }

        throw new IllegalStateException("Header row not found (expected '10-digit PSGC', 'Name' and 'Geographic Level')");
    }

    /**
     "10-digit\nPSGC" -> "10digitpsgc": tolerant to case, spacing, line breaks and dash types.
     */
    private static String key(String header){
        return header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String text(Row row, int col){
        Cell cell = row.getCell(col);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    /** Strips apostrophes/spaces and left-pads to 10 digits; null if the text isn't a code. */
    private static String normalizedCode(String raw){
        Matcher m = CODE_CELL.matcher(raw);
        if (!m.matches()) return null;
        String digits = m.group(1);
        return "0".repeat(10 - digits.length()) + digits;

    }
}
