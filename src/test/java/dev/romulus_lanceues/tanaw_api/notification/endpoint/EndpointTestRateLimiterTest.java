package dev.romulus_lanceues.tanaw_api.notification.endpoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EndpointTestRateLimiter Unit Tests")
class EndpointTestRateLimiterTest {

    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneOffset zone = ZoneOffset.UTC;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneOffset getZone() {
            return zone;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    private final Instant baseTime = Instant.parse("2026-10-15T12:00:00Z");
    private final MutableClock clock = new MutableClock(baseTime);
    private final EndpointTestRateLimiter rateLimiter = new EndpointTestRateLimiter(clock);

    @Test
    @DisplayName("Allows requests up to limit within the time window")
    void allowsRequestsUpToLimit() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> {
            rateLimiter.acquire(userId);
            rateLimiter.acquire(userId);
            rateLimiter.acquire(userId);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Throws RateLimitExceededException on request exceeding limit with positive retryAfter")
    void throwsRateLimitExceededOnExceedingLimit() {
        UUID userId = UUID.randomUUID();

        rateLimiter.acquire(userId);
        rateLimiter.acquire(userId);
        rateLimiter.acquire(userId);

        assertThatThrownBy(() -> rateLimiter.acquire(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded")
                .hasMessageContaining("60 seconds")
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfter()).isPositive();
                    assertThat(rle.getRetryAfter().toSeconds()).isLessThanOrEqualTo(60);
                });
    }

    @Test
    @DisplayName("Guarantees positive retry-after of at least 1 second when sub-second remaining")
    void guaranteesAtLeastOneSecondForSubSecondRemaining() {
        UUID userId = UUID.randomUUID();

        rateLimiter.acquire(userId);
        rateLimiter.acquire(userId);
        rateLimiter.acquire(userId);

        // Advance by 59 seconds and 500 milliseconds (500ms remaining in window)
        clock.advance(Duration.ofMillis(59_500));

        assertThatThrownBy(() -> rateLimiter.acquire(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfter().toSeconds()).isEqualTo(1);
                    assertThat(rle.getMessage()).contains("Try again in 1 seconds.");
                });
    }

    @Test
    @DisplayName("Resets allowance after time window elapses")
    void resetsAfterWindowElapses() {
        UUID userId = UUID.randomUUID();

        rateLimiter.acquire(userId);
        rateLimiter.acquire(userId);
        rateLimiter.acquire(userId);

        // Advance clock by 30 seconds - should still be rate limited
        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> rateLimiter.acquire(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfter().toSeconds()).isEqualTo(30);
                });

        // Advance clock another 31 seconds (total 61s from start) - earliest request expired
        clock.advance(Duration.ofSeconds(31));
        assertThatCode(() -> rateLimiter.acquire(userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rate limits different users independently")
    void rateLimitsIndependentlyPerUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        // Exhaust user A
        rateLimiter.acquire(userA);
        rateLimiter.acquire(userA);
        rateLimiter.acquire(userA);

        assertThatThrownBy(() -> rateLimiter.acquire(userA))
                .isInstanceOf(RateLimitExceededException.class);

        // User B is unaffected
        assertThatCode(() -> {
            rateLimiter.acquire(userB);
            rateLimiter.acquire(userB);
            rateLimiter.acquire(userB);
        }).doesNotThrowAnyException();
    }
}
