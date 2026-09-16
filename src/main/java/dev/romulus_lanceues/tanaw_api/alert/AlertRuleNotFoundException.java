package dev.romulus_lanceues.tanaw_api.alert;

public class AlertRuleNotFoundException extends RuntimeException {
    public AlertRuleNotFoundException(String message) {
        super(message);
    }
}
