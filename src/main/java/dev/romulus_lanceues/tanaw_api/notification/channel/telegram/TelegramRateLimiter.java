package dev.romulus_lanceues.tanaw_api.notification.channel.telegram;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory proactive rate limiter enforcing Telegram Bot API limits:
 * <ul>
 *     <li>Global limit: at most 30 messages/second across all chats</li>
 *     <li>Per-chat limit: at most 1 message/second to the same chat destination</li>
 * </ul>
 * Operates deterministically via an injected {@link Clock} without blocking or sleeping threads.
 */
@Component
public class TelegramRateLimiter {

    public static final int DEFAULT_GLOBAL_LIMIT = 30;
    public static final int DEFAULT_CHAT_LIMIT = 1;
    public static final Duration DEFAULT_WINDOW = Duration.ofSeconds(1);
    private static final int MAX_TRACKED_CHATS = 1000;

    private final Clock clock;
    private final int maxGlobalRequests;
    private final int maxChatRequests;
    private final Duration window;

    private final Deque<Instant> globalTimestamps = new ArrayDeque<>();
    private final Map<String, Deque<Instant>> chatTimestamps = new ConcurrentHashMap<>();

    public TelegramRateLimiter(Clock clock) {
        this.clock = clock;
        this.maxGlobalRequests = DEFAULT_GLOBAL_LIMIT;
        this.maxChatRequests = DEFAULT_CHAT_LIMIT;
        this.window = DEFAULT_WINDOW;
    }

    /**
     * Attempts to acquire a rate limit slot for the given destination.
     *
     * @param destination the Telegram chat ID or username
     * @return {@link RateLimitResult} indicating whether delivery is permitted or limited with retry delay
     */
    public synchronized RateLimitResult tryAcquire(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destination must not be null or blank");
        }

        Instant now = clock.instant();
        Instant cutoff = now.minus(window);

        // 1. Evict expired global timestamps (older than or equal to cutoff)
        while (!globalTimestamps.isEmpty() && !globalTimestamps.peekFirst().isAfter(cutoff)) {
            globalTimestamps.pollFirst();
        }

        // 2. Check per-chat limit
        Deque<Instant> chatQueue = chatTimestamps.computeIfAbsent(destination, k -> new ArrayDeque<>());
        while (!chatQueue.isEmpty() && !chatQueue.peekFirst().isAfter(cutoff)) {
            chatQueue.pollFirst();
        }

        Duration chatRetryAfter = null;
        if (chatQueue.size() >= maxChatRequests) {
            Instant oldestChat = chatQueue.peekFirst();
            Duration delay = Duration.between(now, oldestChat.plus(window));
            chatRetryAfter = delay.isNegative() || delay.isZero() ? Duration.ofMillis(1) : delay;
        }

        // 3. Check global limit
        Duration globalRetryAfter = null;
        if (globalTimestamps.size() >= maxGlobalRequests) {
            Instant oldestGlobal = globalTimestamps.peekFirst();
            Duration delay = Duration.between(now, oldestGlobal.plus(window));
            globalRetryAfter = delay.isNegative() || delay.isZero() ? Duration.ofMillis(1) : delay;
        }

        // 4. If either is limited, reject and return the longest required backoff
        if (chatRetryAfter != null || globalRetryAfter != null) {
            Duration maxDelay;
            if (chatRetryAfter != null && globalRetryAfter != null) {
                maxDelay = chatRetryAfter.compareTo(globalRetryAfter) >= 0 ? chatRetryAfter : globalRetryAfter;
            } else if (chatRetryAfter != null) {
                maxDelay = chatRetryAfter;
            } else {
                maxDelay = globalRetryAfter;
            }
            return RateLimitResult.limited(maxDelay);
        }

        // 5. Permitted: record timestamp in both queues
        chatQueue.addLast(now);
        globalTimestamps.addLast(now);

        // Periodic cleanup if tracked chats map exceeds threshold
        if (chatTimestamps.size() > MAX_TRACKED_CHATS) {
            chatTimestamps.entrySet().removeIf(entry -> {
                Deque<Instant> q = entry.getValue();
                while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) {
                    q.pollFirst();
                }
                return q.isEmpty();
            });
        }

        return RateLimitResult.allowed();
    }

    public record RateLimitResult(boolean isAllowed, Duration retryAfter) {
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null);
        }

        public static RateLimitResult limited(Duration retryAfter) {
            return new RateLimitResult(false, retryAfter);
        }
    }
}
