package dev.romulus_lanceues.tanaw_api.alert;

import java.util.List;

public record BatchMatchResult(
        int claimedCount,
        int createdAlertsCount,
        List<CreatedAlert> createdAlerts
) {
}
