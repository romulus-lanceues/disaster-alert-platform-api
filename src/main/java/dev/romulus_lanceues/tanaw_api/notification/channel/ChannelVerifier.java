package dev.romulus_lanceues.tanaw_api.notification.channel;

public interface ChannelVerifier {

    NotificationChannel channel();

    void verify(String destination);
}
