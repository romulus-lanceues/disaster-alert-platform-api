package dev.romulus_lanceues.tanaw_api.notification.channel;

public class InvalidDestinationException extends RuntimeException {

    public InvalidDestinationException(String message) {
        super(message);
    }

    public InvalidDestinationException(String message, Throwable cause) {
        super(message, cause);
    }
}
