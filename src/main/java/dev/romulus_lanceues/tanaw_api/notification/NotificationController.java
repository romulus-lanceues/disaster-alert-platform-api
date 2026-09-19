package dev.romulus_lanceues.tanaw_api.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(
        name = "Notifications",
        description = "Read-only notification delivery history for users. Notification lifecycle mutations are internal worker operations and are not exposed by this API."
)
public class NotificationController {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final NotificationService notificationService;

    @Operation(
            summary = "List notifications",
            description = "Retrieves a newest-first page of notifications owned by the supplied user. Results can optionally be filtered by delivery status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notifications retrieved successfully. The page content contains NotificationResponse entries.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A required parameter is missing or invalid, or the page number or size is outside the allowed range.",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping
    public Page<NotificationResponse> getNotifications(
            @Parameter(
                    description = "Unique UUID identifier of the user who owns the notifications",
                    example = "123e4567-e89b-12d3-a456-426614174000",
                    required = true
            )
            @RequestParam UUID userId,
            @Parameter(
                    description = "Optional delivery status used to filter notifications",
                    example = "SENT"
            )
            @RequestParam(required = false) NotificationStatus status,
            @Parameter(
                    description = "Zero-based page index",
                    example = "0"
            )
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(
                    description = "Maximum number of notifications per page; must be between 1 and 10",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") @Min(1) @Max(DEFAULT_PAGE_SIZE) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationService.getNotificationsForUser(userId, status, pageable);
    }

    @Operation(
            summary = "Get a notification",
            description = "Retrieves one notification after verifying that it belongs to the supplied user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notification retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The notification ID or user ID is missing or is not a valid UUID.",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No notification was found for the supplied notification and user UUIDs.",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    public NotificationResponse getNotification(
            @Parameter(
                    description = "Unique UUID identifier of the notification",
                    example = "123e4567-e89b-12d3-a456-426614174001",
                    required = true
            )
            @PathVariable UUID id,
            @Parameter(
                    description = "Unique UUID identifier of the user who owns the notification",
                    example = "123e4567-e89b-12d3-a456-426614174000",
                    required = true
            )
            @RequestParam UUID userId) {
        return notificationService.getNotificationForUser(id, userId);
    }
}
