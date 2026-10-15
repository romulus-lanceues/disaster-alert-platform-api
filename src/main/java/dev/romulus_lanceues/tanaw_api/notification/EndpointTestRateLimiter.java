package dev.romulus_lanceues.tanaw_api.notification;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class EndpointTestRateLimiter {

    private static final int DEFAULT_MAX_REQUESTS = 3;
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(60);

    private final Clock clock;
    private final int maxRequests;
    private final Duration window;
    private final ConcurrentMap<UUID, Deque<Instant>> userTimestamps = new ConcurrentHashMap<>();

    public EndpointTestRateLimiter(Clock clock) {
        this.clock = clock;
        this.maxRequests = DEFAULT_MAX_REQUESTS;
        this.window = DEFAULT_WINDOW;
    }

    public void acquire(UUID userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);

        userTimestamps.compute(userId, (key, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayDeque<>();
            }

            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                Instant oldest = timestamps.peekFirst();
                Duration retryAfter = Duration.between(now, oldest.plus(window));
                if (retryAfter.isNegative() || retryAfter.isZero() || retryAfter.toSeconds() == 0) {
                    retryAfter = Duration.ofSeconds(Math.max(1, (long) Math.ceil(retryAfter.toMillis() / 1000.0)));
                }
                throw new RateLimitExceededException(
                        "Rate limit exceeded for endpoint testing. Try again in " + retryAfter.toSeconds() + " seconds.",
                        retryAfter
                );
            }

            timestamps.addLast(now);
            return timestamps;
        });
    }

}
