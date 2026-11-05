package dev.romulus_lanceues.tanaw_api.ops;

import dev.romulus_lanceues.tanaw_api.ops.dto.SimulateEarthquakeRequest;
import dev.romulus_lanceues.tanaw_api.ops.dto.SimulatedEarthquakeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ops/simulations")
@RequiredArgsConstructor
@Tag(name = "Ops Simulation", description = "Operational disaster simulation endpoints")
public class OpsSimulationController {

    private final OpsSimulationService opsSimulationService;

    @Operation(
            summary = "Simulate an earthquake disaster event",
            description = "Targeted simulation of an earthquake disaster event against an alert rule without notifying other users."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Simulated earthquake processed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SimulatedEarthquakeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid simulation request payload or constraints violated",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, empty, or invalid X-Ops-Key header",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ops simulations disabled or alert rule not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/earthquake")
    public ResponseEntity<SimulatedEarthquakeResponse> simulateEarthquake(
            @Valid @RequestBody SimulateEarthquakeRequest request
    ) {
        SimulatedEarthquakeResponse response = opsSimulationService.simulateEarthquake(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Cleanup a simulated disaster event",
            description = "Deletes a simulated disaster event and its associated cascaded alerts. Returns 404 when ops simulations are disabled or event not found."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Simulated disaster event cleaned up successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, empty, or invalid X-Ops-Key header",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ops simulations disabled or disaster event not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @DeleteMapping("/{disasterEventId}")
    public ResponseEntity<Void> cleanup(@PathVariable UUID disasterEventId) {
        opsSimulationService.cleanup(disasterEventId);
        return ResponseEntity.noContent().build();
    }
}
