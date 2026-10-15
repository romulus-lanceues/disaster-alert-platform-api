package dev.romulus_lanceues.tanaw_api.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
@Slf4j
public class DiscordTestSender implements ChannelVerifier {

    private final RestClient restClient;
    private final NotificationProperties properties;
    private final DiscordDestinationValidator validator;
    private final DestinationMasker masker;

    public DiscordTestSender(
            NotificationProperties properties,
            DiscordDestinationValidator validator,
            DestinationMasker masker,
            @Qualifier("discordRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.validator = validator;
        this.masker = masker;
        this.restClient = restClient;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.DISCORD;
    }

    @Override
    public void verify(String destination) {
        validator.validate(destination);

        if (Boolean.TRUE.equals(properties.dryRun())) {
            log.info("Dry-run mode enabled: skipping outbound Discord test message to {}",
                    masker.mask(NotificationChannel.DISCORD, destination));
            return;
        }

        DiscordTestPayload payload = new DiscordTestPayload(
                "Tanaw Alerts",
                List.of(new DiscordEmbed(
                        "[TEST] Tanaw Alert Notification — Endpoint Verification",
                        "This is a test notification confirming that your Discord webhook is active and configured correctly.",
                        3447003
                )),
                new AllowedMentions(List.of())
        );

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(destination)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new EndpointVerificationException(
                        "Discord webhook verification failed: HTTP " + response.getStatusCode().value()
                );
            }

            log.info("Successfully sent test verification message to Discord endpoint {}",
                    masker.mask(NotificationChannel.DISCORD, destination));
        } catch (RestClientResponseException ex) {
            log.warn("Discord webhook verification failed for destination {}: HTTP {}",
                    masker.mask(NotificationChannel.DISCORD, destination), ex.getStatusCode().value());
            throw new EndpointVerificationException(
                    "Discord webhook verification failed: HTTP " + ex.getStatusCode().value()
            );
        } catch (ResourceAccessException ex) {
            log.warn("Discord webhook verification timed out or connection failed for destination {}",
                    masker.mask(NotificationChannel.DISCORD, destination));
            throw new EndpointVerificationException(
                    "Discord webhook verification failed: connection timed out or unreachable"
            );
        } catch (EndpointVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Discord webhook verification error for destination {}: {}",
                    masker.mask(NotificationChannel.DISCORD, destination), ex.getClass().getSimpleName());
            throw new EndpointVerificationException(
                    "Discord webhook verification failed: unexpected error occurred"
            );
        }
    }

    record DiscordEmbed(String title, String description, int color) {}
    record AllowedMentions(List<String> parse) {}
    record DiscordTestPayload(String username, List<DiscordEmbed> embeds, AllowedMentions allowed_mentions) {}
}
