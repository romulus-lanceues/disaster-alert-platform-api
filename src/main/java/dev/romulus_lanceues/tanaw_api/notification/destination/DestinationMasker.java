package dev.romulus_lanceues.tanaw_api.notification.destination;

import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class DestinationMasker {

    public String mask(NotificationChannel channel, String destination) {
        if (channel == null || destination == null || destination.isBlank()) {
            return "";
        }

        String trimmed = destination.trim();

        return switch (channel) {
            case DISCORD -> maskDiscord(trimmed);
            case EMAIL -> maskEmail(trimmed);
            case TELEGRAM -> maskTelegram(trimmed);
        };
    }

    private String maskDiscord(String destination) {
        int lastSlash = destination.lastIndexOf('/');
        String token = (lastSlash >= 0 && lastSlash < destination.length() - 1)
                ? destination.substring(lastSlash + 1)
                : destination;

        String suffix = token.length() > 4 ? token.substring(token.length() - 4) : token;
        return "Discord webhook ..." + suffix;
    }

    private String maskEmail(String destination) {
        int atIndex = destination.indexOf('@');
        if (atIndex > 0) {
            String firstChar = destination.substring(0, 1);
            String domain = destination.substring(atIndex);
            return firstChar + "***" + domain;
        }

        String firstChar = destination.isEmpty() ? "" : destination.substring(0, 1);
        return firstChar + "***";
    }

    private String maskTelegram(String destination) {
        if (destination.startsWith("@")) {
            return destination;
        }

        String suffix = destination.length() > 4
                ? destination.substring(destination.length() - 4)
                : destination;
        return "Telegram chat ..." + suffix;
    }
}
