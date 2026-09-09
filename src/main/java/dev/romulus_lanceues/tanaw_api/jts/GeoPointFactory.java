package dev.romulus_lanceues.tanaw_api.jts;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

@Component
public class GeoPointFactory {

    private static final int SRID = 4326;

    private final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), SRID);

    public Point create(double latitude, double longitude){
        validateCoordinates(latitude, longitude);

        return geometryFactory.createPoint(new Coordinate(longitude, latitude));
    }

    private void validateCoordinates(double latitude, double longitude){
        if(latitude < -90 || latitude > 90){
            throw new IllegalArgumentException("Invalid latitude");
        }

        if(longitude < -180 || longitude > 180){
            throw new IllegalArgumentException("Invalid longitude");
        }
    }

}
