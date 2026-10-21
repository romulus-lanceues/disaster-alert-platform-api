package dev.romulus_lanceues.tanaw_api.notification;

/**
 * Summary record capturing the metrics and outcomes of a single dispatcher tick execution.
 *
 * @param fanoutResult               result of the alert fan-out stage (or empty if failed/skipped)
 * @param reapedLeases               number of expired processing notification leases reaped
 * @param batchesClaimed             number of notification batches claimed and drained
 * @param notificationsDispatched    total number of notifications processed through the delivery executor
 */
public record DispatchTickResult(
        FanoutResult fanoutResult,
        int reapedLeases,
        int batchesClaimed,
        int notificationsDispatched
) {
    public static DispatchTickResult empty() {
        return new DispatchTickResult(FanoutResult.empty(), 0, 0, 0);
    }
}
