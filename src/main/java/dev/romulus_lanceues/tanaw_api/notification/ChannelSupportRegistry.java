package dev.romulus_lanceues.tanaw_api.notification;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChannelSupportRegistry {

    private final Map<NotificationChannel, ChannelVerifier> verifiers;

    public ChannelSupportRegistry(List<ChannelVerifier> verifierList) {
        this.verifiers = verifierList.stream()
                .collect(Collectors.toMap(ChannelVerifier::channel, Function.identity()));
    }

    public boolean isSupported(NotificationChannel channel) {
        return verifiers.containsKey(channel);
    }

    public void assertSupported(NotificationChannel channel) {
        if (!isSupported(channel)) {
            throw new UnsupportedNotificationChannelException(channel);
        }
    }

    public ChannelVerifier getVerifier(NotificationChannel channel) {
        ChannelVerifier verifier = verifiers.get(channel);
        if (verifier == null) {
            throw new UnsupportedNotificationChannelException(channel);
        }
        return verifier;
    }

    public Set<NotificationChannel> getSupportedChannels() {
        return Collections.unmodifiableSet(verifiers.keySet());
    }
}
