package dev.romulus_lanceues.tanaw_api.psgc;

import java.util.Optional;

public enum AreaType {

    REGION(1, "Reg"),
    PROVINCE(2, "Prov"),
    SPECIAL_AREA(2, null),
    CITY(3, "City"),
    MUNICIPALITY(3, "Mun"),
    SUB_MUNICIPALITY(4, "SubMun"),
    BARANGAY(5, "Bgy");

    private final int rank;
    private final String excelLevel;

    AreaType(int rank, String excelLevel) {
        this.rank = rank;
        this.excelLevel = excelLevel;
    }

    public int rank(){
        return rank;
    }

    public static Optional<AreaType> fromExcelLevel(String excelLevel){
        for(AreaType type : values()){
            if(type.excelLevel != null && type.excelLevel.equalsIgnoreCase(excelLevel)){
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }
}
