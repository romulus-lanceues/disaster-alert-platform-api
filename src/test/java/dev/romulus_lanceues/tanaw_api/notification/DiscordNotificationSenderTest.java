package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("DiscordNotificationSender Unit Tests")
class DiscordNotificationSenderTest {

    private static final String BASE_WEBHOOK = "https://discord.com/api/webhooks/1234567890/secret-token-xyz-1234";
    private static final String EXPECTED_REQUEST_URI = BASE_WEBHOOK + "?wait=true";
    private static final String INVALID_WEBHOOK = "https://malicious.example.com/api/webhooks/1234567890/secret-token";
    private static final String SECRET_TOKEN = "secret-token-xyz-1234";
    private static final Instant FIXED_NOW = Instant.parse("2026-10-17T10:00:00Z");

    private final DiscordDestinationValidator validator = new DiscordDestinationValidator();
    private final DestinationMasker masker = new DestinationMasker();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer mockServer;
    private RestClient.Builder restClientBuilder;

    private NotificationProperties createProperties(boolean dryRun) {
        return new NotificationProperties(
                dryRun,
                new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                new NotificationProperties.Fanout(Duration.ofHours(6)),
                new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new NotificationProperties.Crypto("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="),
                null
        );
    }

    private AlertMessage createSampleMessage(String severity) {
        return new AlertMessage(
                "M5.4 earthquake — 23 km from Home",
                "24 km SSW of Balangiga, Philippines",
                5.4,
                "M5.4",
                23.4,
                "23 km from Home",
                "Home",
                10.0,
                "10.0 km",
                severity != null ? severity : "MODERATE",
                Instant.parse("2026-10-17T09:43:00Z"),
                "2026-10-17 17:43:00 PHT",
                "2026-10-17 09:43:00 UTC",
                "automatic",
                "Preliminary report: details may be revised upon review by USGS.",
                "https://earthquake.usgs.gov/earthquakes/eventpage/us7000test",
                "Tanaw Alert • USGS Event Monitoring"
        );
    }

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private DiscordNotificationSender createSender(boolean dryRun) {
        return new DiscordNotificationSender(
                createProperties(dryRun),
                validator,
                masker,
                objectMapper,
                restClientBuilder.build()
        );
    }

    @Test
    @DisplayName("channel returns NotificationChannel.DISCORD")
    void channelReturnsDiscord() {
        DiscordNotificationSender sender = createSender(false);
        assertThat(sender.channel()).isEqualTo(NotificationChannel.DISCORD);
    }

    @Test
    @DisplayName("Invalid / SSRF webhook destination returns Permanent failure with invalidateEndpoint = true")
    void invalidDestinationReturnsPermanentFailure() {
        DiscordNotificationSender sender = createSender(false);

        DeliveryResult result = sender.send(createSampleMessage("MODERATE"), INVALID_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
        DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
        assertThat(permanent.invalidateEndpoint()).isTrue();
        assertThat(permanent.reason()).contains("Invalid Discord webhook URL");
        assertThat(permanent.reason()).doesNotContain(SECRET_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("Dry-run mode renders message and logs without HTTP request, returning Sent('dry-run')")
    void dryRunSkipsHttpRequestAndReturnsSentDryRun() {
        DiscordNotificationSender sender = createSender(true);

        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
        DeliveryResult.Sent sent = (DeliveryResult.Sent) result;
        assertThat(sent.providerMessageId()).isEqualTo("dry-run");

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 200 with ID returns Sent with providerMessageId, ?wait=true, and mention suppression")
    void http200WithIdReturnsSent() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.username").value("Tanaw Alerts"))
                .andExpect(jsonPath("$.allowed_mentions.parse").isArray())
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andExpect(jsonPath("$.embeds[0].title").value("M5.4 earthquake — 23 km from Home"))
                .andExpect(jsonPath("$.embeds[0].color").value(15844367)) // MODERATE = Yellow
                .andExpect(jsonPath("$.embeds[0].fields").isArray())
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\": \"1301234567890123456\", \"type\": 0}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("MODERATE"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
        DeliveryResult.Sent sent = (DeliveryResult.Sent) result;
        assertThat(sent.providerMessageId()).isEqualTo("1301234567890123456");

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 204 No Content returns Sent with null providerMessageId fallback")
    void http204ReturnsSentWithNullFallback() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("CRITICAL"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
        DeliveryResult.Sent sent = (DeliveryResult.Sent) result;
        assertThat(sent.providerMessageId()).isNull();

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 200 without ID returns Sent with null fallback")
    void http200WithoutIdReturnsSentWithNullFallback() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("LOW"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
        DeliveryResult.Sent sent = (DeliveryResult.Sent) result;
        assertThat(sent.providerMessageId()).isNull();

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 429 parses Retry-After header and returns Retryable with retryAfter duration")
    void http429WithRetryAfterHeaderReturnsRetryable() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "3.5");

        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"You are being rate limited.\"}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
        DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
        assertThat(retryable.retryAfter()).isEqualTo(Duration.ofMillis(3500));
        assertThat(retryable.reason()).contains("429");
        assertThat(retryable.reason()).doesNotContain(SECRET_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 429 parses retry_after from JSON body when header is absent")
    void http429WithRetryAfterJsonBodyReturnsRetryable() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"You are being rate limited.\", \"retry_after\": 2.0}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
        DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
        assertThat(retryable.retryAfter()).isEqualTo(Duration.ofMillis(2000));
        assertThat(retryable.reason()).contains("429");
        assertThat(retryable.reason()).doesNotContain(SECRET_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 429 without retry_after returns Retryable with null retryAfter")
    void http429WithoutRetryAfterReturnsRetryableWithNull() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"You are being rate limited.\"}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
        DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
        assertThat(retryable.retryAfter()).isNull();
        assertThat(retryable.reason()).contains("429");

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 404 returns Permanent failure with invalidateEndpoint = true and redacted reason")
    void http404ReturnsPermanentWithInvalidateEndpointTrue() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"Unknown Webhook\", \"code\": 10015}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
        DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
        assertThat(permanent.invalidateEndpoint()).isTrue();
        assertThat(permanent.reason()).contains("404");
        assertThat(permanent.reason()).doesNotContain(SECRET_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 401 returns Permanent failure with invalidateEndpoint = true and redacted reason")
    void http401ReturnsPermanentWithInvalidateEndpointTrue() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"401: Unauthorized\", \"code\": 0}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
        DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
        assertThat(permanent.invalidateEndpoint()).isTrue();
        assertThat(permanent.reason()).contains("401");
        assertThat(permanent.reason()).doesNotContain(SECRET_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 400 Bad Request returns Permanent failure with invalidateEndpoint = false")
    void http400ReturnsPermanentWithInvalidateEndpointFalse() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"Invalid Form Body\"}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
        DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
        assertThat(permanent.invalidateEndpoint()).isFalse();
        assertThat(permanent.reason()).contains("400");
        assertThat(permanent.reason()).doesNotContain(SECRET_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 500 Server Error returns Retryable with null retryAfter")
    void http500ReturnsRetryable() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"Internal Server Error\"}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
        DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
        assertThat(retryable.retryAfter()).isNull();
        assertThat(retryable.reason()).contains("500");
        assertThat(retryable.reason()).doesNotContain(SECRET_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("SocketTimeoutException returns Retryable with null retryAfter and redacted reason")
    void timeoutReturnsRetryable() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new SocketTimeoutException("Read timed out on https://discord.com/api/webhooks/123/" + SECRET_TOKEN)));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
        DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
        assertThat(retryable.retryAfter()).isNull();
        assertThat(retryable.reason()).doesNotContain(SECRET_TOKEN);
        assertThat(retryable.reason()).containsIgnoringCase("timed out");

        mockServer.verify();
    }

    @Test
    @DisplayName("Connection I/O error returns Retryable with null retryAfter and redacted reason")
    void connectionErrorReturnsRetryable() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Connection refused to https://discord.com/api/webhooks/123/" + SECRET_TOKEN)));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
        DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
        assertThat(retryable.retryAfter()).isNull();
        assertThat(retryable.reason()).doesNotContain(SECRET_TOKEN);
        assertThat(retryable.reason()).containsIgnoringCase("Connection");

        mockServer.verify();
    }

    @Test
    @DisplayName("Embed character limits are guarded and truncated to avoid Discord 400 rejection")
    void characterLimitGuardingTruncatesOversizedFields() {
        String oversizedTitle = "M7.5 " + "A".repeat(300);
        String oversizedPlace = "Manila " + "B".repeat(1500);
        String oversizedFooter = "Tanaw " + "C".repeat(2500);

        AlertMessage oversizedMessage = new AlertMessage(
                oversizedTitle,
                oversizedPlace,
                7.5,
                "M7.5",
                10.0,
                "10 km away",
                "Home",
                5.0,
                "5.0 km",
                "CRITICAL",
                FIXED_NOW,
                "Time",
                "Time UTC",
                "automatic",
                "Notice",
                "https://earthquake.usgs.gov",
                oversizedFooter
        );

        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.embeds[0].title").value(org.hamcrest.Matchers.hasLength(256)))
                .andExpect(jsonPath("$.embeds[0].color").value(15158332)) // CRITICAL = Red
                .andExpect(jsonPath("$.embeds[0].footer.text").value(org.hamcrest.Matchers.hasLength(2048)))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(oversizedMessage, BASE_WEBHOOK);

        assertThat(result.isSent()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("Untrusted markdown formatting and user mentions in message fields are escaped")
    void untrustedMarkdownAndMentionsAreEscapedInEmbed() {
        AlertMessage injectionMessage = new AlertMessage(
                "M5.0 *Earthquake* [Click Here](http://evil.com) @everyone",
                "Place **bold** ||spoiler||",
                5.0,
                "M5.0",
                12.0,
                "12 km from `Home`",
                "`Home`",
                10.0,
                "10.0 km",
                "LOW",
                FIXED_NOW,
                "Time PHT",
                "Time UTC",
                null,
                null,
                null,
                "Tanaw Footer"
        );

        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.embeds[0].title").value("M5.0 \\*Earthquake\\* \\[Click Here\\]\\(http://evil.com\\) @\u200Beveryone"))
                .andExpect(jsonPath("$.embeds[0].color").value(3447003)) // LOW = Blue
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(injectionMessage, BASE_WEBHOOK);

        assertThat(result.isSent()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 403 Forbidden returns Permanent failure with invalidateEndpoint = true")
    void http403ReturnsPermanentWithInvalidateEndpointTrue() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"Missing Permissions\", \"code\": 50001}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
        DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
        assertThat(permanent.invalidateEndpoint()).isTrue();
        assertThat(permanent.reason()).contains("403");
        assertThat(permanent.reason()).doesNotContain(SECRET_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 502 Bad Gateway returns Retryable")
    void http502ReturnsRetryable() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
        DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
        assertThat(retryable.retryAfter()).isNull();
        assertThat(retryable.reason()).contains("502");

        mockServer.verify();
    }

    @Test
    @DisplayName("Destination with leading and trailing whitespace is trimmed and delivered successfully")
    void destinationWithWhitespaceIsTrimmed() {
        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), "  " + BASE_WEBHOOK + "  \n");

        assertThat(result.isSent()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("Malformed URI destination does not leak secret token in error reason")
    void malformedUriDoesNotLeakTokenInReason() {
        String malformedUrl = "https://discord.com/api/webhooks/123/" + SECRET_TOKEN + " with invalid spaces";

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), malformedUrl);

        assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
        DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
        assertThat(permanent.invalidateEndpoint()).isTrue();
        assertThat(permanent.reason()).doesNotContain(SECRET_TOKEN);
        assertThat(permanent.reason()).contains("Invalid Discord webhook URI syntax");
    }

    @Test
    @DisplayName("HTTP 429 parses X-RateLimit-Reset-After header when Retry-After is missing")
    void http429ParsesXRateLimitResetAfterHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Reset-After", "4.2");

        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"You are being rate limited.\"}"));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(createSampleMessage("HIGH"), BASE_WEBHOOK);

        assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
        DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
        assertThat(retryable.retryAfter()).isEqualTo(Duration.ofMillis(4200));

        mockServer.verify();
    }

    @Test
    @DisplayName("Blank and empty message fields are omitted from embed to prevent Discord 400 Bad Request")
    void blankFieldsAreOmittedFromEmbed() {
        AlertMessage sparseMessage = new AlertMessage(
                "   ",
                "",
                null,
                "  ",
                null,
                "   ",
                null,
                null,
                " ",
                null,
                null,
                "  ",
                "   ",
                null,
                "   ",
                "ftp://invalid-url.com",
                "   "
        );

        mockServer.expect(requestTo(EXPECTED_REQUEST_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.embeds[0].title").value("Earthquake alert"))
                .andExpect(jsonPath("$.embeds[0].color").value(3447003)) // Default = Blue
                .andExpect(jsonPath("$.embeds[0].fields").isArray())
                .andExpect(jsonPath("$.embeds[0].fields").isEmpty())
                .andExpect(jsonPath("$.embeds[0].description").doesNotExist())
                .andExpect(jsonPath("$.embeds[0].footer").doesNotExist())
                .andExpect(jsonPath("$.embeds[0].url").doesNotExist())
                .andExpect(jsonPath("$.embeds[0].timestamp").doesNotExist())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        DiscordNotificationSender sender = createSender(false);
        DeliveryResult result = sender.send(sparseMessage, BASE_WEBHOOK);

        assertThat(result.isSent()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("Null or blank destination returns Permanent failure with invalidateEndpoint = true")
    void nullOrBlankDestinationReturnsPermanent() {
        DiscordNotificationSender sender = createSender(false);

        DeliveryResult nullResult = sender.send(createSampleMessage("HIGH"), null);
        assertThat(nullResult).isInstanceOf(DeliveryResult.Permanent.class);
        assertThat(((DeliveryResult.Permanent) nullResult).invalidateEndpoint()).isTrue();

        DeliveryResult blankResult = sender.send(createSampleMessage("HIGH"), "   ");
        assertThat(blankResult).isInstanceOf(DeliveryResult.Permanent.class);
        assertThat(((DeliveryResult.Permanent) blankResult).invalidateEndpoint()).isTrue();
    }
}
