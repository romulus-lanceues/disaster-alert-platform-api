package dev.romulus_lanceues.tanaw_api.notification.channel.telegram;

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
import java.util.Locale;

/**
 * Outbound notification sender for delivering earthquake alerts to Telegram chats via Telegram Bot API.
 * Features include:
 * <ul>
 *     <li>Destination validation and SSRF immunity via {@link TelegramDestinationValidator}</li>
 *     <li>Proactive and reactive rate limiting compliance with Telegram Bot API rules (30 msg/s global, 1 msg/s per chat)</li>
 *     <li>HTML rendering with untrusted content escaping and 4096-character length guarding</li>
 *     <li>Dry-run mode bypassing HTTP calls and logging masked destinations</li>
 *     <li>HTTP status and exception classification into sealed {@link DeliveryResult}</li>
 *     <li>Secret token hygiene preventing credential leakage in logs, exceptions, or failure reasons</li>
 * </ul>
 */
@Component
@Slf4j
public class TelegramNotificationSender implements NotificationSender {

    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int TITLE_MAX_LENGTH = 256;
    private static final int PLACE_MAX_LENGTH = 1000;
    private static final int NOTICE_MAX_LENGTH = 1000;
    private static final int FOOTER_MAX_LENGTH = 1000;

    private final NotificationProperties properties;
    private final TelegramDestinationValidator validator;
    private final DestinationMasker masker;
    private final TelegramRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TelegramNotificationSender(
            NotificationProperties properties,
            TelegramDestinationValidator validator,
            DestinationMasker masker,
            TelegramRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            @Qualifier("telegramRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.validator = validator;
        this.masker = masker;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public DeliveryResult send(AlertMessage message, String destination) {
        if (destination == null || destination.isBlank()) {
            return new DeliveryResult.Permanent("Telegram destination must not be null or blank", true);
        }

        String trimmedDestination = destination.trim();
        try {
            validator.validate(trimmedDestination);
        } catch (InvalidDestinationException ex) {
            log.warn("Invalid Telegram destination rejected: {}", ex.getMessage());
            return new DeliveryResult.Permanent("Invalid Telegram destination: " + ex.getMessage(), true);
        }

        if (message == null) {
            return new DeliveryResult.Permanent("AlertMessage must not be null", false);
        }

        if (Boolean.TRUE.equals(properties.dryRun())) {
            log.info("Dry-run mode enabled: skipping outbound Telegram notification to {}. Message: '{}'",
                    masker.mask(NotificationChannel.TELEGRAM, trimmedDestination),
                    message.title());
            return new DeliveryResult.Sent("dry-run");
        }

        String botToken = (properties.telegram() != null) ? properties.telegram().botToken() : null;
        if (botToken == null || botToken.isBlank()) {
            log.warn("Telegram bot token is not configured; skipping delivery to {}",
                    masker.mask(NotificationChannel.TELEGRAM, trimmedDestination));
            return new DeliveryResult.Retryable("TELEGRAM_BOT_TOKEN_NOT_CONFIGURED");
        }

        TelegramRateLimiter.RateLimitResult rateLimitResult = rateLimiter.tryAcquire(trimmedDestination);
        if (!rateLimitResult.isAllowed()) {
            log.warn("Telegram rate limit reached for destination {}. Retry after: {}",
                    masker.mask(NotificationChannel.TELEGRAM, trimmedDestination), rateLimitResult.retryAfter());
            return new DeliveryResult.Retryable("HTTP 429 RATE_LIMITED", rateLimitResult.retryAfter());
        }

        String renderedHtml = renderHtml(message);
        TelegramSendMessageRequest payload = new TelegramSendMessageRequest(trimmedDestination, renderedHtml, "HTML");
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String providerMessageId = extractProviderMessageId(response.getBody(), trimmedDestination);
                log.info("Successfully delivered Telegram alert to endpoint {} with messageId: {}",
                        masker.mask(NotificationChannel.TELEGRAM, trimmedDestination), providerMessageId);
                return new DeliveryResult.Sent(providerMessageId);
            }

            return classifyStatusCode(response.getStatusCode().value(), response.getHeaders(), response.getBody());

        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            HttpHeaders headers = ex.getResponseHeaders();
            String responseBody = ex.getResponseBodyAsString();

            log.warn("Telegram delivery failed for destination {}: HTTP {}",
                    masker.mask(NotificationChannel.TELEGRAM, trimmedDestination), statusCode);

            return classifyStatusCode(statusCode, headers, responseBody);

        } catch (ResourceAccessException ex) {
            log.warn("Telegram connection failed or timed out for destination {}: {}",
                    masker.mask(NotificationChannel.TELEGRAM, trimmedDestination), ex.getClass().getSimpleName());
            boolean isTimeout = isTimeoutException(ex);
            return new DeliveryResult.Retryable(isTimeout ? "TIMEOUT" : "CONNECTION_ERROR");

        } catch (Exception ex) {
            log.warn("Unexpected error during Telegram delivery for destination {}: {}",
                    masker.mask(NotificationChannel.TELEGRAM, trimmedDestination), ex.getClass().getSimpleName());
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

    private String renderHtml(AlertMessage message) {
        StringBuilder sb = new StringBuilder();

        String rawTitle = (message.title() != null && !message.title().isBlank())
                ? message.title().trim()
                : "Earthquake alert";
        String escapedTitle = UntrustedContentEscaper.escapeTelegramHtml(truncate(rawTitle, TITLE_MAX_LENGTH));
        sb.append("<b>").append(escapedTitle.isBlank() ? "Earthquake alert" : escapedTitle).append("</b>\n\n");

        if (message.reviewNotice() != null && !message.reviewNotice().isBlank()) {
            String escapedNotice = UntrustedContentEscaper.escapeTelegramHtml(truncate(message.reviewNotice().trim(), NOTICE_MAX_LENGTH));
            if (!escapedNotice.isBlank()) {
                sb.append("⚠️ <i>").append(escapedNotice).append("</i>\n\n");
            }
        }

        if (message.formattedMagnitude() != null && !message.formattedMagnitude().isBlank()) {
            sb.append("<b>Magnitude:</b> ").append(UntrustedContentEscaper.escapeTelegramHtml(message.formattedMagnitude().trim())).append("\n");
        }
        if (message.formattedDepth() != null && !message.formattedDepth().isBlank()) {
            sb.append("<b>Depth:</b> ").append(UntrustedContentEscaper.escapeTelegramHtml(message.formattedDepth().trim())).append("\n");
        }
        if (message.formattedDistance() != null && !message.formattedDistance().isBlank()) {
            String escapedDistance = UntrustedContentEscaper.escapeTelegramHtml(message.formattedDistance().trim());
            if (!escapedDistance.isBlank()) {
                sb.append("<b>Distance:</b> ").append(escapedDistance).append("\n");
            }
        }
        if (message.place() != null && !message.place().isBlank()) {
            String escapedPlace = UntrustedContentEscaper.escapeTelegramHtml(truncate(message.place().trim(), PLACE_MAX_LENGTH));
            if (!escapedPlace.isBlank()) {
                sb.append("<b>Location:</b> ").append(escapedPlace).append("\n");
            }
        }
        if (message.formattedTimeManila() != null && !message.formattedTimeManila().isBlank()) {
            sb.append("<b>Time (PHT):</b> ").append(UntrustedContentEscaper.escapeTelegramHtml(message.formattedTimeManila().trim())).append("\n");
        }
        if (message.formattedTimeUtc() != null && !message.formattedTimeUtc().isBlank()) {
            sb.append("<b>Time (UTC):</b> ").append(UntrustedContentEscaper.escapeTelegramHtml(message.formattedTimeUtc().trim())).append("\n");
        }
        if (message.severity() != null && !message.severity().isBlank()) {
            sb.append("<b>Severity:</b> ").append(UntrustedContentEscaper.escapeTelegramHtml(message.severity().trim())).append("\n");
        }

        if (message.usgsUrl() != null && !message.usgsUrl().isBlank()) {
            String trimmedUrl = message.usgsUrl().trim();
            if (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) {
                sb.append("\n<a href=\"").append(UntrustedContentEscaper.escapeTelegramHtml(trimmedUrl)).append("\">USGS Event Details</a>\n");
            }
        }

        if (message.footer() != null && !message.footer().isBlank()) {
            String escapedFooter = UntrustedContentEscaper.escapeTelegramHtml(truncate(message.footer().trim(), FOOTER_MAX_LENGTH));
            if (!escapedFooter.isBlank()) {
                sb.append("\n<i>").append(escapedFooter).append("</i>");
            }
        }

        String rendered = sb.toString();
        return truncate(rendered, MAX_MESSAGE_LENGTH);
    }

    private String extractProviderMessageId(String responseBody, String destination) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("result")) {
                JsonNode resultNode = root.get("result");
                if (resultNode.hasNonNull("message_id")) {
                    return resultNode.get("message_id").asString();
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse Telegram message ID for destination {}: {}",
                    masker.mask(NotificationChannel.TELEGRAM, destination), ex.getClass().getSimpleName());
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

        if (statusCode == HttpStatus.BAD_REQUEST.value()) { // 400
            return new DeliveryResult.Permanent("HTTP 400 BAD_REQUEST", true);
        }

        if (statusCode == HttpStatus.FORBIDDEN.value()) { // 403
            return new DeliveryResult.Permanent("HTTP 403 FORBIDDEN", true);
        }

        if (statusCode == HttpStatus.NOT_FOUND.value()) { // 404
            return new DeliveryResult.Permanent("HTTP 404 NOT_FOUND", true);
        }

        if (statusCode == HttpStatus.UNAUTHORIZED.value()) { // 401
            return new DeliveryResult.Retryable("HTTP 401 INVALID_BOT_TOKEN");
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
                if (root.hasNonNull("parameters") && root.get("parameters").hasNonNull("retry_after")) {
                    double seconds = root.get("parameters").get("retry_after").asDouble();
                    long millis = Math.round(seconds * 1000.0);
                    if (millis > 0) {
                        return Duration.ofMillis(millis);
                    }
                } else if (root.hasNonNull("retry_after")) {
                    double seconds = root.get("retry_after").asDouble();
                    long millis = Math.round(seconds * 1000.0);
                    if (millis > 0) {
                        return Duration.ofMillis(millis);
                    }
                }
            } catch (Exception ex) {
                log.debug("Unable to parse retry_after from Telegram response body: {}", ex.getMessage());
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

    record TelegramSendMessageRequest(String chat_id, String text, String parse_mode) {}
}
