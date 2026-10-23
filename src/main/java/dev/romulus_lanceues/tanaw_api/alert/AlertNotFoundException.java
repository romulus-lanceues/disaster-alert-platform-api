package dev.romulus_lanceues.tanaw_api.alert;

public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(String message) {
        super(message);
    }
}
