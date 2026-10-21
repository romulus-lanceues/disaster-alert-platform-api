package dev.romulus_lanceues.tanaw_api.notification.dispatch;

public record FanoutResult(
        int alertsClaimed,
        int alertsCancelledStale,
        int alertsProcessed,
        int notificationsCreated
) {
    public static FanoutResult empty() {
        return new FanoutResult(0, 0, 0, 0);
    }
}
