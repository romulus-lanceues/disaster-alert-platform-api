package dev.romulus_lanceues.tanaw_api.notification;

/**
 * Service contract for channel-specific notification delivery (e.g. Discord, Telegram, Email).
 */
public interface NotificationSender {

    /**
     * The notification channel supported by this sender.
     */
    NotificationChannel channel();

    /**
     * Delivers an alert message to the given destination endpoint.
     *
     * @param message     the formatted channel-neutral alert message
     * @param destination the unencrypted destination URI or target identifier
     * @return the classified delivery outcome (Sent, Retryable, or Permanent)
     */
    DeliveryResult send(AlertMessage message, String destination);
}
