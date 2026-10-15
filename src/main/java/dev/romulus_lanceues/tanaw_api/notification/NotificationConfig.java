package dev.romulus_lanceues.tanaw_api.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

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
}
