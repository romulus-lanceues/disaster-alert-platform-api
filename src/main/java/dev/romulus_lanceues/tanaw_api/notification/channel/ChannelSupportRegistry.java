package dev.romulus_lanceues.tanaw_api.notification.channel;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central registry managing supported notification channels, their endpoint verifiers,
 * and their outbound delivery senders.
 */
@Component
public class ChannelSupportRegistry {

    private final Map<NotificationChannel, ChannelVerifier> verifiers;
    private final Map<NotificationChannel, NotificationSender> senders;

    public ChannelSupportRegistry(
            List<ChannelVerifier> verifiers,
            List<NotificationSender> senders
    ) {
        this.verifiers = verifiers == null
                ? Collections.emptyMap()
                : verifiers.stream().collect(Collectors.toMap(ChannelVerifier::channel, Function.identity()));
        this.senders = senders == null
                ? Collections.emptyMap()
                : senders.stream().collect(Collectors.toMap(NotificationSender::channel, Function.identity()));
    }

    /**
     * Checks if endpoint verification is supported for the given channel.
     */
    public boolean isSupported(NotificationChannel channel) {
        return verifiers.containsKey(channel);
    }

    /**
     * Asserts that endpoint verification is supported for the given channel,
     * throwing {@link UnsupportedNotificationChannelException} if not.
     */
    public void assertSupported(NotificationChannel channel) {
        if (!isSupported(channel)) {
            throw new UnsupportedNotificationChannelException(channel);
        }
    }

    /**
     * Retrieves the {@link ChannelVerifier} for the given channel,
     * throwing {@link UnsupportedNotificationChannelException} if not supported.
     */
    public ChannelVerifier getVerifier(NotificationChannel channel) {
        ChannelVerifier verifier = verifiers.get(channel);
        if (verifier == null) {
            throw new UnsupportedNotificationChannelException(channel);
        }
        return verifier;
    }

    /**
     * Returns an unmodifiable set of channels with active endpoint verifiers.
     */
    public Set<NotificationChannel> getSupportedChannels() {
        return Collections.unmodifiableSet(verifiers.keySet());
    }

    /**
     * Checks if an outbound notification sender is registered for the given channel.
     */
    public boolean hasSender(NotificationChannel channel) {
        return senders.containsKey(channel);
    }

    /**
     * Asserts that an outbound notification sender is registered for the given channel,
     * throwing {@link UnsupportedNotificationChannelException} if not.
     */
    public void assertSenderSupported(NotificationChannel channel) {
        if (!hasSender(channel)) {
            throw new UnsupportedNotificationChannelException(channel);
        }
    }

    /**
     * Retrieves the {@link NotificationSender} for the given channel,
     * throwing {@link UnsupportedNotificationChannelException} if not supported.
     */
    public NotificationSender getSender(NotificationChannel channel) {
        NotificationSender sender = senders.get(channel);
        if (sender == null) {
            throw new UnsupportedNotificationChannelException(channel);
        }
        return sender;
    }

    /**
     * Returns an unmodifiable set of channels with active notification senders.
     */
    public Set<NotificationChannel> getSupportedSenderChannels() {
        return Collections.unmodifiableSet(senders.keySet());
    }

    /**
     * Returns an unmodifiable map of registered notification senders keyed by channel.
     */
    public Map<NotificationChannel, NotificationSender> getSupportedSenders() {
        return Collections.unmodifiableMap(senders);
    }
}
