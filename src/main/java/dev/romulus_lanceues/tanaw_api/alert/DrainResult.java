package dev.romulus_lanceues.tanaw_api.alert;

import java.util.List;

public record DrainResult(
        int batchesProcessed,
        int eventsClaimed,
        int alertsCreated,
        List<CreatedAlert> createdAlerts
) {
}
