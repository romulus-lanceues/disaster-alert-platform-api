package dev.romulus_lanceues.tanaw_api.alert.rule;

public class AlertRuleNotFoundException extends RuntimeException {
    public AlertRuleNotFoundException(String message) {
        super(message);
    }
}
