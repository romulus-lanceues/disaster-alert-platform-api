package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationRetryPolicy Unit Tests")
class NotificationRetryPolicyTest {

    private NotificationProperties properties;
    private AtomicReference<Double> jitterValue;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties(
                true,
                new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                new NotificationProperties.Fanout(Duration.ofHours(6)),
                new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new NotificationProperties.Crypto("dGVzdC1zZWNyZXQta2V5LTMyLWJ5dGVzLWxvbmctISEhIQ=="),
                null
        );
        jitterValue = new AtomicReference<>(0.5); // 0.5 is neutral jitter (multiplier = 1.0)
    }

    private NotificationRetryPolicy createPolicy() {
        return new NotificationRetryPolicy(properties, jitterValue::get);
    }

    @Nested
    @DisplayName("isExhausted")
    class IsExhausted {

        @Test
        @DisplayName("returns false when attempt count is below max attempts")
        void returnsFalseWhenBelowMaxAttempts() {
            NotificationRetryPolicy policy = createPolicy();

            assertThat(policy.isExhausted(0)).isFalse();
            assertThat(policy.isExhausted(1)).isFalse();
            assertThat(policy.isExhausted(2)).isFalse();
            assertThat(policy.isExhausted(3)).isFalse();
            assertThat(policy.isExhausted(4)).isFalse();
        }

        @Test
        @DisplayName("returns true when attempt count is at or above max attempts")
        void returnsTrueWhenAtOrAboveMaxAttempts() {
            NotificationRetryPolicy policy = createPolicy();

            assertThat(policy.isExhausted(5)).isTrue();
            assertThat(policy.isExhausted(6)).isTrue();
            assertThat(policy.isExhausted(10)).isTrue();
        }
    }

    @Nested
    @DisplayName("calculateDelay")
    class CalculateDelay {

        @Test
        @DisplayName("follows exponential progression with neutral jitter")
        void followsExponentialProgression() {
            NotificationRetryPolicy policy = createPolicy();

            // initial = 10s: attempt 1 -> 10s, attempt 2 -> 20s, attempt 3 -> 40s, attempt 4 -> 80s
            assertThat(policy.calculateDelay(1, null)).isEqualTo(Duration.ofSeconds(10));
            assertThat(policy.calculateDelay(2, null)).isEqualTo(Duration.ofSeconds(20));
            assertThat(policy.calculateDelay(3, null)).isEqualTo(Duration.ofSeconds(40));
            assertThat(policy.calculateDelay(4, null)).isEqualTo(Duration.ofSeconds(80));
        }

        @Test
        @DisplayName("caps delay at max backoff")
        void capsDelayAtMaxBackoff() {
            NotificationRetryPolicy policy = createPolicy();

            // maxBackoff is 10 minutes (600s). For attempt 7, 10s * 2^6 = 640s, capped to 600s.
            assertThat(policy.calculateDelay(7, null)).isEqualTo(Duration.ofMinutes(10));
            assertThat(policy.calculateDelay(10, null)).isEqualTo(Duration.ofMinutes(10));
        }

        @Test
        @DisplayName("applies bounded jitter correctly (±20%)")
        void appliesBoundedJitter() {
            // base for attempt 1 is 10s
            // jitter 0.0 -> factor 0.8 -> 8s
            jitterValue.set(0.0);
            assertThat(createPolicy().calculateDelay(1, null)).isEqualTo(Duration.ofSeconds(8));

            // jitter 1.0 -> factor 1.2 -> 12s
            jitterValue.set(1.0);
            assertThat(createPolicy().calculateDelay(1, null)).isEqualTo(Duration.ofSeconds(12));

            // jitter 0.5 -> factor 1.0 -> 10s
            jitterValue.set(0.5);
            assertThat(createPolicy().calculateDelay(1, null)).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("honors provider retry_after when greater than computed exponential backoff")
        void honorsProviderRetryAfterWhenLonger() {
            NotificationRetryPolicy policy = createPolicy();

            // At attempt 1, base delay is 10s. Provider specifies 45s -> should use 45s.
            assertThat(policy.calculateDelay(1, Duration.ofSeconds(45))).isEqualTo(Duration.ofSeconds(45));

            // At attempt 4, base delay is 80s. Provider specifies 15s -> should use 80s.
            assertThat(policy.calculateDelay(4, Duration.ofSeconds(15))).isEqualTo(Duration.ofSeconds(80));
        }

        @Test
        @DisplayName("ignores provider retry_after when null, zero, or negative")
        void ignoresInvalidProviderRetryAfter() {
            NotificationRetryPolicy policy = createPolicy();

            assertThat(policy.calculateDelay(1, null)).isEqualTo(Duration.ofSeconds(10));
            assertThat(policy.calculateDelay(1, Duration.ZERO)).isEqualTo(Duration.ofSeconds(10));
            assertThat(policy.calculateDelay(1, Duration.ofSeconds(-5))).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("handles non-positive attempt counts safely without error")
        void handlesNonPositiveAttemptCounts() {
            NotificationRetryPolicy policy = createPolicy();

            assertThat(policy.calculateDelay(0, null)).isEqualTo(Duration.ofSeconds(10));
            assertThat(policy.calculateDelay(-5, null)).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("handles very large attempt counts without arithmetic overflow")
        void handlesVeryLargeAttemptCountsWithoutOverflow() {
            NotificationRetryPolicy policy = createPolicy();

            assertThat(policy.calculateDelay(100, null)).isEqualTo(Duration.ofMinutes(10));
            assertThat(policy.calculateDelay(Integer.MAX_VALUE, null)).isEqualTo(Duration.ofMinutes(10));
        }
    }

    @Nested
    @DisplayName("calculateNextAttempt")
    class CalculateNextAttempt {

        @Test
        @DisplayName("computes target Instant by adding calculated delay to current time")
        void computesTargetInstant() {
            NotificationRetryPolicy policy = createPolicy();
            Instant now = Instant.parse("2026-10-20T10:00:00Z");

            Instant nextAttempt = policy.calculateNextAttempt(1, null, now);
            assertThat(nextAttempt).isEqualTo(Instant.parse("2026-10-20T10:00:10Z"));
        }
    }
}
