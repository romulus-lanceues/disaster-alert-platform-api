package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@EnableConfigurationProperties(UsgsProperties.class)
class UsgsClient {

    private final RestClient restClient;
    private final UsgsProperties properties;
    private final ObjectMapper objectMapper;

    UsgsClient(ObjectMapper objectMapper, UsgsProperties properties) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    UsgsResponse fetchUpdatedAfter(Instant since) {
        return fetchUpdatedAfter(since, properties.minMagnitude());
    }

    UsgsResponse fetchUpdatedAfter(Instant since, double minMag) {
        String body = restClient.get()
                .uri(u -> {
                    var builder = u.path("/query")
                            .queryParam("format", "geojson")
                            .queryParam("updatedafter", since.toString())
                            .queryParam("minmagnitude", minMag)
                            .queryParam("orderby", "time");
                    return builder.build();
                })
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) {
            return new UsgsResponse(List.of());
        }

        JsonNode root = objectMapper.readTree(body);
        List<UsgsFeature> features = new ArrayList<>();
        JsonNode featuresNode = root.path("features");

        if (featuresNode.isArray()) {
            for (JsonNode node : featuresNode) {
                UsgsFeature feature = objectMapper.treeToValue(node, UsgsFeature.class);
                features.add(new UsgsFeature(
                        feature.id(),
                        feature.properties(),
                        feature.geometry(),
                        node.toString()
                ));
            }
        }

        return new UsgsResponse(features);
    }
}
