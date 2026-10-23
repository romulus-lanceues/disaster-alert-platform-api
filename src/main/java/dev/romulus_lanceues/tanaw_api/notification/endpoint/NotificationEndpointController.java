package dev.romulus_lanceues.tanaw_api.notification.endpoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Notification Endpoints",
        description = "Endpoints for configuring and managing user notification delivery destinations (e.g. Discord webhooks)"
)
@RestController
@RequestMapping("/api/v1/notification-endpoints")
@RequiredArgsConstructor
public class NotificationEndpointController {

    private final NotificationEndpointService notificationEndpointService;

    @Operation(
            summary = "List notification endpoints",
            description = "Retrieves all notification delivery endpoints configured for the authenticated user, ordered by creation date ascending."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Endpoints retrieved successfully",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = NotificationEndpointResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated: valid JWT Bearer token is missing or expired",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<NotificationEndpointResponse>> listEndpoints(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(notificationEndpointService.listEndpoints(userId));
    }

    @Operation(
            summary = "Get a notification endpoint",
            description = "Retrieves a specific notification endpoint after verifying that it belongs to the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Endpoint retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationEndpointResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The path parameter is not a valid UUID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated: valid JWT Bearer token is missing or expired",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No notification endpoint was found for the supplied ID and authenticated user",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<NotificationEndpointResponse> getEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Unique UUID identifier of the notification endpoint", required = true)
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(notificationEndpointService.getEndpoint(userId, id));
    }

    @Operation(
            summary = "Upsert a notification endpoint",
            description = "Creates or updates a notification endpoint for the authenticated user and channel. Resets verified_at to null on upsert."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Endpoint upserted successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationEndpointResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid payload, unsupported notification channel, or destination URL failed validation",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated: valid JWT Bearer token is missing or expired",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PutMapping
    public ResponseEntity<NotificationEndpointResponse> upsertEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpsertNotificationEndpointRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        NotificationEndpointResponse response = notificationEndpointService.upsertEndpoint(
                userId,
                request.channel(),
                request.destination()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Upsert a notification endpoint (POST alias)",
            description = "Creates or updates a notification endpoint for the authenticated user and channel. Alias for PUT."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Endpoint upserted successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationEndpointResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid payload, unsupported notification channel, or destination URL failed validation",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated: valid JWT Bearer token is missing or expired",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    public ResponseEntity<NotificationEndpointResponse> createOrUpdateEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpsertNotificationEndpointRequest request) {
        return upsertEndpoint(jwt, request);
    }

    @Operation(
            summary = "Toggle a notification endpoint",
            description = "Enables or disables a notification endpoint owned by the authenticated user. Disabling an endpoint atomically cancels pending notifications."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Endpoint toggled successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationEndpointResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or UUID parameter",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated: valid JWT Bearer token is missing or expired",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No notification endpoint was found for the supplied ID and authenticated user",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PatchMapping("/{id}")
    public ResponseEntity<NotificationEndpointResponse> toggleEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Unique UUID identifier of the notification endpoint", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody ToggleNotificationEndpointRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        NotificationEndpointResponse response = notificationEndpointService.toggleEndpoint(
                userId,
                id,
                request.enabled()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Delete a notification endpoint",
            description = "Deletes a notification endpoint owned by the authenticated user and atomically cancels any pending notifications enqueued for it."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Notification endpoint deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The path parameter is not a valid UUID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated: valid JWT Bearer token is missing or expired",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No notification endpoint was found for the supplied ID and authenticated user",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Unique UUID identifier of the notification endpoint", required = true)
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        notificationEndpointService.deleteEndpoint(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Test a notification endpoint",
            description = "Sends a test verification message to the endpoint to confirm it is reachable. Marks the endpoint verified on success. Enforces per-user rate limiting."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Test message delivered successfully and endpoint marked verified",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationEndpointResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Unsupported notification channel or invalid destination",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated: valid JWT Bearer token is missing or expired",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No notification endpoint was found for the supplied ID and authenticated user",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Rate limit exceeded: too many test requests in window",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Bad Gateway: upstream webhook test invocation failed",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/{id}/test")
    public ResponseEntity<NotificationEndpointResponse> testEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Unique UUID identifier of the notification endpoint", required = true)
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        NotificationEndpointResponse response = notificationEndpointService.testEndpoint(userId, id);
        return ResponseEntity.ok(response);
    }
}
