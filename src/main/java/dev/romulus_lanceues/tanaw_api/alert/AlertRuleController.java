package dev.romulus_lanceues.tanaw_api.alert;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @PostMapping
    public ResponseEntity<AlertRuleResponse> createAlertRule(@Valid @RequestBody AlertRuleRequest request) {
        AlertRuleResponse response = alertRuleService.createAlertRule(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> getAlertRules(
            @RequestParam UUID userId,
            @RequestParam(required = false) UUID locationId) {

        List<AlertRuleResponse> rules = (locationId != null)
                ? alertRuleService.getAlertRulesByLocation(locationId, userId)
                : alertRuleService.getAlertRulesByUser(userId);

        return ResponseEntity.ok(rules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> getAlertRule(
            @PathVariable UUID id,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(alertRuleService.getAlertRule(id, userId));
    }

    @PatchMapping("/{id}/thresholds")
    public ResponseEntity<AlertRuleResponse> updateThresholds(
            @PathVariable UUID id,
            @RequestParam UUID userId,
            @Valid @RequestBody AlertRuleThresholdRequest request) {

        AlertRuleResponse response = alertRuleService.updateThresholds(
                id, userId,
                request.minimumMagnitude(), request.radiusKm(), request.minimumSeverity());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<AlertRuleResponse> enableAlertRule(
            @PathVariable UUID id,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(alertRuleService.enableAlertRule(id, userId));
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<AlertRuleResponse> disableAlertRule(
            @PathVariable UUID id,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(alertRuleService.disableAlertRule(id, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlertRule(
            @PathVariable UUID id,
            @RequestParam UUID userId) {
        alertRuleService.deleteAlertRule(id, userId);
        return ResponseEntity.noContent().build();
    }
}
