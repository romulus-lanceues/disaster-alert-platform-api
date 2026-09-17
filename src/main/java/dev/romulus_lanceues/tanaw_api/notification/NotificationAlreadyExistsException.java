package dev.romulus_lanceues.tanaw_api.notification;

public class NotificationAlreadyExistsException extends RuntimeException {
    public NotificationAlreadyExistsException(String message) {
        super(message);
    }
}
