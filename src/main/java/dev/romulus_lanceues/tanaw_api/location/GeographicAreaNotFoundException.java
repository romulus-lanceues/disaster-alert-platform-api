package dev.romulus_lanceues.tanaw_api.location;

public class GeographicAreaNotFoundException extends RuntimeException {
    public GeographicAreaNotFoundException(String message) {
        super(message);
    }
}
