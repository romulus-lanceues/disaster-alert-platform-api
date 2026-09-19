package dev.romulus_lanceues.tanaw_api.location;

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
import org.springframework.web.bind.annotation.GetMapping;
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

@Tag(name = "Locations", description = "Endpoints for managing user locations, geographic coordinates, and area associations")
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @Operation(
            summary = "Create a new location",
            description = "Creates a new location for a user with coordinates and an associated geographic area. Returns the created location details along with a Location header pointing to the new resource."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Location created successfully",
                    headers = @Header(name = "Location", description = "URI of the newly created location resource", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LocationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or validation constraint failure",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Referenced user or geographic area not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody LocationRequest request) {
        LocationResponse response = locationService.createLocation(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Operation(
            summary = "List locations by user",
            description = "Retrieves all locations belonging to the specified user."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Locations retrieved successfully",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = LocationResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No user found with the given UUID",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<LocationResponse>> getLocationsByUser(
            @Parameter(description = "Unique UUID identifier of the user", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
            @RequestParam UUID userId) {
        return ResponseEntity.ok(locationService.getLocationsByUser(userId));
    }

    @Operation(
            summary = "Get location by ID and user",
            description = "Retrieves a specific location by its unique UUID identifier and verifying user ownership."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Location found successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LocationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No location found with the given UUID for the specified user",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<LocationResponse> getLocation(
            @Parameter(description = "Unique UUID identifier of the location", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Unique UUID identifier of the user who owns the location", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
            @RequestParam UUID userId) {
        return ResponseEntity.ok(locationService.getLocation(id, userId));
    }
}
