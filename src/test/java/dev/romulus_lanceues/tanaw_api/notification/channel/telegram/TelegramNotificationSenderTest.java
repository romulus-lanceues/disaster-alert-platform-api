package dev.romulus_lanceues.tanaw_api.notification.channel.telegram;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.AlertMessage;
import dev.romulus_lanceues.tanaw_api.notification.channel.DeliveryResult;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("TelegramNotificationSender Unit Tests")
class TelegramNotificationSenderTest {

    private static final String BOT_TOKEN = "123456789:ABCdefGHIjklMNOpqrsTUVwxyz_1234";
    private static final String BOT_USERNAME = "tanaw_alert_bot";
    private static final String VALID_CHAT_ID = "987654321";
    private static final String VALID_USERNAME = "@tanaw_alerts_channel";
    private static final String INVALID_DESTINATION = "https://malicious.com/api/telegram";
    private static final String EXPECTED_URL = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
    private static final Instant FIXED_NOW = Instant.parse("2026-11-06T12:00:00Z");

    private final TelegramDestinationValidator validator = new TelegramDestinationValidator();
    private final DestinationMasker masker = new DestinationMasker();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private MockRestServiceServer mockServer;
    private RestClient.Builder restClientBuilder;
    private TelegramRateLimiter rateLimiter;

    private NotificationProperties createProperties(boolean dryRun, String botToken) {
        return new NotificationProperties(
                dryRun,
                new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                new NotificationProperties.Fanout(Duration.ofHours(6)),
                new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new NotificationProperties.Crypto("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="),
                new NotificationProperties.Telegram(botToken, BOT_USERNAME, Duration.ofSeconds(3), Duration.ofSeconds(10))
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
        rateLimiter = new TelegramRateLimiter(clock);
    }

    private TelegramNotificationSender createSender(boolean dryRun, String botToken) {
        return new TelegramNotificationSender(
                createProperties(dryRun, botToken),
                validator,
                masker,
                rateLimiter,
                objectMapper,
                restClientBuilder.build()
        );
    }

    private TelegramNotificationSender createSender(boolean dryRun, String botToken, TelegramRateLimiter customLimiter) {
        return new TelegramNotificationSender(
                createProperties(dryRun, botToken),
                validator,
                masker,
                customLimiter,
                objectMapper,
                restClientBuilder.build()
        );
    }

    @Test
    @DisplayName("channel returns NotificationChannel.TELEGRAM")
    void channelReturnsTelegram() {
        TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
        assertThat(sender.channel()).isEqualTo(NotificationChannel.TELEGRAM);
    }

    @Nested
    @DisplayName("Input Validation & Destination Checks")
    class InputValidation {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("Blank destination returns Permanent failure with invalidateEndpoint = true")
        void blankDestinationReturnsPermanent(String emptyDest) {
            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("MODERATE"), emptyDest);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.invalidateEndpoint()).isTrue();
            assertThat(permanent.reason()).contains("destination must not be null or blank");
            mockServer.verify();
        }

        @Test
        @DisplayName("Null destination returns Permanent failure with invalidateEndpoint = true")
        void nullDestinationReturnsPermanent() {
            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("MODERATE"), null);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.invalidateEndpoint()).isTrue();
            assertThat(permanent.reason()).contains("destination must not be null or blank");
            mockServer.verify();
        }

        @Test
        @DisplayName("Invalid / SSRF destination returns Permanent failure with invalidateEndpoint = true")
        void invalidDestinationReturnsPermanent() {
            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("MODERATE"), INVALID_DESTINATION);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.invalidateEndpoint()).isTrue();
            assertThat(permanent.reason()).contains("Invalid Telegram destination");
            mockServer.verify();
        }

        @Test
        @DisplayName("Null AlertMessage returns Permanent failure with invalidateEndpoint = false")
        void nullMessageReturnsPermanent() {
            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(null, VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.invalidateEndpoint()).isFalse();
            assertThat(permanent.reason()).contains("AlertMessage must not be null");
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Dry-Run and Configuration Guards")
    class DryRunAndConfigGuards {

        @Test
        @DisplayName("Dry-run mode skips outbound HTTP call and returns Sent('dry-run')")
        void dryRunSkipsHttpAndReturnsSentDryRun() {
            TelegramNotificationSender sender = createSender(true, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
            DeliveryResult.Sent sent = (DeliveryResult.Sent) result;
            assertThat(sent.providerMessageId()).isEqualTo("dry-run");
            mockServer.verify();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("Missing or blank bot token returns Retryable without executing HTTP request")
        void missingBotTokenReturnsRetryable(String emptyToken) {
            TelegramNotificationSender sender = createSender(false, emptyToken);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("TELEGRAM_BOT_TOKEN_NOT_CONFIGURED");
            mockServer.verify();
        }

        @Test
        @DisplayName("Null telegram properties returns Retryable without executing HTTP request")
        void nullTelegramPropertiesReturnsRetryable() {
            NotificationProperties propsWithoutTelegram = new NotificationProperties(
                    false,
                    new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                    new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                    new NotificationProperties.Fanout(Duration.ofHours(6)),
                    new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                    new NotificationProperties.Crypto("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="),
                    null
            );
            TelegramNotificationSender sender = new TelegramNotificationSender(
                    propsWithoutTelegram,
                    validator,
                    masker,
                    rateLimiter,
                    objectMapper,
                    restClientBuilder.build()
            );

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("TELEGRAM_BOT_TOKEN_NOT_CONFIGURED");
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Proactive Rate Limiting")
    class ProactiveRateLimiting {

        @Test
        @DisplayName("Returns Retryable('HTTP 429 RATE_LIMITED', retryAfter) when proactive rate limit is reached")
        void proactiveRateLimitReturnsRetryable() {
            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);

            // 1st call succeeds via mock server
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":true,\"result\":{\"message_id\":1001}}"));

            DeliveryResult first = sender.send(createSampleMessage("MODERATE"), VALID_CHAT_ID);
            assertThat(first).isInstanceOf(DeliveryResult.Sent.class);

            // 2nd call at same timestamp (T=0) to same chat is proactively blocked
            DeliveryResult second = sender.send(createSampleMessage("MODERATE"), VALID_CHAT_ID);

            assertThat(second).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) second;
            assertThat(retryable.reason()).isEqualTo("HTTP 429 RATE_LIMITED");
            assertThat(retryable.retryAfter()).isNotNull();
            assertThat(retryable.retryAfter().toMillis()).isPositive();

            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Delivery Success and Status Classification")
    class SuccessAndClassification {

        @Test
        @DisplayName("HTTP 200 with result.message_id returns Sent(providerMessageId)")
        void http200WithMessageIdReturnsSent() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.chat_id").value(VALID_CHAT_ID))
                    .andExpect(jsonPath("$.parse_mode").value("HTML"))
                    .andExpect(jsonPath("$.text").value(containsString("<b>M5.4 earthquake — 23 km from Home</b>")))
                    .andExpect(jsonPath("$.text").value(containsString("<b>Magnitude:</b> M5.4")))
                    .andExpect(jsonPath("$.text").value(containsString("<a href=\"https://earthquake.usgs.gov/earthquakes/eventpage/us7000test\">USGS Event Details</a>")))
                    .andRespond(withStatus(HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":true,\"result\":{\"message_id\":54321}}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("MODERATE"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
            DeliveryResult.Sent sent = (DeliveryResult.Sent) result;
            assertThat(sent.providerMessageId()).isEqualTo("54321");
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 200 without message_id returns Sent(null)")
        void http200WithoutMessageIdReturnsSentNull() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":true,\"result\":{}}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("MODERATE"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
            DeliveryResult.Sent sent = (DeliveryResult.Sent) result;
            assertThat(sent.providerMessageId()).isNull();
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 429 with parameters.retry_after in JSON body returns Retryable with retryAfter duration")
        void http429WithParametersRetryAfterReturnsRetryable() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\",\"parameters\":{\"retry_after\":5}}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("HTTP 429 RATE_LIMITED");
            assertThat(retryable.retryAfter()).isEqualTo(Duration.ofSeconds(5));
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 429 with Retry-After header returns Retryable with retryAfter duration")
        void http429WithRetryAfterHeaderReturnsRetryable() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Retry-After", "3");

            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .headers(headers)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\"}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("HTTP 429 RATE_LIMITED");
            assertThat(retryable.retryAfter()).isEqualTo(Duration.ofSeconds(3));
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 429 without retry_after returns Retryable with null retryAfter")
        void http429WithoutRetryAfterReturnsRetryableNull() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\"}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("HTTP 429 RATE_LIMITED");
            assertThat(retryable.retryAfter()).isNull();
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 400 Bad Request returns Permanent failure with invalidateEndpoint = true")
        void http400ReturnsPermanentInvalidateEndpointTrue() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.invalidateEndpoint()).isTrue();
            assertThat(permanent.reason()).isEqualTo("HTTP 400 BAD_REQUEST");
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 403 Forbidden returns Permanent failure with invalidateEndpoint = true")
        void http403ReturnsPermanentInvalidateEndpointTrue() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.FORBIDDEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.invalidateEndpoint()).isTrue();
            assertThat(permanent.reason()).isEqualTo("HTTP 403 FORBIDDEN");
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 404 Not Found returns Permanent failure with invalidateEndpoint = true")
        void http404ReturnsPermanentInvalidateEndpointTrue() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":false,\"error_code\":404,\"description\":\"Not Found\"}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.invalidateEndpoint()).isTrue();
            assertThat(permanent.reason()).isEqualTo("HTTP 404 NOT_FOUND");
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 401 Unauthorized returns Retryable with INVALID_BOT_TOKEN and does NOT invalidate endpoint")
        void http401ReturnsRetryableInvalidBotToken() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("HTTP 401 INVALID_BOT_TOKEN");
            mockServer.verify();
        }

        @Test
        @DisplayName("Other 4xx (e.g. 422) returns Permanent failure with invalidateEndpoint = false")
        void other4xxReturnsPermanentInvalidateFalse() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":false,\"error_code\":422,\"description\":\"Unprocessable Entity\"}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.invalidateEndpoint()).isFalse();
            assertThat(permanent.reason()).isEqualTo("HTTP 422 CLIENT_ERROR");
            mockServer.verify();
        }

        @ParameterizedTest
        @ValueSource(ints = {500, 502, 503})
        @DisplayName("HTTP 5xx Server Error returns Retryable('HTTP <status> SERVER_ERROR')")
        void http5xxReturnsRetryable(int status) {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.valueOf(status)));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("HTTP " + status + " SERVER_ERROR");
            mockServer.verify();
        }

        @Test
        @DisplayName("SocketTimeoutException returns Retryable('TIMEOUT')")
        void timeoutReturnsRetryable() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withException(new SocketTimeoutException("Read timed out to " + EXPECTED_URL)));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("TIMEOUT");
            assertThat(retryable.reason()).doesNotContain(BOT_TOKEN);
            mockServer.verify();
        }

        @Test
        @DisplayName("Connection I/O error returns Retryable('CONNECTION_ERROR')")
        void connectionErrorReturnsRetryable() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withException(new IOException("Connection refused for " + EXPECTED_URL)));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("CONNECTION_ERROR");
            assertThat(retryable.reason()).doesNotContain(BOT_TOKEN);
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("HTML Escaping and Length Guarding")
    class HtmlEscapingAndGuarding {

        @Test
        @DisplayName("HTML special characters in untrusted fields are escaped properly")
        void htmlSpecialCharactersAreEscaped() {
            AlertMessage dangerousMessage = new AlertMessage(
                    "M5.0 <script>alert('xss')</script> & \"quotes\"",
                    "Place with <tags> & 'quotes'",
                    5.0,
                    "M5.0",
                    12.0,
                    "12 km from <Home>",
                    "<Home>",
                    10.0,
                    "10.0 km",
                    "LOW",
                    FIXED_NOW,
                    "Time PHT",
                    "Time UTC",
                    "automatic",
                    "Notice with <b>injection</b> & 'raw'",
                    "https://earthquake.usgs.gov?param=1&param2=2",
                    "Footer <test> & \"more\""
            );

            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.text").value(containsString("&lt;script&gt;alert('xss')&lt;/script&gt; &amp; &quot;quotes&quot;")))
                    .andExpect(jsonPath("$.text").value(containsString("&lt;tags&gt; &amp; 'quotes'")))
                    .andExpect(jsonPath("$.text").value(containsString("&lt;Home&gt;")))
                    .andExpect(jsonPath("$.text").value(containsString("<i>Notice with &lt;b&gt;injection&lt;/b&gt; &amp; 'raw'</i>")))
                    .andExpect(jsonPath("$.text").value(containsString("param=1&amp;param2=2")))
                    .andExpect(jsonPath("$.text").value(containsString("Footer &lt;test&gt; &amp; &quot;more&quot;")))
                    .andRespond(withStatus(HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":true,\"result\":{\"message_id\":888}}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(dangerousMessage, VALID_CHAT_ID);

            assertThat(result.isSent()).isTrue();
            mockServer.verify();
        }

        @Test
        @DisplayName("Message length is guarded and truncated to at most 4096 characters")
        void messageLengthGuardedTo4096Chars() {
            String oversizedTitle = "M7.5 " + "A".repeat(500);
            String oversizedPlace = "City " + "B".repeat(3000);
            String oversizedNotice = "Notice " + "C".repeat(2000);
            String oversizedFooter = "Footer " + "D".repeat(2000);

            AlertMessage hugeMessage = new AlertMessage(
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
                    oversizedNotice,
                    "https://earthquake.usgs.gov",
                    oversizedFooter
            );

            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.hasLength(lessThanOrEqualTo(4096))))
                    .andRespond(withStatus(HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"ok\":true,\"result\":{\"message_id\":999}}"));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(hugeMessage, VALID_CHAT_ID);

            assertThat(result.isSent()).isTrue();
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("Secret Redaction & Security Hygiene")
    class SecretRedactionAndHygiene {

        @Test
        @DisplayName("Bot token is never present in any failure reason or logged output")
        void botTokenNeverLeakedInFailureReasons() {
            mockServer.expect(requestTo(EXPECTED_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withException(new IOException("Crash involving token " + BOT_TOKEN)));

            TelegramNotificationSender sender = createSender(false, BOT_TOKEN);
            DeliveryResult result = sender.send(createSampleMessage("HIGH"), VALID_CHAT_ID);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).doesNotContain(BOT_TOKEN);
            assertThat(retryable.reason()).isEqualTo("CONNECTION_ERROR");
            mockServer.verify();
        }
    }
}
