package dev.romulus_lanceues.tanaw_api.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AlertMatchingService {

    private final AlertMatchingRepository repository;

    public List<CreatedAlert> createAlertForEvents(List<UUID> eventIds){

        if (eventIds == null || eventIds.isEmpty()) return List.of();

        List<CreatedAlert> createdAlerts = repository.persistAlert(eventIds);

        long matchedEventsCount = createdAlerts.stream()
                .map(CreatedAlert::disasterEventId)
                .distinct()
                .count();

        log.info("Evaluated {} event(s): {} matched rules, created {} alert(s)",
                eventIds.size(), matchedEventsCount, createdAlerts.size());

        return createdAlerts;
    }

}
