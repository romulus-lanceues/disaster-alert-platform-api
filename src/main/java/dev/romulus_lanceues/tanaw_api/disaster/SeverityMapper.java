package dev.romulus_lanceues.tanaw_api.disaster;

import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeatureProperties;
import org.springframework.stereotype.Component;

@Component
public class SeverityMapper {

    public Severity fromUsgs(UsgsFeatureProperties properties) {

        return calculateEarthquakeSeverity(
                fromAlertLevel(properties.alert()), fromMagnitude(properties.mag())
        );

    }

    private Severity fromAlertLevel(String alert){

        if(alert == null) return  Severity.LOW;

        return switch (alert){
            case "red" -> Severity.CRITICAL;
            case "orange" -> Severity.HIGH;
            case "yellow" -> Severity.MODERATE;
            default -> Severity.LOW;
        };
    }

    private Severity fromMagnitude(Double magnitude){

        if(magnitude == null) return Severity.LOW;
        if (magnitude >= 7.0) return Severity.CRITICAL;
        if (magnitude >= 5.5) return Severity.HIGH;
        if (magnitude >= 4.0) return Severity.MODERATE;
        return Severity.LOW;
        };


    private Severity calculateEarthquakeSeverity(Severity fromAlertLevel, Severity fromMagnitude) {
        return fromAlertLevel.compareTo(fromMagnitude) >= 0 ? fromAlertLevel : fromMagnitude;
    }
}



