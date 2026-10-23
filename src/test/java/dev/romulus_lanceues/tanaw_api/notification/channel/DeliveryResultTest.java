package dev.romulus_lanceues.tanaw_api.notification.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DeliveryResult Unit Tests")
class DeliveryResultTest {

    @Nested
    @DisplayName("Sealed hierarchy structure")
    class SealedHierarchyStructure {

        @Test
        @DisplayName("DeliveryResult is a sealed interface permitting Sent, Retryable, and Permanent")
        void sealedHierarchyPermits() {
            assertThat(DeliveryResult.class.isSealed()).isTrue();
            Class<?>[] permittedSubclasses = DeliveryResult.class.getPermittedSubclasses();
            assertThat(permittedSubclasses).containsExactlyInAnyOrder(
                    DeliveryResult.Sent.class,
                    DeliveryResult.Retryable.class,
                    DeliveryResult.Permanent.class
            );
        }

        @Test
        @DisplayName("Exhaustive switch pattern matching without default branch")
        void patternMatchingSwitch() {
            DeliveryResult sent = new DeliveryResult.Sent("prov-1");
            DeliveryResult retryable = new DeliveryResult.Retryable("Rate limited", Duration.ofSeconds(5));
            DeliveryResult permanent = new DeliveryResult.Permanent("Invalid webhook", true);

            assertThat(describeResult(sent)).isEqualTo("SENT:prov-1");
            assertThat(describeResult(retryable)).isEqualTo("RETRYABLE:Rate limited:5s");
            assertThat(describeResult(permanent)).isEqualTo("PERMANENT:Invalid webhook:invalidate");
        }

        private String describeResult(DeliveryResult result) {
            return switch (result) {
                case DeliveryResult.Sent s -> "SENT:" + s.providerMessageId();
                case DeliveryResult.Retryable r -> "RETRYABLE:" + r.reason() + (r.retryAfter() != null ? ":" + r.retryAfter().toSeconds() + "s" : "");
                case DeliveryResult.Permanent p -> "PERMANENT:" + p.reason() + (p.invalidateEndpoint() ? ":invalidate" : ":keep");
            };
        }
    }

    @Nested
    @DisplayName("Sent result")
    class SentResult {

        @Test
        @DisplayName("Sent holds providerMessageId and helper predicates")
        void sentWithProviderId() {
            DeliveryResult.Sent sent = new DeliveryResult.Sent("msg-xyz");

            assertThat(sent.providerMessageId()).isEqualTo("msg-xyz");
            assertThat(sent.isSent()).isTrue();
            assertThat(sent.isRetryable()).isFalse();
            assertThat(sent.isPermanent()).isFalse();
        }

        @Test
        @DisplayName("Sent allows null providerMessageId for 204 fallback or dry-run")
        void sentWithNullProviderId() {
            DeliveryResult.Sent sent = new DeliveryResult.Sent(null);
            assertThat(sent.providerMessageId()).isNull();
            assertThat(sent.isSent()).isTrue();

            DeliveryResult.Sent defaultSent = new DeliveryResult.Sent();
            assertThat(defaultSent.providerMessageId()).isNull();
            assertThat(defaultSent.isSent()).isTrue();
        }
    }

    @Nested
    @DisplayName("Retryable result")
    class RetryableResult {

        @Test
        @DisplayName("Retryable holds reason and optional retryAfter duration")
        void retryableWithDuration() {
            Duration backoff = Duration.ofSeconds(30);
            DeliveryResult.Retryable retryable = new DeliveryResult.Retryable("Service unavailable", backoff);

            assertThat(retryable.reason()).isEqualTo("Service unavailable");
            assertThat(retryable.retryAfter()).isEqualTo(backoff);
            assertThat(retryable.isRetryable()).isTrue();
            assertThat(retryable.isSent()).isFalse();
            assertThat(retryable.isPermanent()).isFalse();
        }

        @Test
        @DisplayName("Retryable single-arg constructor defaults retryAfter to null")
        void retryableSingleArg() {
            DeliveryResult.Retryable retryable = new DeliveryResult.Retryable("Connection timeout");

            assertThat(retryable.reason()).isEqualTo("Connection timeout");
            assertThat(retryable.retryAfter()).isNull();
            assertThat(retryable.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Retryable throws IllegalArgumentException if reason is null or blank")
        void retryableRejectsBlankReason() {
            assertThatThrownBy(() -> new DeliveryResult.Retryable(null, Duration.ofSeconds(5)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must not be null or blank");

            assertThatThrownBy(() -> new DeliveryResult.Retryable("   ", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must not be null or blank");
        }
    }

    @Nested
    @DisplayName("Permanent result")
    class PermanentResult {

        @Test
        @DisplayName("Permanent holds reason and invalidateEndpoint flag")
        void permanentWithInvalidate() {
            DeliveryResult.Permanent permanent = new DeliveryResult.Permanent("Webhook 404 deleted", true);

            assertThat(permanent.reason()).isEqualTo("Webhook 404 deleted");
            assertThat(permanent.invalidateEndpoint()).isTrue();
            assertThat(permanent.isPermanent()).isTrue();
            assertThat(permanent.isSent()).isFalse();
            assertThat(permanent.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("Permanent single-arg constructor defaults invalidateEndpoint to false")
        void permanentSingleArg() {
            DeliveryResult.Permanent permanent = new DeliveryResult.Permanent("Payload malformed");

            assertThat(permanent.reason()).isEqualTo("Payload malformed");
            assertThat(permanent.invalidateEndpoint()).isFalse();
            assertThat(permanent.isPermanent()).isTrue();
        }

        @Test
        @DisplayName("Permanent throws IllegalArgumentException if reason is null or blank")
        void permanentRejectsBlankReason() {
            assertThatThrownBy(() -> new DeliveryResult.Permanent(null, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must not be null or blank");

            assertThatThrownBy(() -> new DeliveryResult.Permanent("   ", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must not be null or blank");
        }
    }
}
