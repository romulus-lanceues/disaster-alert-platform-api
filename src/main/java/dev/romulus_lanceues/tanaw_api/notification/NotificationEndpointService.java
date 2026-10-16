package dev.romulus_lanceues.tanaw_api.notification;

import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationEndpointService {

    private final NotificationEndpointRepository notificationEndpointRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ChannelSupportRegistry channelSupportRegistry;
    private final DiscordDestinationValidator discordDestinationValidator;
    private final DestinationMasker destinationMasker;
    private final EndpointTestRateLimiter endpointTestRateLimiter;
    private final Clock clock;

    public List<NotificationEndpointResponse> listEndpoints(UUID userId) {
        log.info("Fetching notification endpoints for user {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        return notificationEndpointRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(NotificationEndpointResponse::from)
                .toList();
    }

    public NotificationEndpointResponse getEndpoint(UUID userId, UUID endpointId) {
        log.info("Fetching notification endpoint {} for user {}", endpointId, userId);

        return notificationEndpointRepository.findByIdAndUserId(endpointId, userId)
                .map(NotificationEndpointResponse::from)
                .orElseThrow(() -> new NotificationEndpointNotFoundException(
                        "Notification endpoint not found: " + endpointId
                ));
    }

    @Transactional
    public NotificationEndpointResponse upsertEndpoint(UUID userId, NotificationChannel channel, String destination) {
        log.info("Upserting notification endpoint for user {} and channel {}", userId, channel);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        channelSupportRegistry.assertSupported(channel);

        if (channel == NotificationChannel.DISCORD) {
            discordDestinationValidator.validate(destination);
        }

        String destinationHint = destinationMasker.mask(channel, destination);

        NotificationEndpoint endpoint = notificationEndpointRepository.findByUserIdAndChannel(userId, channel)
                .map(existing -> {
                    existing.updateDestination(destination, destinationHint);
                    return existing;
                })
                .orElseGet(() -> NotificationEndpoint.builder()
                        .user(user)
                        .channel(channel)
                        .destination(destination)
                        .destinationHint(destinationHint)
                        .enabled(true)
                        .verifiedAt(null)
                        .build());

        NotificationEndpoint saved = notificationEndpointRepository.save(endpoint);
        log.info("Successfully upserted notification endpoint {} for user {} and channel {}",
                saved.getId(), userId, channel);

        return NotificationEndpointResponse.from(saved);
    }

    @Transactional
    public NotificationEndpointResponse toggleEndpoint(UUID userId, UUID endpointId, boolean enabled) {
        log.info("Toggling notification endpoint {} for user {} to enabled={}", endpointId, userId, enabled);

        NotificationEndpoint endpoint = notificationEndpointRepository.findByIdAndUserId(endpointId, userId)
                .orElseThrow(() -> new NotificationEndpointNotFoundException(
                        "Notification endpoint not found: " + endpointId
                ));

        if (!enabled && endpoint.isEnabled()) {
            endpoint.disable("User disabled endpoint");
            int cancelled = notificationRepository.cancelPendingByEndpointId(endpointId, "Endpoint disabled");
            log.info("Cancelled {} pending notifications for disabled endpoint {}", cancelled, endpointId);
        } else if (enabled && !endpoint.isEnabled()) {
            endpoint.enable();
        }

        NotificationEndpoint saved = notificationEndpointRepository.save(endpoint);
        return NotificationEndpointResponse.from(saved);
    }

    @Transactional
    public void deleteEndpoint(UUID userId, UUID endpointId) {
        log.info("Deleting notification endpoint {} for user {}", endpointId, userId);

        NotificationEndpoint endpoint = notificationEndpointRepository.findByIdAndUserId(endpointId, userId)
                .orElseThrow(() -> new NotificationEndpointNotFoundException(
                        "Notification endpoint not found: " + endpointId
                ));

        int cancelled = notificationRepository.cancelPendingByEndpointId(endpointId, "Endpoint deleted");
        log.info("Cancelled {} pending notifications for deleted endpoint {}", cancelled, endpointId);

        notificationEndpointRepository.delete(endpoint);
        log.info("Deleted notification endpoint {} for user {}", endpointId, userId);
    }

    @Transactional
    public NotificationEndpointResponse testEndpoint(UUID userId, UUID endpointId) {
        log.info("Testing notification endpoint {} for user {}", endpointId, userId);

        NotificationEndpoint endpoint = notificationEndpointRepository.findByIdAndUserId(endpointId, userId)
                .orElseThrow(() -> new NotificationEndpointNotFoundException(
                        "Notification endpoint not found: " + endpointId
                ));

        channelSupportRegistry.assertSupported(endpoint.getChannel());

        endpointTestRateLimiter.acquire(userId);

        ChannelVerifier verifier = channelSupportRegistry.getVerifier(endpoint.getChannel());
        verifier.verify(endpoint.getDestination());

        endpoint.markVerified(clock.instant());
        NotificationEndpoint saved = notificationEndpointRepository.save(endpoint);
        log.info("Successfully verified notification endpoint {} for user {}", endpointId, userId);

        return NotificationEndpointResponse.from(saved);
    }
}
