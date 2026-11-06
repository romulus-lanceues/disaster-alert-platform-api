package dev.romulus_lanceues.tanaw_api.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    public RestClient discordRestClient(NotificationProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.discord().connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.discord().readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public RestClient telegramRestClient(NotificationProperties properties) {
        Duration connectTimeout = (properties.telegram() != null && properties.telegram().connectTimeout() != null)
                ? properties.telegram().connectTimeout()
                : Duration.ofSeconds(3);
        Duration readTimeout = (properties.telegram() != null && properties.telegram().readTimeout() != null)
                ? properties.telegram().readTimeout()
                : Duration.ofSeconds(10);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
