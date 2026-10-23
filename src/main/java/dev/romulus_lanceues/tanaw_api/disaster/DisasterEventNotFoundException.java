package dev.romulus_lanceues.tanaw_api.disaster;

public class DisasterEventNotFoundException extends RuntimeException {
    public DisasterEventNotFoundException(String message) {
        super(message);
    }
}
