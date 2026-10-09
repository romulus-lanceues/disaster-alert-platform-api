package dev.romulus_lanceues.tanaw_api.alert;

import java.util.UUID;

public record CreatedAlerts(
        UUID id,
        UUID alertRuleId,
        UUID disasterEventId
) {}
