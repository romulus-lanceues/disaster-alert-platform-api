package dev.romulus_lanceues.tanaw_api.location;

public class LocationNotFoundException extends RuntimeException {
    public LocationNotFoundException(String message) {
        super(message);
    }
}
