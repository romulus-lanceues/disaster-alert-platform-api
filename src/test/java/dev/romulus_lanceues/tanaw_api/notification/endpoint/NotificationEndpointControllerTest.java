package dev.romulus_lanceues.tanaw_api.notification.endpoint;

import dev.romulus_lanceues.tanaw_api.auth.JwtProperties;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAccessDeniedHandler;
import dev.romulus_lanceues.tanaw_api.auth.ProblemDetailsAuthenticationEntryPoint;
import dev.romulus_lanceues.tanaw_api.auth.TestJwtFactory;
import dev.romulus_lanceues.tanaw_api.notification.channel.EndpointVerificationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.UnsupportedNotificationChannelException;
import dev.romulus_lanceues.tanaw_api.shared.config.SecurityConfig;
import dev.romulus_lanceues.tanaw_api.shared.exception.GlobalExceptionHandler;
import dev.romulus_lanceues.tanaw_api.user.UserAuthState;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationEndpointController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "JWT_SECRET=dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
})
@DisplayName("NotificationEndpointController Web Slice Tests")
class NotificationEndpointControllerTest {

    private static final String BASE_URL = "/api/v1/notification-endpoints";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    @MockitoBean
    private NotificationEndpointService notificationEndpointService;

    @MockitoBean
    private UserRepository userRepository;

    private TestJwtFactory jwtFactory;
    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID endpointId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        jwtFactory = new TestJwtFactory(jwtProperties, clock);

        given(userRepository.findAuthState(any(UUID.class)))
                .willReturn(Optional.of(new UserAuthState(UserStatus.ACTIVE, 0L)));
    }

    private NotificationEndpointResponse sampleResponse(boolean enabled, Instant verifiedAt) {
        return new NotificationEndpointResponse(
                endpointId,
                userId,
                NotificationChannel.DISCORD,
                "Discord webhook ...1234",
                enabled,
                verifiedAt,
                enabled ? null : "User disabled endpoint",
                Instant.parse("2026-10-16T10:00:00Z"),
                Instant.parse("2026-10-16T10:00:00Z")
        );
    }

    @Nested
    @DisplayName("Authentication checks")
    class AuthenticationTests {

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void returns401WhenUnauthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("listEndpoints (GET /api/v1/notification-endpoints)")
    class ListEndpoints {

        @Test
        @DisplayName("returns 200 with list of endpoints for authenticated user")
        void returns200WithEndpointsList() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            given(notificationEndpointService.listEndpoints(userId))
                    .willReturn(List.of(sampleResponse(true, Instant.parse("2026-10-16T11:00:00Z"))));

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(endpointId.toString())))
                    .andExpect(jsonPath("$[0].userId", is(userId.toString())))
                    .andExpect(jsonPath("$[0].channel", is("DISCORD")))
                    .andExpect(jsonPath("$[0].destinationHint", is("Discord webhook ...1234")))
                    .andExpect(jsonPath("$[0].enabled", is(true)));

            then(notificationEndpointService).should().listEndpoints(userId);
        }

        @Test
        @DisplayName("returns 200 with empty list when user has no endpoints configured")
        void returns200WithEmptyList() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            given(notificationEndpointService.listEndpoints(userId))
                    .willReturn(List.of());

            mockMvc.perform(get(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            then(notificationEndpointService).should().listEndpoints(userId);
        }
    }

    @Nested
    @DisplayName("getEndpoint (GET /api/v1/notification-endpoints/{id})")
    class GetEndpoint {

        @Test
        @DisplayName("returns 400 ProblemDetail when endpoint ID is not a valid UUID")
        void returns400WhenInvalidUuid() throws Exception {
            String token = jwtFactory.createValidToken(userId);

            mockMvc.perform(get(BASE_URL + "/not-a-valid-uuid")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 200 with endpoint when owned by user")
        void returns200WhenOwned() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            given(notificationEndpointService.getEndpoint(userId, endpointId))
                    .willReturn(sampleResponse(true, null));

            mockMvc.perform(get(BASE_URL + "/" + endpointId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(endpointId.toString())))
                    .andExpect(jsonPath("$.channel", is("DISCORD")))
                    .andExpect(jsonPath("$.enabled", is(true)));
        }

        @Test
        @DisplayName("returns 404 ProblemDetail when endpoint not found or user mismatch")
        void returns404WhenNotFound() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            given(notificationEndpointService.getEndpoint(userId, endpointId))
                    .willThrow(new NotificationEndpointNotFoundException("Notification endpoint not found: " + endpointId));

            mockMvc.perform(get(BASE_URL + "/" + endpointId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Notification Endpoint Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.detail", is("Notification endpoint not found: " + endpointId)));
        }
    }

    @Nested
    @DisplayName("upsertEndpoint (PUT /api/v1/notification-endpoints)")
    class UpsertEndpoint {

        @Test
        @DisplayName("returns 200 with upserted endpoint on valid payload")
        void returns200OnValidUpsert() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = """
                    {
                        "channel": "DISCORD",
                        "destination": "https://discord.com/api/webhooks/123456789/abcdef-1234"
                    }
                    """;

            given(notificationEndpointService.upsertEndpoint(
                    userId,
                    NotificationChannel.DISCORD,
                    "https://discord.com/api/webhooks/123456789/abcdef-1234"
            )).willReturn(sampleResponse(true, null));

            mockMvc.perform(put(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(endpointId.toString())))
                    .andExpect(jsonPath("$.destinationHint", is("Discord webhook ...1234")))
                    .andExpect(jsonPath("$.verifiedAt").doesNotExist());
        }

        @Test
        @DisplayName("returns 200 with upserted endpoint on valid POST payload (alias for PUT)")
        void returns200OnValidUpsertViaPost() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = """
                    {
                        "channel": "DISCORD",
                        "destination": "https://discord.com/api/webhooks/123456789/abcdef-1234"
                    }
                    """;

            given(notificationEndpointService.upsertEndpoint(
                    userId,
                    NotificationChannel.DISCORD,
                    "https://discord.com/api/webhooks/123456789/abcdef-1234"
            )).willReturn(sampleResponse(true, null));

            mockMvc.perform(post(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(endpointId.toString())))
                    .andExpect(jsonPath("$.destinationHint", is("Discord webhook ...1234")));
        }

        @Test
        @DisplayName("returns 400 ProblemDetail when channel is null")
        void returns400WhenChannelIsNull() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = """
                    {
                        "destination": "https://discord.com/api/webhooks/123456789/abcdef-1234"
                    }
                    """;

            mockMvc.perform(put(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.channel").exists());
        }

        @Test
        @DisplayName("returns 400 ProblemDetail when destination is blank")
        void returns400WhenDestinationIsBlank() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = """
                    {
                        "channel": "DISCORD",
                        "destination": "   "
                    }
                    """;

            mockMvc.perform(put(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.destination").exists());
        }

        @Test
        @DisplayName("returns 400 ProblemDetail when destination exceeds 2048 characters")
        void returns400WhenDestinationExceedsMaxLength() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String longDestination = "https://discord.com/api/webhooks/123/" + "a".repeat(2048);
            String payload = String.format("""
                    {
                        "channel": "DISCORD",
                        "destination": "%s"
                    }
                    """, longDestination);

            mockMvc.perform(put(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.destination").exists());
        }

        @Test
        @DisplayName("returns 400 ProblemDetail when channel is unsupported")
        void returns400WhenUnsupportedChannel() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = """
                    {
                        "channel": "TELEGRAM",
                        "destination": "123456789"
                    }
                    """;

            given(notificationEndpointService.upsertEndpoint(eq(userId), eq(NotificationChannel.TELEGRAM), any()))
                    .willThrow(new UnsupportedNotificationChannelException(NotificationChannel.TELEGRAM));

            mockMvc.perform(put(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Unsupported Notification Channel")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail").value("Notification channel 'TELEGRAM' is not supported yet."));
        }

        @Test
        @DisplayName("returns 400 ProblemDetail when destination fails validation (SSRF / format)")
        void returns400WhenInvalidDestination() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = """
                    {
                        "channel": "DISCORD",
                        "destination": "http://169.254.169.254/secret"
                    }
                    """;

            given(notificationEndpointService.upsertEndpoint(eq(userId), eq(NotificationChannel.DISCORD), any()))
                    .willThrow(new InvalidDestinationException("Discord webhook URL must use HTTPS scheme"));

            mockMvc.perform(put(BASE_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid Notification Destination")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail").value("Discord webhook URL must use HTTPS scheme"));
        }
    }

    @Nested
    @DisplayName("toggleEndpoint (PATCH /api/v1/notification-endpoints/{id})")
    class ToggleEndpoint {

        @Test
        @DisplayName("returns 200 with updated endpoint when toggled")
        void returns200OnValidToggle() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = """
                    {
                        "enabled": false
                    }
                    """;

            given(notificationEndpointService.toggleEndpoint(userId, endpointId, false))
                    .willReturn(sampleResponse(false, null));

            mockMvc.perform(patch(BASE_URL + "/" + endpointId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled", is(false)))
                    .andExpect(jsonPath("$.disabledReason", is("User disabled endpoint")));
        }

        @Test
        @DisplayName("returns 400 ProblemDetail when enabled flag is null")
        void returns400WhenEnabledIsNull() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = "{}";

            mockMvc.perform(patch(BASE_URL + "/" + endpointId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Validation Failed")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.enabled").exists());
        }

        @Test
        @DisplayName("returns 404 ProblemDetail when endpoint not found or user mismatch")
        void returns404WhenNotFound() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            String payload = """
                    {
                        "enabled": true
                    }
                    """;

            given(notificationEndpointService.toggleEndpoint(userId, endpointId, true))
                    .willThrow(new NotificationEndpointNotFoundException("Notification endpoint not found: " + endpointId));

            mockMvc.perform(patch(BASE_URL + "/" + endpointId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Notification Endpoint Not Found")))
                    .andExpect(jsonPath("$.status", is(404)));
        }
    }

    @Nested
    @DisplayName("deleteEndpoint (DELETE /api/v1/notification-endpoints/{id})")
    class DeleteEndpoint {

        @Test
        @DisplayName("returns 204 No Content when deleted successfully")
        void returns204WhenDeleted() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            willDoNothing().given(notificationEndpointService).deleteEndpoint(userId, endpointId);

            mockMvc.perform(delete(BASE_URL + "/" + endpointId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNoContent());

            then(notificationEndpointService).should().deleteEndpoint(userId, endpointId);
        }

        @Test
        @DisplayName("returns 404 ProblemDetail when endpoint not found or user mismatch")
        void returns404WhenNotFound() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            willThrow(new NotificationEndpointNotFoundException("Notification endpoint not found: " + endpointId))
                    .given(notificationEndpointService).deleteEndpoint(userId, endpointId);

            mockMvc.perform(delete(BASE_URL + "/" + endpointId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Notification Endpoint Not Found")))
                    .andExpect(jsonPath("$.status", is(404)));
        }
    }

    @Nested
    @DisplayName("testEndpoint (POST /api/v1/notification-endpoints/{id}/test)")
    class TestEndpoint {

        @Test
        @DisplayName("returns 200 with verified endpoint on successful test verification")
        void returns200OnSuccessfulTest() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            Instant verifiedAt = Instant.parse("2026-10-16T12:00:00Z");
            given(notificationEndpointService.testEndpoint(userId, endpointId))
                    .willReturn(sampleResponse(true, verifiedAt));

            mockMvc.perform(post(BASE_URL + "/" + endpointId + "/test")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(endpointId.toString())))
                    .andExpect(jsonPath("$.verifiedAt", is("2026-10-16T12:00:00Z")));
        }

        @Test
        @DisplayName("returns 429 ProblemDetail and Retry-After header when rate limited")
        void returns429WhenRateLimited() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            given(notificationEndpointService.testEndpoint(userId, endpointId))
                    .willThrow(new RateLimitExceededException("Rate limit exceeded for endpoint testing. Try again in 45 seconds.", Duration.ofSeconds(45)));

            mockMvc.perform(post(BASE_URL + "/" + endpointId + "/test")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"))
                    .andExpect(jsonPath("$.title", is("Too Many Requests")))
                    .andExpect(jsonPath("$.status", is(429)))
                    .andExpect(jsonPath("$.retryAfterSeconds", is(45)))
                    .andExpect(jsonPath("$.detail", is("Rate limit exceeded for endpoint testing. Try again in 45 seconds.")));
        }

        @Test
        @DisplayName("returns 502 ProblemDetail when upstream verification fails")
        void returns502WhenVerificationFails() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            given(notificationEndpointService.testEndpoint(userId, endpointId))
                    .willThrow(new EndpointVerificationException("Discord webhook verification failed: HTTP 404"));

            mockMvc.perform(post(BASE_URL + "/" + endpointId + "/test")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.title", is("Endpoint Verification Failed")))
                    .andExpect(jsonPath("$.status", is(502)))
                    .andExpect(jsonPath("$.detail", is("Discord webhook verification failed: HTTP 404")));
        }

        @Test
        @DisplayName("returns 404 ProblemDetail when endpoint not found or user mismatch")
        void returns404WhenNotFound() throws Exception {
            String token = jwtFactory.createValidToken(userId);
            given(notificationEndpointService.testEndpoint(userId, endpointId))
                    .willThrow(new NotificationEndpointNotFoundException("Notification endpoint not found: " + endpointId));

            mockMvc.perform(post(BASE_URL + "/" + endpointId + "/test")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Notification Endpoint Not Found")))
                    .andExpect(jsonPath("$.status", is(404)));
        }
    }
}
