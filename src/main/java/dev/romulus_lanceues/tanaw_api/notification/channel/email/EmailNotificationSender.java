package dev.romulus_lanceues.tanaw_api.notification.channel.email;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.AlertMessage;
import dev.romulus_lanceues.tanaw_api.notification.channel.DeliveryResult;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationSender;
import dev.romulus_lanceues.tanaw_api.notification.channel.UntrustedContentEscaper;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Outbound notification sender for delivering earthquake alerts to email endpoints via SMTP/MIME.
 * Features include:
 * <ul>
 *     <li>Destination validation and SMTP injection prevention via {@link EmailDestinationValidator}</li>
 *     <li>Deterministic RFC 5322 Message-ID header generation for tracking and deduplication</li>
 *     <li>Multipart/alternative MIME message construction (structured plain text fallback + responsive HTML card)</li>
 *     <li>Untrusted HTML content escaping for all alert attributes</li>
 *     <li>Severity-based color badge rendering (#E74C3C, #E67E22, #F1C40F, #3498DB)</li>
 *     <li>Dry-run mode bypassing SMTP network operations and logging masked recipients</li>
 *     <li>Exception classification into sealed {@link DeliveryResult}</li>
 *     <li>PII and credential hygiene preventing email leakage in logs and failure reasons</li>
 * </ul>
 */
@Component
@Slf4j
public class EmailNotificationSender implements NotificationSender {

    private static final String DEFAULT_FROM_ADDRESS = "no-reply@tanaw.dev";
    private static final String DEFAULT_FROM_NAME = "Tanaw Alerts";
    private static final String DEFAULT_SUBJECT = "Earthquake alert";

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final EmailDestinationValidator validator;
    private final DestinationMasker masker;

    public EmailNotificationSender(
            JavaMailSender mailSender,
            NotificationProperties properties,
            EmailDestinationValidator validator,
            DestinationMasker masker
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.validator = validator;
        this.masker = masker;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public DeliveryResult send(AlertMessage message, String destination) {
        if (destination == null || destination.isBlank()) {
            return new DeliveryResult.Permanent("DESTINATION_BLANK", true);
        }

        try {
            validator.validate(destination);
        } catch (InvalidDestinationException ex) {
            log.warn("Invalid email destination rejected: {}", ex.getMessage());
            return new DeliveryResult.Permanent("INVALID_DESTINATION", true);
        }

        if (message == null) {
            return new DeliveryResult.Permanent("ALERT_MESSAGE_NULL", false);
        }

        String maskedDestination = masker.mask(NotificationChannel.EMAIL, destination);

        if (Boolean.TRUE.equals(properties.dryRun())) {
            log.info("Dry-run mode enabled: skipping outbound Email alert to {}. Title: '{}'",
                    maskedDestination, message.title());
            return new DeliveryResult.Sent("dry-run");
        }

        String fromAddress = (properties.email() != null && properties.email().fromAddress() != null && !properties.email().fromAddress().isBlank())
                ? properties.email().fromAddress().trim()
                : DEFAULT_FROM_ADDRESS;
        String fromName = (properties.email() != null && properties.email().fromName() != null && !properties.email().fromName().isBlank())
                ? properties.email().fromName().trim()
                : DEFAULT_FROM_NAME;

        String domain = extractDomain(fromAddress);
        String seed = destination + "|" + (message.title() != null ? message.title() : "") + "|" + (message.occurredAt() != null ? message.occurredAt().toString() : "");
        UUID messageUuid = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        String messageId = "<" + messageUuid + "@" + domain + ">";

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress, fromName);
            helper.setTo(destination);

            String subject = (message.title() != null && !message.title().isBlank())
                    ? message.title().trim()
                    : DEFAULT_SUBJECT;
            helper.setSubject(subject);

            String plainText = buildPlainText(message);
            String htmlText = buildHtml(message);
            helper.setText(plainText, htmlText);

            mimeMessage.saveChanges();
            mimeMessage.setHeader("Message-ID", messageId);

            mailSender.send(mimeMessage);

            log.info("Successfully delivered Email alert to {} with messageId: {}", maskedDestination, messageId);
            return new DeliveryResult.Sent(messageId);

        } catch (Exception ex) {
            return classifyDeliveryException(ex, destination, maskedDestination);
        }
    }

    private DeliveryResult classifyDeliveryException(Exception ex, String destination, String maskedDestination) {
        if (isAuthError(ex)) {
            log.error("Email delivery authentication failed for destination {}", maskedDestination);
            return new DeliveryResult.Retryable("EMAIL_AUTH_ERROR");
        }

        if (isInvalidRecipient(ex)) {
            log.warn("Email delivery rejected due to invalid recipient for destination {}", maskedDestination);
            return new DeliveryResult.Permanent("INVALID_RECIPIENT", true);
        }

        if (isTimeoutException(ex)) {
            log.warn("Email delivery timed out for destination {}", maskedDestination);
            return new DeliveryResult.Retryable("TIMEOUT");
        }

        if (ex instanceof MailSendException) {
            log.warn("Email send failed for destination {}: {}", maskedDestination, ex.getClass().getSimpleName());
            return new DeliveryResult.Retryable("EMAIL_SEND_FAILED");
        }

        if (ex instanceof MailException) {
            log.warn("Email service error for destination {}: {}", maskedDestination, ex.getClass().getSimpleName());
            return new DeliveryResult.Retryable("MAIL_SERVICE_ERROR");
        }

        log.warn("Unexpected error during Email delivery for destination {}: {}", maskedDestination, ex.getClass().getSimpleName());
        return new DeliveryResult.Retryable("UNEXPECTED_ERROR: " + ex.getClass().getSimpleName());
    }

    private static String extractDomain(String fromAddress) {
        int atIndex = fromAddress.lastIndexOf('@');
        if (atIndex != -1 && atIndex < fromAddress.length() - 1) {
            String domain = fromAddress.substring(atIndex + 1).trim();
            if (!domain.isBlank()) {
                return domain;
            }
        }
        return "tanaw.dev";
    }

    private static boolean isAuthError(Throwable ex) {
        if (ex instanceof MailAuthenticationException) {
            return true;
        }
        Throwable current = ex;
        while (current != null) {
            if (current instanceof jakarta.mail.AuthenticationFailedException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("AuthenticationFailedException")) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("535") || lower.contains("authentication failed") || lower.contains("bad credentials")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        if (ex instanceof MailSendException mse) {
            for (Exception msgEx : mse.getMessageExceptions()) {
                if (isAuthError(msgEx)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInvalidRecipient(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof jakarta.mail.SendFailedException sfe) {
                if (sfe.getInvalidAddresses() != null && sfe.getInvalidAddresses().length > 0) {
                    return true;
                }
            }
            String className = current.getClass().getName();
            if (className.contains("SMTPAddressFailedException")) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("550") || lower.contains("553")
                        || lower.contains("invalid recipient")
                        || lower.contains("invalid address")
                        || lower.contains("unknown user")
                        || lower.contains("user unknown")
                        || lower.contains("mailbox unavailable")
                        || lower.contains("mailbox not found")
                        || lower.contains("no such user")
                        || lower.contains("recipient address rejected")
                        || lower.contains("user not found")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        if (ex instanceof MailSendException mse) {
            for (Exception msgEx : mse.getMessageExceptions()) {
                if (isInvalidRecipient(msgEx)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        if (ex instanceof MailSendException mse) {
            for (Exception msgEx : mse.getMessageExceptions()) {
                if (isTimeoutException(msgEx)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getSeverityColor(String severity) {
        if (severity == null || severity.isBlank()) {
            return "#3498DB"; // Default Blue
        }
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "#E74C3C"; // Red
            case "HIGH" -> "#E67E22";     // Orange
            case "MODERATE" -> "#F1C40F"; // Yellow
            default -> "#3498DB";         // Blue
        };
    }

    private String buildPlainText(AlertMessage message) {
        StringBuilder sb = new StringBuilder();

        String rawTitle = (message.title() != null && !message.title().isBlank())
                ? message.title().trim()
                : DEFAULT_SUBJECT;
        sb.append(rawTitle).append("\n\n");

        if (message.reviewNotice() != null && !message.reviewNotice().isBlank()) {
            sb.append("Notice: ").append(message.reviewNotice().trim()).append("\n\n");
        }

        if (message.formattedMagnitude() != null && !message.formattedMagnitude().isBlank()) {
            sb.append("Magnitude: ").append(message.formattedMagnitude().trim()).append("\n");
        } else if (message.magnitude() != null) {
            sb.append("Magnitude: M ").append(message.magnitude()).append("\n");
        }

        if (message.formattedDistance() != null && !message.formattedDistance().isBlank()) {
            sb.append("Distance: ").append(message.formattedDistance().trim()).append("\n");
        } else if (message.distanceKm() != null) {
            sb.append("Distance: ").append(message.distanceKm()).append(" km\n");
        }

        if (message.locationName() != null && !message.locationName().isBlank()) {
            sb.append("Monitored Location: ").append(message.locationName().trim()).append("\n");
        }

        if (message.place() != null && !message.place().isBlank()) {
            sb.append("Place: ").append(message.place().trim()).append("\n");
        }

        if (message.formattedDepth() != null && !message.formattedDepth().isBlank()) {
            sb.append("Depth: ").append(message.formattedDepth().trim()).append("\n");
        } else if (message.depthKm() != null) {
            sb.append("Depth: ").append(message.depthKm()).append(" km\n");
        }

        if (message.formattedTimeManila() != null && !message.formattedTimeManila().isBlank()) {
            sb.append("Time (PHT): ").append(message.formattedTimeManila().trim()).append("\n");
        }

        if (message.formattedTimeUtc() != null && !message.formattedTimeUtc().isBlank()) {
            sb.append("Time (UTC): ").append(message.formattedTimeUtc().trim()).append("\n");
        }

        if (message.severity() != null && !message.severity().isBlank()) {
            sb.append("Severity: ").append(message.severity().trim()).append("\n");
        }

        if (message.usgsUrl() != null && !message.usgsUrl().isBlank()) {
            String trimmedUrl = message.usgsUrl().trim();
            if (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) {
                sb.append("\nUSGS Event Details: ").append(trimmedUrl).append("\n");
            }
        }

        if (message.footer() != null && !message.footer().isBlank()) {
            sb.append("\n").append(message.footer().trim()).append("\n");
        }

        return sb.toString();
    }

    private String buildHtml(AlertMessage message) {
        String severity = (message.severity() != null && !message.severity().isBlank())
                ? message.severity().trim()
                : "INFO";
        String severityColor = getSeverityColor(severity);
        String escapedSeverity = UntrustedContentEscaper.escapeEmailHtml(severity);

        String rawTitle = (message.title() != null && !message.title().isBlank())
                ? message.title().trim()
                : DEFAULT_SUBJECT;
        String escapedTitle = UntrustedContentEscaper.escapeEmailHtml(rawTitle);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n<head>\n");
        sb.append("  <meta charset=\"utf-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>").append(escapedTitle).append("</title>\n");
        sb.append("</head>\n");
        sb.append("<body style=\"margin: 0; padding: 0; background-color: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1f2937;\">\n");
        sb.append("  <div style=\"max-width: 600px; margin: 20px auto; padding: 0 16px;\">\n");
        sb.append("    <div style=\"background-color: #ffffff; border-radius: 8px; border: 1px solid #e5e7eb; overflow: hidden; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);\">\n");
        sb.append("      <div style=\"background-color: ").append(severityColor).append("; height: 6px;\"></div>\n");
        sb.append("      <div style=\"padding: 24px;\">\n");
        sb.append("        <div style=\"margin-bottom: 16px;\">\n");
        sb.append("          <span style=\"display: inline-block; background-color: ").append(severityColor)
                .append("; color: #ffffff; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 700; text-transform: uppercase;\">")
                .append(escapedSeverity).append("</span>\n");
        sb.append("        </div>\n");
        sb.append("        <h1 style=\"margin: 0 0 16px 0; font-size: 20px; font-weight: 700; color: #111827; line-height: 1.3;\">")
                .append(escapedTitle).append("</h1>\n");

        if (message.reviewNotice() != null && !message.reviewNotice().isBlank()) {
            String escapedNotice = UntrustedContentEscaper.escapeEmailHtml(message.reviewNotice().trim());
            sb.append("        <div style=\"background-color: #fffbe8; border-left: 4px solid #f1c40f; padding: 12px; margin-bottom: 16px; border-radius: 4px; font-size: 14px;\">\n");
            sb.append("          <strong>Notice:</strong> ").append(escapedNotice).append("\n");
            sb.append("        </div>\n");
        }

        sb.append("        <table style=\"width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 14px;\">\n");
        sb.append("          <tbody>\n");

        if (message.formattedMagnitude() != null && !message.formattedMagnitude().isBlank()) {
            appendTableRow(sb, "Magnitude", UntrustedContentEscaper.escapeEmailHtml(message.formattedMagnitude().trim()));
        } else if (message.magnitude() != null) {
            appendTableRow(sb, "Magnitude", "M " + message.magnitude());
        }

        if (message.formattedDistance() != null && !message.formattedDistance().isBlank()) {
            appendTableRow(sb, "Distance", UntrustedContentEscaper.escapeEmailHtml(message.formattedDistance().trim()));
        } else if (message.distanceKm() != null) {
            appendTableRow(sb, "Distance", message.distanceKm() + " km");
        }

        if (message.locationName() != null && !message.locationName().isBlank()) {
            appendTableRow(sb, "Monitored Location", UntrustedContentEscaper.escapeEmailHtml(message.locationName().trim()));
        }

        if (message.place() != null && !message.place().isBlank()) {
            appendTableRow(sb, "Place", UntrustedContentEscaper.escapeEmailHtml(message.place().trim()));
        }

        if (message.formattedDepth() != null && !message.formattedDepth().isBlank()) {
            appendTableRow(sb, "Depth", UntrustedContentEscaper.escapeEmailHtml(message.formattedDepth().trim()));
        } else if (message.depthKm() != null) {
            appendTableRow(sb, "Depth", message.depthKm() + " km");
        }

        if (message.formattedTimeManila() != null && !message.formattedTimeManila().isBlank()) {
            appendTableRow(sb, "Time (PHT)", UntrustedContentEscaper.escapeEmailHtml(message.formattedTimeManila().trim()));
        }

        if (message.formattedTimeUtc() != null && !message.formattedTimeUtc().isBlank()) {
            appendTableRow(sb, "Time (UTC)", UntrustedContentEscaper.escapeEmailHtml(message.formattedTimeUtc().trim()));
        }

        appendTableRow(sb, "Severity", escapedSeverity);

        sb.append("          </tbody>\n");
        sb.append("        </table>\n");

        if (message.usgsUrl() != null && !message.usgsUrl().isBlank()) {
            String trimmedUrl = message.usgsUrl().trim();
            if (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) {
                String escapedUrl = UntrustedContentEscaper.escapeEmailHtml(trimmedUrl);
                sb.append("        <div style=\"margin-top: 24px;\">\n");
                sb.append("          <a href=\"").append(escapedUrl)
                        .append("\" style=\"display: inline-block; background-color: #2563eb; color: #ffffff; padding: 10px 18px; border-radius: 6px; text-decoration: none; font-weight: 500; font-size: 14px;\">View on USGS</a>\n");
                sb.append("        </div>\n");
            }
        }

        if (message.footer() != null && !message.footer().isBlank()) {
            String escapedFooter = UntrustedContentEscaper.escapeEmailHtml(message.footer().trim());
            sb.append("        <div style=\"margin-top: 24px; padding-top: 16px; border-top: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; text-align: center;\">\n");
            sb.append("          ").append(escapedFooter).append("\n");
            sb.append("        </div>\n");
        }

        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private static void appendTableRow(StringBuilder sb, String label, String escapedValue) {
        sb.append("            <tr>\n");
        sb.append("              <td style=\"padding: 8px 0; color: #6b7280; width: 40%; vertical-align: top; font-weight: 500;\">")
                .append(label).append("</td>\n");
        sb.append("              <td style=\"padding: 8px 0; color: #111827; font-weight: 600; vertical-align: top;\">")
                .append(escapedValue).append("</td>\n");
        sb.append("            </tr>\n");
    }
}
