package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.channel.AlertMessage;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AlertMessageFactory Tests")
class AlertMessageFactoryTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-17T10:00:00Z");

    private AlertMessageFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AlertMessageFactory();
    }

    @Nested
    @DisplayName("Complete Delivery Context Formatting")
    class CompleteDeliveryContext {

        @Test
        @DisplayName("Constructs expected AlertMessage with Manila time, UTC time, rounded distance, and automatic review notice")
        void constructsCompleteMessage() {
            Instant occurredAt = Instant.parse("2026-10-17T09:43:00Z"); // 17:43:00 in Asia/Manila (UTC+8)

            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    NotificationChannel.DISCORD,
                    "https://discord.com/api/webhooks/123/token",
                    "Discord webhook ...token",
                    UUID.randomUUID(),
                    true,
                    FIXED_NOW,
                    UUID.randomUUID(),
                    UserStatus.ACTIVE,
                    "Home",
                    "24 km SSW of Balangiga, Philippines",
                    occurredAt,
                    "automatic",
                    "MODERATE",
                    "https://earthquake.usgs.gov/earthquakes/eventpage/us7000test",
                    5.4,
                    10.0,
                    23.4
            );

            AlertMessage message = factory.create(context);

            assertThat(message).isNotNull();
            assertThat(message.title()).isEqualTo("M5.4 earthquake — 23 km from Home");
            assertThat(message.place()).isEqualTo("24 km SSW of Balangiga, Philippines");
            assertThat(message.magnitude()).isEqualTo(5.4);
            assertThat(message.formattedMagnitude()).isEqualTo("M5.4");
            assertThat(message.distanceKm()).isEqualTo(23.4);
            assertThat(message.formattedDistance()).isEqualTo("23 km from Home");
            assertThat(message.locationName()).isEqualTo("Home");
            assertThat(message.depthKm()).isEqualTo(10.0);
            assertThat(message.formattedDepth()).isEqualTo("10.0 km");
            assertThat(message.severity()).isEqualTo("MODERATE");
            assertThat(message.occurredAt()).isEqualTo(occurredAt);
            assertThat(message.formattedTimeManila()).isEqualTo("2026-10-17 17:43:00 PHT");
            assertThat(message.formattedTimeUtc()).isEqualTo("2026-10-17 09:43:00 UTC");
            assertThat(message.eventStatus()).isEqualTo("automatic");
            assertThat(message.reviewNotice())
                    .isEqualTo("Preliminary report: details may be revised upon review by USGS.");
            assertThat(message.usgsUrl()).isEqualTo("https://earthquake.usgs.gov/earthquakes/eventpage/us7000test");
            assertThat(message.footer()).isEqualTo("Tanaw Alert • USGS Event Monitoring");
        }

        @Test
        @DisplayName("Omits review notice when USGS status is reviewed")
        void omitsReviewNoticeWhenReviewed() {
            NotificationDeliveryContext context = createContextWithStatus("reviewed");

            AlertMessage message = factory.create(context);

            assertThat(message.eventStatus()).isEqualTo("reviewed");
            assertThat(message.reviewNotice()).isNull();
        }

        @Test
        @DisplayName("Handles case-insensitivity of automatic status")
        void handlesCaseInsensitiveStatus() {
            NotificationDeliveryContext context = createContextWithStatus("AUTOMATIC");

            AlertMessage message = factory.create(context);

            assertThat(message.eventStatus()).isEqualTo("AUTOMATIC");
            assertThat(message.reviewNotice())
                    .isEqualTo("Preliminary report: details may be revised upon review by USGS.");
        }

        @Test
        @DisplayName("Trims whitespace on automatic status correctly")
        void handlesWhitespaceInStatus() {
            NotificationDeliveryContext context = createContextWithStatus("  automatic  ");

            AlertMessage message = factory.create(context);

            assertThat(message.eventStatus()).isEqualTo("automatic");
            assertThat(message.reviewNotice())
                    .isEqualTo("Preliminary report: details may be revised upon review by USGS.");
        }

        @Test
        @DisplayName("Treats blank eventStatus and usgsUrl as null")
        void treatsBlankStatusAndUrlAsNull() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.DISCORD,
                    "dest", "hint", UUID.randomUUID(), true, FIXED_NOW,
                    UUID.randomUUID(), UserStatus.ACTIVE, "Home",
                    "Manila", Instant.parse("2026-10-17T08:00:00Z"),
                    "   ", "LOW", "   ",
                    4.2, 5.0, 10.0
            );

            AlertMessage message = factory.create(context);

            assertThat(message.eventStatus()).isNull();
            assertThat(message.reviewNotice()).isNull();
            assertThat(message.usgsUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Missing Fields")
    class EdgeCasesAndMissingFields {

        @Test
        @DisplayName("Formats title when distanceKm is null")
        void formatsTitleWhenDistanceIsNull() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.DISCORD,
                    "dest", "hint", UUID.randomUUID(), true, FIXED_NOW,
                    UUID.randomUUID(), UserStatus.ACTIVE, "Work Office",
                    "Mindanao, Philippines", Instant.parse("2026-10-17T09:00:00Z"),
                    "reviewed", "HIGH", "https://usgs.gov",
                    6.1, 15.0, null
            );

            AlertMessage message = factory.create(context);

            assertThat(message.title()).isEqualTo("M6.1 earthquake — Work Office");
            assertThat(message.formattedDistance()).isEqualTo("Near Work Office");
            assertThat(message.distanceKm()).isNull();
        }

        @Test
        @DisplayName("Formats title when locationName is blank")
        void formatsTitleWhenLocationIsBlank() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.DISCORD,
                    "dest", "hint", UUID.randomUUID(), true, FIXED_NOW,
                    UUID.randomUUID(), UserStatus.ACTIVE, "  ",
                    "Mindanao, Philippines", Instant.parse("2026-10-17T09:00:00Z"),
                    "reviewed", "HIGH", "https://usgs.gov",
                    6.1, 15.0, 45.8
            );

            AlertMessage message = factory.create(context);

            assertThat(message.title()).isEqualTo("M6.1 earthquake — 46 km away");
            assertThat(message.formattedDistance()).isEqualTo("46 km");
        }

        @Test
        @DisplayName("Formats title when magnitude is null")
        void formatsTitleWhenMagnitudeIsNull() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.DISCORD,
                    "dest", "hint", UUID.randomUUID(), true, FIXED_NOW,
                    UUID.randomUUID(), UserStatus.ACTIVE, "Home",
                    "Luzon, Philippines", Instant.parse("2026-10-17T09:00:00Z"),
                    "reviewed", "MODERATE", "https://usgs.gov",
                    null, 20.0, 12.3
            );

            AlertMessage message = factory.create(context);

            assertThat(message.title()).isEqualTo("Earthquake — 12 km from Home");
            assertThat(message.formattedMagnitude()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Handles minimal context with all nullable attributes null")
        void handlesMinimalContextGracefully() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.DISCORD,
                    null, null, null, null, null,
                    UUID.randomUUID(), UserStatus.ACTIVE, null,
                    null, null,
                    null, null, null,
                    null, null, null
            );

            AlertMessage message = factory.create(context);

            assertThat(message.title()).isEqualTo("Earthquake alert");
            assertThat(message.place()).isEqualTo("Unknown location");
            assertThat(message.formattedMagnitude()).isEqualTo("Unknown");
            assertThat(message.formattedDistance()).isEqualTo("Unknown");
            assertThat(message.formattedDepth()).isEqualTo("Unknown");
            assertThat(message.formattedTimeManila()).isEqualTo("Unknown");
            assertThat(message.formattedTimeUtc()).isEqualTo("Unknown");
            assertThat(message.reviewNotice()).isNull();
            assertThat(message.severity()).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("Throws IllegalArgumentException if context is null")
        void throwsOnNullContext() {
            assertThatThrownBy(() -> factory.create(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("NotificationDeliveryContext must not be null");
        }

        @Test
        @DisplayName("Prefixes title with [TEST] when place starts with [TEST]")
        void prefixesTitleWhenPlaceStartsTest() {
            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.DISCORD,
                    "https://discord.com/webhook", "hint",
                    UUID.randomUUID(), true, FIXED_NOW,
                    UUID.randomUUID(), UserStatus.ACTIVE,
                    "Home", "[TEST] 10 km SW of Manila",
                    Instant.parse("2026-10-17T08:00:00Z"), "reviewed", "MODERATE",
                    "https://usgs.gov", 5.2, 10.0, 15.0
            );

            AlertMessage message = factory.create(context);
            assertThat(message.title()).isEqualTo("[TEST] M5.2 earthquake — 15 km from Home");
        }

        @Test
        @DisplayName("Prefixes title with [TEST] when eventStatus is test or severity is test")
        void prefixesTitleWhenStatusOrSeverityIsTest() {
            NotificationDeliveryContext contextStatus = new NotificationDeliveryContext(
                    UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.DISCORD,
                    "https://discord.com/webhook", "hint",
                    UUID.randomUUID(), true, FIXED_NOW,
                    UUID.randomUUID(), UserStatus.ACTIVE,
                    "Home", "10 km SW of Manila",
                    Instant.parse("2026-10-17T08:00:00Z"), "test", "MODERATE",
                    "https://usgs.gov", 5.2, 10.0, 15.0
            );

            AlertMessage messageStatus = factory.create(contextStatus);
            assertThat(messageStatus.title()).isEqualTo("[TEST] M5.2 earthquake — 15 km from Home");

            NotificationDeliveryContext contextSeverity = new NotificationDeliveryContext(
                    UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.DISCORD,
                    "https://discord.com/webhook", "hint",
                    UUID.randomUUID(), true, FIXED_NOW,
                    UUID.randomUUID(), UserStatus.ACTIVE,
                    "Home", "10 km SW of Manila",
                    Instant.parse("2026-10-17T08:00:00Z"), "reviewed", "TEST",
                    "https://usgs.gov", 5.2, 10.0, 15.0
            );

            AlertMessage messageSeverity = factory.create(contextSeverity);
            assertThat(messageSeverity.title()).isEqualTo("[TEST] M5.2 earthquake — 15 km from Home");
        }
    }

    private NotificationDeliveryContext createContextWithStatus(String eventStatus) {
        return new NotificationDeliveryContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationChannel.DISCORD,
                "https://discord.com/webhook",
                "hint",
                UUID.randomUUID(),
                true,
                FIXED_NOW,
                UUID.randomUUID(),
                UserStatus.ACTIVE,
                "Home",
                "Manila",
                Instant.parse("2026-10-17T08:00:00Z"),
                eventStatus,
                "LOW",
                "https://usgs.gov",
                4.2,
                5.0,
                10.0
        );
    }
}
