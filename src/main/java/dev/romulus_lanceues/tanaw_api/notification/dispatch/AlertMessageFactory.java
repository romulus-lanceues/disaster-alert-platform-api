package dev.romulus_lanceues.tanaw_api.notification.dispatch;

import dev.romulus_lanceues.tanaw_api.notification.channel.AlertMessage;
import dev.romulus_lanceues.tanaw_api.notification.channel.UntrustedContentEscaper;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Factory responsible for transforming a raw {@link NotificationDeliveryContext}
 * into a channel-neutral {@link AlertMessage} with formatted timestamps, distances,
 * magnitude labels, and review status warnings.
 */
@Component
public class AlertMessageFactory {

    private static final ZoneId ASIA_MANILA = ZoneId.of("Asia/Manila");
    private static final ZoneId UTC = ZoneOffset.UTC;

    private static final DateTimeFormatter FORMATTER_MANILA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'PHT'", Locale.US).withZone(ASIA_MANILA);

    private static final DateTimeFormatter FORMATTER_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).withZone(UTC);

    public static final String DEFAULT_FOOTER = "Tanaw Alert • USGS Event Monitoring";
    public static final String AUTOMATIC_REVIEW_NOTICE =
            "Preliminary report: details may be revised upon review by USGS.";

    /**
     * Constructs a channel-neutral {@link AlertMessage} from the supplied delivery context.
     *
     * @param context the delivery context containing event, location, and endpoint attributes
     * @return formatted alert message
     * @throws IllegalArgumentException if context is null
     */
    public AlertMessage create(NotificationDeliveryContext context) {
        if (context == null) {
            throw new IllegalArgumentException("NotificationDeliveryContext must not be null.");
        }

        Double magnitude = context.magnitude();
        String formattedMagnitude = (magnitude != null)
                ? String.format(Locale.US, "M%.1f", magnitude)
                : "Unknown";

        String locationName = (context.locationName() != null && !context.locationName().isBlank())
                ? context.locationName().trim()
                : null;

        String place = (context.place() != null && !context.place().isBlank())
                ? context.place().trim()
                : "Unknown location";

        Double distanceKm = context.distanceKm();
        String formattedDistance = formatDistance(distanceKm, locationName);
        String title = formatTitle(magnitude, formattedMagnitude, distanceKm, locationName);

        Double depthKm = context.depthKm();
        String formattedDepth = (depthKm != null)
                ? String.format(Locale.US, "%.1f km", depthKm)
                : "Unknown";

        String severity = (context.severity() != null && !context.severity().isBlank())
                ? context.severity().trim()
                : "UNKNOWN";

        String formattedTimeManila;
        String formattedTimeUtc;
        if (context.occurredAt() != null) {
            formattedTimeManila = FORMATTER_MANILA.format(context.occurredAt());
            formattedTimeUtc = FORMATTER_UTC.format(context.occurredAt());
        } else {
            formattedTimeManila = "Unknown";
            formattedTimeUtc = "Unknown";
        }

        String eventStatus = (context.eventStatus() != null && !context.eventStatus().isBlank())
                ? context.eventStatus().trim()
                : null;
        String reviewNotice = (eventStatus != null && eventStatus.equalsIgnoreCase("automatic"))
                ? AUTOMATIC_REVIEW_NOTICE
                : null;

        String usgsUrl = (context.usgsUrl() != null && !context.usgsUrl().isBlank())
                ? context.usgsUrl().trim()
                : null;

        return new AlertMessage(
                title,
                place,
                magnitude,
                formattedMagnitude,
                distanceKm,
                formattedDistance,
                locationName,
                depthKm,
                formattedDepth,
                severity,
                context.occurredAt(),
                formattedTimeManila,
                formattedTimeUtc,
                eventStatus,
                reviewNotice,
                usgsUrl,
                DEFAULT_FOOTER
        );
    }

    private String formatTitle(Double magnitude, String formattedMagnitude, Double distanceKm, String locationName) {
        String prefix = (magnitude != null)
                ? formattedMagnitude + " earthquake"
                : "Earthquake";

        String suffix = null;
        if (distanceKm != null) {
            long roundedKm = Math.round(distanceKm);
            suffix = (locationName != null)
                    ? roundedKm + " km from " + locationName
                    : roundedKm + " km away";
        } else if (locationName != null) {
            suffix = locationName;
        }

        if (suffix != null) {
            return prefix + " — " + suffix;
        }
        if (magnitude != null) {
            return prefix;
        }
        return "Earthquake alert";
    }

    private String formatDistance(Double distanceKm, String locationName) {
        if (distanceKm != null) {
            long roundedKm = Math.round(distanceKm);
            return (locationName != null)
                    ? roundedKm + " km from " + locationName
                    : roundedKm + " km";
        }
        if (locationName != null) {
            return "Near " + locationName;
        }
        return "Unknown";
    }
}
