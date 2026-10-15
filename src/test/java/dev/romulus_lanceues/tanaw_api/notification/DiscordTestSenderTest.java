package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("DiscordTestSender Unit Tests")
class DiscordTestSenderTest {

    private static final String VALID_WEBHOOK = "https://discord.com/api/webhooks/1234567890/secret-token-xyz-1234";
    private static final String INVALID_WEBHOOK = "https://malicious.com/api/webhooks/1234567890/token";

    private final DiscordDestinationValidator validator = new DiscordDestinationValidator();
    private final DestinationMasker masker = new DestinationMasker();

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

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private DiscordTestSender createSender(boolean dryRun) {
        return new DiscordTestSender(createProperties(dryRun), validator, masker, restClientBuilder.build());
    }

    @Test
    @DisplayName("channel returns DISCORD")
    void channelReturnsDiscord() {
        DiscordTestSender sender = createSender(true);
        assertThat(sender.channel()).isEqualTo(NotificationChannel.DISCORD);
    }

    @Test
    @DisplayName("Rejects invalid / SSRF webhook destination without executing HTTP request")
    void rejectsInvalidDestinationSSRF() {
        DiscordTestSender sender = createSender(false);

        assertThatThrownBy(() -> sender.verify(INVALID_WEBHOOK))
                .isInstanceOf(InvalidDestinationException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("Dry-run mode skips outbound HTTP request completely")
    void dryRunSkipsOutboundHttpRequest() {
        DiscordTestSender sender = createSender(true);

        assertThatCode(() -> sender.verify(VALID_WEBHOOK))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    @DisplayName("Sends formatted test verification payload with [TEST] label and suppressed mentions on HTTP 204")
    void sendsTestMessageSuccessfullyOn204() {
        mockServer.expect(requestTo(VALID_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.username").value("Tanaw Alerts"))
                .andExpect(jsonPath("$.embeds[0].title").value("[TEST] Tanaw Alert Notification — Endpoint Verification"))
                .andExpect(jsonPath("$.embeds[0].description").isNotEmpty())
                .andExpect(jsonPath("$.allowed_mentions.parse").isArray())
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        DiscordTestSender sender = createSender(false);
        sender.verify(VALID_WEBHOOK);

        mockServer.verify();
    }

    @Test
    @DisplayName("Sends test verification successfully when Discord returns HTTP 200")
    void sendsTestMessageSuccessfullyOn200() {
        mockServer.expect(requestTo(VALID_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK));

        DiscordTestSender sender = createSender(false);
        sender.verify(VALID_WEBHOOK);

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles HTTP 404 webhook gone and redacts token from exception message")
    void handlesHttp404AndRedactsToken() {
        mockServer.expect(requestTo(VALID_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        DiscordTestSender sender = createSender(false);

        assertThatThrownBy(() -> sender.verify(VALID_WEBHOOK))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Discord webhook verification failed: HTTP 404")
                .hasMessageNotContaining("secret-token-xyz-1234")
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles HTTP 400 Bad Request with sanitized exception message and null cause")
    void handlesHttp400() {
        mockServer.expect(requestTo(VALID_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        DiscordTestSender sender = createSender(false);

        assertThatThrownBy(() -> sender.verify(VALID_WEBHOOK))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Discord webhook verification failed: HTTP 400")
                .hasMessageNotContaining("secret-token-xyz-1234")
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles connection timeout with sanitized exception, null cause, and no secret token leakage")
    void handlesConnectionTimeout() {
        mockServer.expect(requestTo(VALID_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(
                        new java.net.SocketTimeoutException("Read timed out")
                ));

        DiscordTestSender sender = createSender(false);

        assertThatThrownBy(() -> sender.verify(VALID_WEBHOOK))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Discord webhook verification failed: connection timed out or unreachable")
                .hasMessageNotContaining("secret-token-xyz-1234")
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }

    @Test
    @DisplayName("Handles unexpected I/O error with sanitized message and null cause")
    void handlesUnexpectedIoError() {
        mockServer.expect(requestTo(VALID_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(
                        new java.io.IOException("Internal I/O failure on https://discord.com/api/webhooks/123/secret-token-xyz-1234")
                ));

        DiscordTestSender sender = createSender(false);

        assertThatThrownBy(() -> sender.verify(VALID_WEBHOOK))
                .isInstanceOf(EndpointVerificationException.class)
                .hasMessage("Discord webhook verification failed: connection timed out or unreachable")
                .hasMessageNotContaining("secret-token-xyz-1234")
                .satisfies(ex -> assertThat(ex.getCause()).isNull());

        mockServer.verify();
    }
}
