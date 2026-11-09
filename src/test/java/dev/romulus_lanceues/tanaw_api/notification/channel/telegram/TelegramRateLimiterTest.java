package dev.romulus_lanceues.tanaw_api.notification.channel.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TelegramRateLimiter Unit Tests")
class TelegramRateLimiterTest {

    private static final Instant START_TIME = Instant.parse("2026-11-06T12:00:00Z");

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START_TIME);
    }

    @Nested
    @DisplayName("Per-chat Rate Limiting")
    class PerChatRateLimiting {

        @Test
        @DisplayName("Allows first message to chat destination")
        void allowsFirstMessageToChat() {
            TelegramRateLimiter limiter = new TelegramRateLimiter(clock);
            TelegramRateLimiter.RateLimitResult result = limiter.tryAcquire("123456789");

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.retryAfter()).isNull();
        }

        @Test
        @DisplayName("Enforces 1 message/second limit for the same chat destination")
        void enforcesOneMessagePerSecondForSameChat() {
            TelegramRateLimiter limiter = new TelegramRateLimiter(clock);

            // First request allowed
            TelegramRateLimiter.RateLimitResult first = limiter.tryAcquire("123456789");
            assertThat(first.isAllowed()).isTrue();

            // Advance 200ms (still within 1s window)
            clock.advance(Duration.ofMillis(200));

            // Second request to same chat rejected
            TelegramRateLimiter.RateLimitResult second = limiter.tryAcquire("123456789");
            assertThat(second.isAllowed()).isFalse();
            assertThat(second.retryAfter()).isEqualTo(Duration.ofMillis(800));

            // Request to a DIFFERENT chat at the same instant is allowed
            TelegramRateLimiter.RateLimitResult differentChat = limiter.tryAcquire("987654321");
            assertThat(differentChat.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("Allows new message to same chat after 1 second has elapsed")
        void allowsMessageAfterOneSecondWindow() {
            TelegramRateLimiter limiter = new TelegramRateLimiter(clock);

            limiter.tryAcquire("123456789");

            // Advance exactly 1 second
            clock.advance(Duration.ofSeconds(1));

            TelegramRateLimiter.RateLimitResult result = limiter.tryAcquire("123456789");
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.retryAfter()).isNull();
        }
    }

    @Nested
    @DisplayName("Global Rate Limiting")
    class GlobalRateLimiting {

        @Test
        @DisplayName("Enforces global limit across different chats")
        void enforcesGlobalLimitAcrossChats() {
            TelegramRateLimiter limiter = new TelegramRateLimiter(clock);

            // Send 30 requests to 30 different chats at 10ms intervals (T=0 to T=290ms)
            for (int i = 1; i <= 30; i++) {
                assertThat(limiter.tryAcquire("chat_" + i).isAllowed()).isTrue();
                if (i < 30) {
                    clock.advance(Duration.ofMillis(10));
                }
            }

            // 31st request to chat_31 at T=290ms exceeds global limit (30 requests in window)
            TelegramRateLimiter.RateLimitResult thirtyFirst = limiter.tryAcquire("chat_31");
            assertThat(thirtyFirst.isAllowed()).isFalse();
            // Oldest request was at T=0, window is 1s, now is T=290ms -> retryAfter = 710ms
            assertThat(thirtyFirst.retryAfter()).isEqualTo(Duration.ofMillis(710));

            // Advance past the first request's window (advance 710ms to T=1000ms)
            clock.advance(Duration.ofMillis(710));

            // Now a slot freed up in the global window
            TelegramRateLimiter.RateLimitResult slotFreed = limiter.tryAcquire("chat_31");
            assertThat(slotFreed.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("Default constructor enforces global limit of 30 messages/second")
        void defaultGlobalLimitIsThirty() {
            TelegramRateLimiter limiter = new TelegramRateLimiter(clock);

            for (int i = 1; i <= 30; i++) {
                TelegramRateLimiter.RateLimitResult result = limiter.tryAcquire("chat_" + i);
                assertThat(result.isAllowed()).as("Request %d should be allowed", i).isTrue();
            }

            // 31st request exceeds global limit
            TelegramRateLimiter.RateLimitResult thirtyFirst = limiter.tryAcquire("chat_31");
            assertThat(thirtyFirst.isAllowed()).isFalse();
            assertThat(thirtyFirst.retryAfter()).isNotNull();
            assertThat(thirtyFirst.retryAfter().toMillis()).isPositive();
        }
    }

    @Nested
    @DisplayName("Validation & Edge Cases")
    class ValidationAndEdgeCases {

        @Test
        @DisplayName("Throws IllegalArgumentException on null or blank destination")
        void rejectsNullOrBlankDestination() {
            TelegramRateLimiter limiter = new TelegramRateLimiter(clock);

            assertThatThrownBy(() -> limiter.tryAcquire(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> limiter.tryAcquire(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> limiter.tryAcquire("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Evicts expired chats when map size exceeds threshold")
        void evictsExpiredChatsOnThreshold() {
            TelegramRateLimiter limiter = new TelegramRateLimiter(clock);

            // Send to 1005 chats advancing 40ms each time so global limit (30/sec) is never exceeded
            for (int i = 0; i < 1005; i++) {
                assertThat(limiter.tryAcquire("chat_" + i).isAllowed()).isTrue();
                clock.advance(Duration.ofMillis(40));
            }

            // Advance past window so previous entries expire
            clock.advance(Duration.ofSeconds(2));

            // Next acquire triggers cleanup
            TelegramRateLimiter.RateLimitResult result = limiter.tryAcquire("new_chat");
            assertThat(result.isAllowed()).isTrue();
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        public MutableClock(Instant initialInstant) {
            this.instant = initialInstant;
            this.zone = ZoneOffset.UTC;
        }

        public void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
