package dev.romulus_lanceues.tanaw_api.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Properties;

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

    @Bean
    public static BeanPostProcessor javaMailSenderTimeoutPostProcessor(
            ObjectProvider<NotificationProperties> propertiesProvider
    ) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof JavaMailSenderImpl mailSender) {
                    NotificationProperties properties = propertiesProvider.getIfAvailable();
                    Duration connectTimeout = (properties != null && properties.email() != null && properties.email().connectTimeout() != null)
                            ? properties.email().connectTimeout()
                            : Duration.ofSeconds(5);
                    Duration readTimeout = (properties != null && properties.email() != null && properties.email().readTimeout() != null)
                            ? properties.email().readTimeout()
                            : Duration.ofSeconds(10);

                    Properties javaMailProperties = mailSender.getJavaMailProperties();
                    String connectTimeoutMs = String.valueOf(connectTimeout.toMillis());
                    String readTimeoutMs = String.valueOf(readTimeout.toMillis());

                    javaMailProperties.putIfAbsent("mail.smtp.connectiontimeout", connectTimeoutMs);
                    javaMailProperties.putIfAbsent("mail.smtp.timeout", readTimeoutMs);
                    javaMailProperties.putIfAbsent("mail.smtp.writetimeout", readTimeoutMs);

                    javaMailProperties.putIfAbsent("mail.smtps.connectiontimeout", connectTimeoutMs);
                    javaMailProperties.putIfAbsent("mail.smtps.timeout", readTimeoutMs);
                    javaMailProperties.putIfAbsent("mail.smtps.writetimeout", readTimeoutMs);
                }
                return bean;
            }
        };
    }
}
