package dev.romulus_lanceues.tanaw_api.auth;


import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(JwtProperties .class)
    static class TestConfig{}

    private ApplicationContextRunner contextWithSecret(String secret) {
        return contextRunner.withPropertyValues(
                "security.jwt.issuer=test-issuer",
                "security.jwt.audience=test-audience",
                "security.jwt.access-token-duration=PT10M",
                "security.jwt.refresh-token-duration=P30D",
                "security.jwt.secret=" + secret
        );
    }

    private String secretOfLength(int length) {
        return Base64.getEncoder()
                .encodeToString(new byte[length]);
    }


    @Test
    void startupSucceedsWhenSecretIsExactly32Bytes() {
        contextWithSecret(secretOfLength(32))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(JwtProperties.class);
                });
    }

    @Test
    void startupSucceedsWhenSecretIsLongerThan32Bytes() {
        contextWithSecret(secretOfLength(33))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(JwtProperties.class);
                });
    }

    @Test
    void startupFailsWhenSecretIsShorterThan32Bytes() {
        contextWithSecret(secretOfLength(31))
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining(
                                    "at least 32 bytes"
                            );
                });
    }

    @Test
    void startupFailsWhenSecretIsNotValidBase64() {
        contextWithSecret("not-valid-base64!!!")
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining(
                                    "Illegal base64 character 2d"
                            );
                });
    }

    @Test
    void startupFailsWhenSecretIsBlank() {
        contextWithSecret(" ")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void startupFailsWhenAccessTokenDurationIsZero() {
        contextWithSecret(secretOfLength(32))
                .withPropertyValues(
                        "security.jwt.access-token-duration=PT0S"
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("must be positive");
                });
    }

    @Test
    void startupFailsWhenRefreshTokenDurationIsNegative() {
        contextWithSecret(secretOfLength(32))
                .withPropertyValues(
                        "security.jwt.refresh-token-duration=PT-1H"
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("must be positive");
                });
    }
}
