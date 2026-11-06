package dev.romulus_lanceues.tanaw_api.notification.channel.telegram;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.EndpointVerificationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("TelegramTestSender Unit Tests")
class TelegramTestSenderTest {

    private static final String BOT_TOKEN = "123456789:ABCdefGHIjklMNOpqrsTUVwxyz_1234";
    private static final String BOT_USERNAME = "tanaw_alert_bot";
    private static final String VALID_DESTINATION = "987654321";
    private static final String VALID_USERNAME_DESTINATION = "@tanaw_alerts_channel";
    private static final String INVALID_DESTINATION = "https://malicious.com/api/telegram";
    private static final String EXPECTED_URL = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";

    private final TelegramDestinationValidator validator = new TelegramDestinationValidator();
    private final DestinationMasker masker = new DestinationMasker();

    private MockRestServiceServer mockServer;
    private RestClient.Builder restClientBuilder;

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

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private TelegramTestSender createSender(boolean dryRun, String botToken) {
        return new TelegramTestSender(createProperties(dryRun, botToken), validator, masker, restClientBuilder.build());
    }

    @Test
    @DisplayName("channel returns TELEGRAM")
    void channelReturnsTelegram() {
        TelegramTestSender sender = createSender(true, BOT_TOKEN);
        assertThat(sender.channel()).isEqualTo(NotificationChannel.TELEGRAM);
    }

    @Test
    @DisplayName("Rejects invalid / SSRF destination without executing HTTP request")
    void rejectsInvalidDestinationSSRF() {
        TelegramTestSender sender = createSender(false, BOT_TOKEN);

        assertThatThrownBy(() -> sender.verify(INVALID_DESTINATION))
                .isInstanceOf(InvalidDestinationException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("Dry-run mode skips outbound HTTP request completely")
    void dryRunSkipsOutboundHttpRequest() {
        TelegramTestSender sender = createSender(true, BOT_TOKEN);

        assertThatCode(() -> sender.verify(VALID_DESTINATION))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Throws EndpointVerificationException when Telegram bot token is missing or blank")
    void missingBotTokenThrows(String emptyToken) {
        TelegramTestSender sender = createSender(false, emptyToken);

        assertThatThrownBy(() -> sender.verify(VALID_DESTINATION))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Telegram bot token is not configured");

        mockServer.verify();
    }

    @Test
    @DisplayName("Throws EndpointVerificationException when Telegram properties are null")
    void nullTelegramPropertiesThrows() {
        NotificationProperties propsWithoutTelegram = new NotificationProperties(
                false,
                new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                new NotificationProperties.Fanout(Duration.ofHours(6)),
                new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new NotificationProperties.Crypto("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="),
                null
        );
        TelegramTestSender sender = new TelegramTestSender(propsWithoutTelegram, validator, masker, restClientBuilder.build());

        assertThatThrownBy(() -> sender.verify(VALID_DESTINATION))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Telegram bot token is not configured");

        mockServer.verify();
    }

    @Test
    @DisplayName("Sends formatted HTML test verification payload with [TEST] label on HTTP 200")
    void sendsTestMessageSuccessfullyOn200() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.chat_id").value(VALID_DESTINATION))
                .andExpect(jsonPath("$.text").value("<b>[TEST] Tanaw Alert Notification — Endpoint Verification</b>\n\nThis is a test notification confirming that your Telegram endpoint is active and configured correctly."))
                .andExpect(jsonPath("$.parse_mode").value("HTML"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":true,\"result\":{\"message_id\":1234}}"));

        TelegramTestSender sender = createSender(false, BOT_TOKEN);
        sender.verify(VALID_DESTINATION);

        mockServer.verify();
    }

    @Test
    @DisplayName("Sends test verification payload to username destination on HTTP 200")
    void sendsTestMessageToUsernameSuccessfully() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.chat_id").value(VALID_USERNAME_DESTINATION))
                .andExpect(jsonPath("$.text").value("<b>[TEST] Tanaw Alert Notification — Endpoint Verification</b>\n\nThis is a test notification confirming that your Telegram endpoint is active and configured correctly."))
                .andExpect(jsonPath("$.parse_mode").value("HTML"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":true,\"result\":{\"message_id\":1235}}"));

        TelegramTestSender sender = createSender(false, BOT_TOKEN);
        sender.verify(VALID_USERNAME_DESTINATION);

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles HTTP 429 Too Many Requests with sanitized message and no token leakage")
    void handlesHttp429() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\"}"));

        TelegramTestSender sender = createSender(false, BOT_TOKEN);

        assertThatThrownBy(() -> sender.verify(VALID_DESTINATION))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Telegram verification failed: HTTP 429")
                .hasMessageNotContaining(BOT_TOKEN)
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles HTTP 400 Bad Request with sanitized exception message and null cause")
    void handlesHttp400() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}"));

        TelegramTestSender sender = createSender(false, BOT_TOKEN);

        assertThatThrownBy(() -> sender.verify(VALID_DESTINATION))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Telegram verification failed: HTTP 400")
                .hasMessageNotContaining(BOT_TOKEN)
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles HTTP 401 Unauthorized with sanitized exception message and null cause")
    void handlesHttp401() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}"));

        TelegramTestSender sender = createSender(false, BOT_TOKEN);

        assertThatThrownBy(() -> sender.verify(VALID_DESTINATION))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Telegram verification failed: HTTP 401")
                .hasMessageNotContaining(BOT_TOKEN)
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles HTTP 500 Internal Server Error with sanitized exception message and null cause")
    void handlesHttp500() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        TelegramTestSender sender = createSender(false, BOT_TOKEN);

        assertThatThrownBy(() -> sender.verify(VALID_DESTINATION))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Telegram verification failed: HTTP 500")
                .hasMessageNotContaining(BOT_TOKEN)
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles connection timeout with sanitized exception, null cause, and no secret token leakage")
    void handlesConnectionTimeout() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new SocketTimeoutException("Read timed out to " + EXPECTED_URL)));

        TelegramTestSender sender = createSender(false, BOT_TOKEN);

        assertThatThrownBy(() -> sender.verify(VALID_DESTINATION))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Telegram verification failed: connection timed out or unreachable")
                .hasMessageNotContaining(BOT_TOKEN)
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles unexpected I/O error with sanitized message and null cause")
    void handlesUnexpectedIoError() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Internal socket exception for token " + BOT_TOKEN)));

        TelegramTestSender sender = createSender(false, BOT_TOKEN);

        assertThatThrownBy(() -> sender.verify(VALID_DESTINATION))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Telegram verification failed: connection timed out or unreachable")
                .hasMessageNotContaining(BOT_TOKEN)
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }
}
