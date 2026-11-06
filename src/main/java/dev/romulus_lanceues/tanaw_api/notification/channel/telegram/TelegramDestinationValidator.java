package dev.romulus_lanceues.tanaw_api.notification.channel.telegram;

import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class TelegramDestinationValidator {

    private static final Pattern NUMERIC_CHAT_ID_PATTERN = Pattern.compile("^-?[1-9][0-9]{0,19}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^@[a-zA-Z0-9_]{5,32}$");

    public void validate(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new InvalidDestinationException("Telegram destination must not be null or blank");
        }

        if (destination.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidDestinationException("Telegram destination must not contain whitespace");
        }

        if (destination.contains("://") || destination.contains("/") || destination.contains("?")
                || destination.contains("#") || destination.contains("&")) {
            throw new InvalidDestinationException("Telegram destination must not contain URL syntax or path characters");
        }

        if (destination.startsWith("@")) {
            if (!USERNAME_PATTERN.matcher(destination).matches()) {
                throw new InvalidDestinationException(
                        "Telegram destination username must start with @ and have 5 to 32 alphanumeric or underscore characters"
                );
            }
            return;
        }

        if (!NUMERIC_CHAT_ID_PATTERN.matcher(destination).matches()) {
            throw new InvalidDestinationException(
                    "Telegram destination must be a valid numeric chat ID (non-zero signed integer up to 20 digits) or @username"
            );
        }
    }
}
