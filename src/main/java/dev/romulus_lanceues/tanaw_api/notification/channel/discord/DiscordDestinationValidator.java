package dev.romulus_lanceues.tanaw_api.notification.channel.discord;

import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DiscordDestinationValidator {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "discord.com",
            "discordapp.com",
            "ptb.discord.com",
            "canary.discord.com",
            "ptb.discordapp.com",
            "canary.discordapp.com"
    );

    // Discord snowflake IDs are numeric; webhook tokens are alphanumeric with dashes/underscores
    private static final Pattern WEBHOOK_PATH_PATTERN = Pattern.compile("^/api/webhooks/(\\d+)/([a-zA-Z0-9_-]+)$");

    public void validate(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new InvalidDestinationException("Discord webhook URL must not be null or blank");
        }

        URI uri;
        try {
            uri = new URI(destination.trim());
        } catch (URISyntaxException ex) {
            throw new InvalidDestinationException("Invalid Discord webhook URI syntax: " + ex.getReason(), ex);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            throw new InvalidDestinationException("Discord webhook URL must use HTTPS scheme");
        }

        if (uri.getUserInfo() != null) {
            throw new InvalidDestinationException("Discord webhook URL must not contain user-info");
        }

        if (uri.getQuery() != null) {
            throw new InvalidDestinationException("Discord webhook URL must not contain query parameters");
        }

        if (uri.getFragment() != null) {
            throw new InvalidDestinationException("Discord webhook URL must not contain fragments");
        }

        int port = uri.getPort();
        if (port != -1 && port != 443) {
            throw new InvalidDestinationException("Discord webhook URL must not use a non-standard port: " + port);
        }

        String host = uri.getHost();
        if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new InvalidDestinationException("Discord webhook URL host is not allowed: " + host);
        }

        String path = uri.getPath();
        if (path == null || !WEBHOOK_PATH_PATTERN.matcher(path).matches()) {
            throw new InvalidDestinationException(
                    "Discord webhook URL path must strictly match /api/webhooks/{id}/{token}"
            );
        }
    }
}
