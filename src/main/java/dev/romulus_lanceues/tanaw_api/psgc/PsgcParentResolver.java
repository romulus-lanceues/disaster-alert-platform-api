package dev.romulus_lanceues.tanaw_api.psgc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PsgcParentResolver {

    public record Resolution(Map<String, String> parentByCode, List<String> errors){}

    public Resolution resolve (Map<String, AreaType> typeByCode){
        Map<String, String> parentByCode = new HashMap<>();
        List<String> errors = new ArrayList<>();

        typeByCode.forEach((code,type)-> {
            if (type == AreaType.REGION) {
                parentByCode.put(code, null);
                return;
            }

            String parent = findParent(code, type, typeByCode);

            if(parent == null){
                errors.add("No parent found for " + code + " (" + type + ")");
            }else {
                parentByCode.put(code, parent);
            }
        });

        return new Resolution(parentByCode, errors);
    }

    private String findParent(String code, AreaType areaType, Map<String, AreaType> typeByCode){
        String[] candidates = {
                code.substring(0, 7) + "000", //City / Municipality
                code.substring(0, 5) + "00000", //Province / Special Area
                code.substring(0, 2) + "00000000" //Region
        };

        for(String candidate : candidates){
            AreaType candidateType = typeByCode.get(candidate);
            if(!candidate.equals(code) && candidateType != null && candidateType.rank() < areaType.rank()){
                return candidate;
            }

        }
        return null;
    }
}
