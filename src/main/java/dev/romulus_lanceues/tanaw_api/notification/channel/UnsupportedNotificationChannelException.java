package dev.romulus_lanceues.tanaw_api.notification.channel;

public class UnsupportedNotificationChannelException extends RuntimeException {

    private final NotificationChannel channel;

    public UnsupportedNotificationChannelException(NotificationChannel channel) {
        super("Notification channel '" + channel + "' is not supported yet.");
        this.channel = channel;
    }

    public NotificationChannel getChannel() {
        return channel;
    }
}
