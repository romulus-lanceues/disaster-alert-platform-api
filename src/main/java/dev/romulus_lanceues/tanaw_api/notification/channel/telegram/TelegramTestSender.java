package dev.romulus_lanceues.tanaw_api.notification.channel.telegram;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelVerifier;
import dev.romulus_lanceues.tanaw_api.notification.channel.EndpointVerificationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class TelegramTestSender implements ChannelVerifier {

    private final RestClient restClient;
    private final NotificationProperties properties;
    private final TelegramDestinationValidator validator;
    private final DestinationMasker masker;

    public TelegramTestSender(
            NotificationProperties properties,
            TelegramDestinationValidator validator,
            DestinationMasker masker,
            @Qualifier("telegramRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.validator = validator;
        this.masker = masker;
        this.restClient = restClient;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public void verify(String destination) {
        validator.validate(destination);

        if (Boolean.TRUE.equals(properties.dryRun())) {
            log.info("Dry-run mode enabled: skipping outbound Telegram test message to {}",
                    masker.mask(NotificationChannel.TELEGRAM, destination));
            return;
        }

        String botToken = properties.telegram() != null ? properties.telegram().botToken() : null;
        if (botToken == null || botToken.isBlank()) {
            throw new EndpointVerificationException("Telegram bot token is not configured");
        }

        TelegramSendMessageRequest payload = new TelegramSendMessageRequest(
                destination,
                "<b>[TEST] Tanaw Alert Notification — Endpoint Verification</b>\n\n"
                        + "This is a test notification confirming that your Telegram endpoint is active and configured correctly.",
                "HTML"
        );

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new EndpointVerificationException(
                        "Telegram verification failed: HTTP " + response.getStatusCode().value()
                );
            }

            log.info("Successfully sent test verification message to Telegram endpoint {}",
                    masker.mask(NotificationChannel.TELEGRAM, destination));
        } catch (RestClientResponseException ex) {
            log.warn("Telegram verification failed for destination {}: HTTP {}",
                    masker.mask(NotificationChannel.TELEGRAM, destination), ex.getStatusCode().value());
            throw new EndpointVerificationException(
                    "Telegram verification failed: HTTP " + ex.getStatusCode().value()
            );
        } catch (ResourceAccessException ex) {
            log.warn("Telegram verification timed out or connection failed for destination {}",
                    masker.mask(NotificationChannel.TELEGRAM, destination));
            throw new EndpointVerificationException(
                    "Telegram verification failed: connection timed out or unreachable"
            );
        } catch (EndpointVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Telegram verification error for destination {}: {}",
                    masker.mask(NotificationChannel.TELEGRAM, destination), ex.getClass().getSimpleName());
            throw new EndpointVerificationException(
                    "Telegram verification failed: unexpected error occurred"
            );
        }
    }

    record TelegramSendMessageRequest(String chat_id, String text, String parse_mode) {}
}
