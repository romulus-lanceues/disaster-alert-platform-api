package dev.romulus_lanceues.tanaw_api.notification.channel.email;

import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validates RFC 5322 email syntax and strictly enforces security constraints
 * including CRLF injection prevention and length boundaries to protect SMTP transport.
 */
@Component
public class EmailDestinationValidator {

    private static final int MAX_TOTAL_LENGTH = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;

    private static final Pattern LOCAL_PART_PATTERN =
            Pattern.compile("^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*$");

    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$");

    /**
     * Validates that the provided email destination conforms to RFC 5322 format
     * and contains no control characters, whitespace, or CRLF injection vectors.
     *
     * @param destination the email address to validate
     * @throws InvalidDestinationException if the email is null, blank, malformed, or contains dangerous characters
     */
    public void validate(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new InvalidDestinationException("Email destination must not be null or blank");
        }

        if (destination.length() > MAX_TOTAL_LENGTH) {
            throw new InvalidDestinationException(
                    "Email destination exceeds maximum length of 254 characters: " + destination.length()
            );
        }

        for (int i = 0; i < destination.length(); i++) {
            char c = destination.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0' || (c < 32 && !Character.isWhitespace(c)) || c == 127) {
                throw new InvalidDestinationException(
                        "Email destination must not contain control characters or line breaks"
                );
            }
            if (Character.isWhitespace(c)) {
                throw new InvalidDestinationException("Email destination must not contain whitespace");
            }
        }

        int atIndex = destination.indexOf('@');
        int lastAtIndex = destination.lastIndexOf('@');
        if (atIndex <= 0 || atIndex != lastAtIndex || atIndex == destination.length() - 1) {
            throw new InvalidDestinationException(
                    "Email destination must contain exactly one '@' separating local-part and domain"
            );
        }

        String localPart = destination.substring(0, atIndex);
        String domain = destination.substring(atIndex + 1);

        if (localPart.length() > MAX_LOCAL_PART_LENGTH) {
            throw new InvalidDestinationException(
                    "Email local-part exceeds maximum length of 64 characters: " + localPart.length()
            );
        }

        if (localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) {
            throw new InvalidDestinationException(
                    "Email local-part must not start or end with a dot or contain consecutive dots"
            );
        }

        if (!LOCAL_PART_PATTERN.matcher(localPart).matches()) {
            throw new InvalidDestinationException("Email local-part contains invalid characters or structure");
        }

        if (domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) {
            throw new InvalidDestinationException(
                    "Email domain must not start or end with a dot or contain consecutive dots"
            );
        }

        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new InvalidDestinationException("Email domain contains invalid characters or structure");
        }
    }
}
