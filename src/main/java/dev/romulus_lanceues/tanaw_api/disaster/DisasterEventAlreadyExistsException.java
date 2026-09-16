package dev.romulus_lanceues.tanaw_api.disaster;

public class DisasterEventAlreadyExistsException extends RuntimeException {
    public DisasterEventAlreadyExistsException(String message) {
        super(message);
    }
}
