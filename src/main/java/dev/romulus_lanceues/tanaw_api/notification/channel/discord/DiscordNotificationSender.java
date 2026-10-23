package dev.romulus_lanceues.tanaw_api.notification.channel.discord;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.AlertMessage;
import dev.romulus_lanceues.tanaw_api.notification.channel.DeliveryResult;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.UntrustedContentEscaper;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Outbound notification sender for delivering earthquake alerts to Discord channels via webhooks.
 * Features include:
 * <ul>
 *     <li>Destination validation and SSRF prevention via {@link DiscordDestinationValidator}</li>
 *     <li>Mention suppression ({@code allowed_mentions: { parse: [] }})</li>
 *     <li>Character limit guarding on all embed attributes</li>
 *     <li>Untrusted markdown and content escaping</li>
 *     <li>Dry-run mode bypassing HTTP calls and logging masked destinations</li>
 *     <li>HTTP status and exception classification into {@link DeliveryResult}</li>
 *     <li>Credential redaction in failure reasons and logs</li>
 * </ul>
 */
@Component
@Slf4j
public class DiscordNotificationSender implements NotificationSender {

    private static final String DEFAULT_USERNAME = "Tanaw Alerts";
    private static final int TITLE_MAX_LENGTH = 256;
    private static final int DESCRIPTION_MAX_LENGTH = 4096;
    private static final int FIELD_NAME_MAX_LENGTH = 256;
    private static final int FIELD_VALUE_MAX_LENGTH = 1024;
    private static final int FOOTER_TEXT_MAX_LENGTH = 2048;

    // Discord embed colors by alert severity
    private static final int COLOR_CRITICAL = 15158332; // Red #E74C3C
    private static final int COLOR_HIGH = 15105570;     // Orange #E67E22
    private static final int COLOR_MODERATE = 15844367; // Yellow #F1C40F
    private static final int COLOR_DEFAULT = 3447003;   // Blue #3498DB

    private final NotificationProperties properties;
    private final DiscordDestinationValidator validator;
    private final DestinationMasker masker;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DiscordNotificationSender(
            NotificationProperties properties,
            DiscordDestinationValidator validator,
            DestinationMasker masker,
            ObjectMapper objectMapper,
            @Qualifier("discordRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.validator = validator;
        this.masker = masker;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.DISCORD;
    }

    @Override
    public DeliveryResult send(AlertMessage message, String destination) {
        if (destination == null || destination.isBlank()) {
            return new DeliveryResult.Permanent("Discord webhook URL must not be null or blank", true);
        }

        String trimmedDestination = destination.trim();
        try {
            validator.validate(trimmedDestination);
        } catch (InvalidDestinationException ex) {
            log.warn("Invalid Discord webhook destination rejected: {}", ex.getMessage());
            return new DeliveryResult.Permanent("Invalid Discord webhook URL: " + ex.getMessage(), true);
        }

        if (message == null) {
            return new DeliveryResult.Permanent("AlertMessage must not be null", false);
        }

        DiscordPayload payload = buildPayload(message);

        if (Boolean.TRUE.equals(properties.dryRun())) {
            log.info("Dry-run mode enabled: skipping outbound Discord notification to {}. Message: '{}'",
                    masker.mask(NotificationChannel.DISCORD, trimmedDestination),
                    message.title());
            return new DeliveryResult.Sent("dry-run");
        }

        String targetUrl = trimmedDestination + "?wait=true";

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String providerMessageId = extractProviderMessageId(response.getBody(), trimmedDestination);
                log.info("Successfully delivered Discord alert to endpoint {} with messageId: {}",
                        masker.mask(NotificationChannel.DISCORD, trimmedDestination), providerMessageId);
                return new DeliveryResult.Sent(providerMessageId);
            }

            return classifyStatusCode(response.getStatusCode().value(), response.getHeaders(), response.getBody());

        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            HttpHeaders headers = ex.getResponseHeaders();
            String responseBody = ex.getResponseBodyAsString();

            log.warn("Discord webhook delivery failed for destination {}: HTTP {}",
                    masker.mask(NotificationChannel.DISCORD, trimmedDestination), statusCode);

            return classifyStatusCode(statusCode, headers, responseBody);

        } catch (ResourceAccessException ex) {
            log.warn("Discord webhook connection failed or timed out for destination {}: {}",
                    masker.mask(NotificationChannel.DISCORD, trimmedDestination), ex.getClass().getSimpleName());
            boolean isTimeout = isTimeoutException(ex);
            return new DeliveryResult.Retryable(isTimeout ? "TIMEOUT" : "CONNECTION_ERROR");

        } catch (Exception ex) {
            log.warn("Unexpected error during Discord webhook delivery for destination {}: {}",
                    masker.mask(NotificationChannel.DISCORD, trimmedDestination), ex.getClass().getSimpleName());
            return new DeliveryResult.Retryable("UNEXPECTED_ERROR: " + ex.getClass().getSimpleName());
        }
    }

    private static boolean isTimeoutException(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof java.net.SocketTimeoutException) {
            return true;
        }
        String msg = ex.getMessage();
        return msg != null && msg.toLowerCase(Locale.ROOT).contains("timed out");
    }

    private DiscordPayload buildPayload(AlertMessage message) {
        String rawTitle = (message.title() != null && !message.title().isBlank())
                ? message.title().trim()
                : "Earthquake alert";
        String escapedTitle = UntrustedContentEscaper.escapeDiscordMarkdown(rawTitle);
        String title = truncate(escapedTitle.isBlank() ? "Earthquake alert" : escapedTitle, TITLE_MAX_LENGTH);

        String url = null;
        if (message.usgsUrl() != null && !message.usgsUrl().isBlank()) {
            String trimmedUrl = message.usgsUrl().trim();
            if (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) {
                url = trimmedUrl;
            }
        }

        int color = resolveEmbedColor(message.severity());

        String description = null;
        if (message.reviewNotice() != null && !message.reviewNotice().isBlank()) {
            String escapedNotice = UntrustedContentEscaper.escapeDiscordMarkdown(message.reviewNotice().trim());
            if (!escapedNotice.isBlank()) {
                description = truncate("⚠️ *" + escapedNotice + "*", DESCRIPTION_MAX_LENGTH);
            }
        }

        List<EmbedField> fields = new ArrayList<>();
        if (message.formattedMagnitude() != null && !message.formattedMagnitude().isBlank()) {
            fields.add(new EmbedField("Magnitude", truncate(message.formattedMagnitude().trim(), FIELD_VALUE_MAX_LENGTH), true));
        }
        if (message.formattedDepth() != null && !message.formattedDepth().isBlank()) {
            fields.add(new EmbedField("Depth", truncate(message.formattedDepth().trim(), FIELD_VALUE_MAX_LENGTH), true));
        }
        if (message.formattedDistance() != null && !message.formattedDistance().isBlank()) {
            String escapedDistance = UntrustedContentEscaper.escapeDiscordMarkdown(message.formattedDistance().trim());
            if (!escapedDistance.isBlank()) {
                fields.add(new EmbedField("Distance", truncate(escapedDistance, FIELD_VALUE_MAX_LENGTH), true));
            }
        }
        if (message.place() != null && !message.place().isBlank()) {
            String escapedPlace = UntrustedContentEscaper.escapeDiscordMarkdown(message.place().trim());
            if (!escapedPlace.isBlank()) {
                fields.add(new EmbedField("Location", truncate(escapedPlace, FIELD_VALUE_MAX_LENGTH), false));
            }
        }
        if (message.formattedTimeManila() != null && !message.formattedTimeManila().isBlank()) {
            fields.add(new EmbedField("Time (PHT)", truncate(message.formattedTimeManila().trim(), FIELD_VALUE_MAX_LENGTH), true));
        }
        if (message.formattedTimeUtc() != null && !message.formattedTimeUtc().isBlank()) {
            fields.add(new EmbedField("Time (UTC)", truncate(message.formattedTimeUtc().trim(), FIELD_VALUE_MAX_LENGTH), true));
        }
        if (message.severity() != null && !message.severity().isBlank()) {
            fields.add(new EmbedField("Severity", truncate(message.severity().trim(), FIELD_VALUE_MAX_LENGTH), true));
        }

        EmbedFooter footer = (message.footer() != null && !message.footer().isBlank())
                ? new EmbedFooter(truncate(message.footer().trim(), FOOTER_TEXT_MAX_LENGTH))
                : null;

        String timestamp = message.occurredAt() != null
                ? message.occurredAt().toString()
                : null;

        DiscordEmbed embed = new DiscordEmbed(
                title,
                description,
                url,
                color,
                fields,
                footer,
                timestamp
        );

        return new DiscordPayload(
                DEFAULT_USERNAME,
                List.of(embed),
                new AllowedMentions(List.of())
        );
    }

    private int resolveEmbedColor(String severity) {
        if (severity == null) {
            return COLOR_DEFAULT;
        }
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> COLOR_CRITICAL;
            case "HIGH" -> COLOR_HIGH;
            case "MODERATE" -> COLOR_MODERATE;
            default -> COLOR_DEFAULT;
        };
    }

    private String extractProviderMessageId(String responseBody, String destination) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("id")) {
                return root.get("id").asString();
            }
        } catch (Exception ex) {
            log.warn("Failed to parse Discord message ID for destination {}: {}",
                    masker.mask(NotificationChannel.DISCORD, destination), ex.getClass().getSimpleName());
        }
        return null;
    }

    private DeliveryResult classifyStatusCode(
            int statusCode,
            HttpHeaders headers,
            String responseBody
    ) {
        if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) { // 429
            Duration retryAfter = parseRetryAfter(headers, responseBody);
            return new DeliveryResult.Retryable("HTTP 429 RATE_LIMITED", retryAfter);
        }

        if (statusCode == HttpStatus.NOT_FOUND.value()) { // 404
            return new DeliveryResult.Permanent("HTTP 404 WEBHOOK_NOT_FOUND", true);
        }

        if (statusCode == HttpStatus.UNAUTHORIZED.value()) { // 401
            return new DeliveryResult.Permanent("HTTP 401 UNAUTHORIZED", true);
        }

        if (statusCode == HttpStatus.FORBIDDEN.value()) { // 403
            return new DeliveryResult.Permanent("HTTP 403 FORBIDDEN", true);
        }

        if (statusCode == HttpStatus.BAD_REQUEST.value()) { // 400
            return new DeliveryResult.Permanent("HTTP 400 BAD_REQUEST", false);
        }

        if (statusCode >= 400 && statusCode < 500) {
            return new DeliveryResult.Permanent("HTTP " + statusCode + " CLIENT_ERROR", false);
        }

        if (statusCode >= 500 && statusCode < 600) {
            return new DeliveryResult.Retryable("HTTP " + statusCode + " SERVER_ERROR");
        }

        return new DeliveryResult.Retryable("HTTP " + statusCode + " UNEXPECTED_RESPONSE");
    }

    private Duration parseRetryAfter(HttpHeaders headers, String responseBody) {
        if (headers != null) {
            String retryAfterHeader = headers.getFirst("Retry-After");
            if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
                retryAfterHeader = headers.getFirst("X-RateLimit-Reset-After");
            }
            if (retryAfterHeader != null && !retryAfterHeader.isBlank()) {
                try {
                    double seconds = Double.parseDouble(retryAfterHeader.trim());
                    long millis = Math.round(seconds * 1000.0);
                    if (millis > 0) {
                        return Duration.ofMillis(millis);
                    }
                } catch (NumberFormatException ex) {
                    log.debug("Unable to parse Retry-After header '{}': {}", retryAfterHeader, ex.getMessage());
                }
            }
        }

        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.hasNonNull("retry_after")) {
                    double seconds = root.get("retry_after").asDouble();
                    long millis = Math.round(seconds * 1000.0);
                    if (millis > 0) {
                        return Duration.ofMillis(millis);
                    }
                }
            } catch (Exception ex) {
                log.debug("Unable to parse retry_after from Discord response body: {}", ex.getMessage());
            }
        }

        return null;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    record DiscordPayload(
            String username,
            List<DiscordEmbed> embeds,
            AllowedMentions allowed_mentions
    ) {}

    record DiscordEmbed(
            String title,
            String description,
            String url,
            int color,
            List<EmbedField> fields,
            EmbedFooter footer,
            String timestamp
    ) {}

    record EmbedField(
            String name,
            String value,
            boolean inline
    ) {}

    record EmbedFooter(
            String text
    ) {}

    record AllowedMentions(
            List<String> parse
    ) {}
}
