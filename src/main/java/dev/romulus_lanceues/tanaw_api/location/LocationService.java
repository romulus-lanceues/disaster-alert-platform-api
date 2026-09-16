package dev.romulus_lanceues.tanaw_api.location;

import dev.romulus_lanceues.tanaw_api.geo.area.GeographicArea;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaNotFoundException;
import dev.romulus_lanceues.tanaw_api.geo.area.GeographicAreaRepository;
import dev.romulus_lanceues.tanaw_api.jts.GeoPointFactory;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserNotFoundException;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final GeographicAreaRepository geographicAreaRepository;
    private final GeoPointFactory geoPointFactory;

    @Transactional
    public Location createLocation(LocationRequest request) {
        log.info("Creating location '{}' for user {}", request.name(), request.userId());

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + request.userId()));

        GeographicArea area = geographicAreaRepository.findByPsgcCode(request.geographicAreaCode())
                .orElseThrow(() -> new GeographicAreaNotFoundException(
                        "Geographic area not found with PSGC code: " + request.geographicAreaCode()));

        Location location = Location.create(
                user,
                request.name(),
                request.address(),
                area,
                request.latitude(),
                request.longitude(),
                geoPointFactory
        );

        return locationRepository.save(location);
    }

    public List<Location> getLocationsByUser(UUID userId) {
        log.info("Fetching locations for user {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        return locationRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Location getLocation(UUID locationId, UUID userId) {
        log.info("Fetching location {} for user {}", locationId, userId);

        return locationRepository.findByIdAndUserId(locationId, userId)
                .orElseThrow(() -> new LocationNotFoundException(
                        "Location not found: " + locationId));
    }
}

