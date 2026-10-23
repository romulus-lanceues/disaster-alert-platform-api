package dev.romulus_lanceues.tanaw_api.alert;

public class AlertAlreadyExistsException extends RuntimeException {
    public AlertAlreadyExistsException(String message) {
        super(message);
    }
}
