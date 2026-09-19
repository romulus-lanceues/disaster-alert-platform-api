package dev.romulus_lanceues.tanaw_api.alert.rule;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Alert Rules", description = "Endpoints for configuring disaster alert rules for user locations")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @Operation(
            summary = "Create an alert rule",
            description = "Creates an enabled alert rule for a location owned by the supplied user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Alert rule created successfully",
                    headers = @Header(
                            name = "Location",
                            description = "URI of the newly created alert rule resource",
                            schema = @Schema(type = "string")
                    ),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertRuleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or validation constraint failure",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The referenced location was not found for the supplied user",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
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

    @Operation(
            summary = "List alert rules",
            description = "Retrieves all alert rules owned by a user, optionally filtered to a location owned by that user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alert rules retrieved successfully",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AlertRuleResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The userId or locationId query parameter is missing or is not a valid UUID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> getAlertRules(
            @Parameter(
                    description = "Unique UUID identifier of the user who owns the alert rules",
                    example = "123e4567-e89b-12d3-a456-426614174000",
                    required = true
            )
            @RequestParam UUID userId,
            @Parameter(
                    description = "Optional unique UUID identifier of a location owned by the user",
                    example = "123e4567-e89b-12d3-a456-426614174001"
            )
            @RequestParam(required = false) UUID locationId) {

        List<AlertRuleResponse> rules = (locationId != null)
                ? alertRuleService.getAlertRulesByLocation(locationId, userId)
                : alertRuleService.getAlertRulesByUser(userId);

        return ResponseEntity.ok(rules);
    }

    @Operation(
            summary = "Get an alert rule",
            description = "Retrieves a specific alert rule after verifying that it belongs to the supplied user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alert rule found successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertRuleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The path or query parameter is not a valid UUID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No alert rule was found for the supplied alert rule and user UUIDs",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> getAlertRule(
            @Parameter(
                    description = "Unique UUID identifier of the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174002",
                    required = true
            )
            @PathVariable UUID id,
            @Parameter(
                    description = "Unique UUID identifier of the user who owns the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174000",
                    required = true
            )
            @RequestParam UUID userId) {
        return ResponseEntity.ok(alertRuleService.getAlertRule(id, userId));
    }

    @Operation(
            summary = "Update alert rule thresholds",
            description = "Replaces the threshold values for an alert rule. Null threshold values clear the corresponding filters."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alert rule thresholds updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertRuleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload, negative threshold, or invalid UUID parameter",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No alert rule was found for the supplied alert rule and user UUIDs",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PatchMapping("/{id}/thresholds")
    public ResponseEntity<AlertRuleResponse> updateThresholds(
            @Parameter(
                    description = "Unique UUID identifier of the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174002",
                    required = true
            )
            @PathVariable UUID id,
            @Parameter(
                    description = "Unique UUID identifier of the user who owns the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174000",
                    required = true
            )
            @RequestParam UUID userId,
            @Valid @RequestBody AlertRuleThresholdRequest request) {

        AlertRuleResponse response = alertRuleService.updateThresholds(
                id, userId,
                request.minimumMagnitude(), request.radiusKm(), request.minimumSeverity());

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Enable an alert rule",
            description = "Enables an alert rule after verifying that it belongs to the supplied user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alert rule enabled successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertRuleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The path or query parameter is not a valid UUID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No alert rule was found for the supplied alert rule and user UUIDs",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PatchMapping("/{id}/enable")
    public ResponseEntity<AlertRuleResponse> enableAlertRule(
            @Parameter(
                    description = "Unique UUID identifier of the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174002",
                    required = true
            )
            @PathVariable UUID id,
            @Parameter(
                    description = "Unique UUID identifier of the user who owns the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174000",
                    required = true
            )
            @RequestParam UUID userId) {
        return ResponseEntity.ok(alertRuleService.enableAlertRule(id, userId));
    }

    @Operation(
            summary = "Disable an alert rule",
            description = "Disables an alert rule after verifying that it belongs to the supplied user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alert rule disabled successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertRuleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The path or query parameter is not a valid UUID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No alert rule was found for the supplied alert rule and user UUIDs",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PatchMapping("/{id}/disable")
    public ResponseEntity<AlertRuleResponse> disableAlertRule(
            @Parameter(
                    description = "Unique UUID identifier of the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174002",
                    required = true
            )
            @PathVariable UUID id,
            @Parameter(
                    description = "Unique UUID identifier of the user who owns the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174000",
                    required = true
            )
            @RequestParam UUID userId) {
        return ResponseEntity.ok(alertRuleService.disableAlertRule(id, userId));
    }

    @Operation(
            summary = "Delete an alert rule",
            description = "Deletes an alert rule after verifying that it belongs to the supplied user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alert rule deleted successfully"),
            @ApiResponse(
                    responseCode = "400",
                    description = "The path or query parameter is not a valid UUID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No alert rule was found for the supplied alert rule and user UUIDs",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlertRule(
            @Parameter(
                    description = "Unique UUID identifier of the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174002",
                    required = true
            )
            @PathVariable UUID id,
            @Parameter(
                    description = "Unique UUID identifier of the user who owns the alert rule",
                    example = "123e4567-e89b-12d3-a456-426614174000",
                    required = true
            )
            @RequestParam UUID userId) {
        alertRuleService.deleteAlertRule(id, userId);
        return ResponseEntity.noContent().build();
    }
}
