package dev.romulus_lanceues.tanaw_api.ingestion.usgs;


import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
@EnableConfigurationProperties(UsgsProperties.class)
class UsgsClient {

    private final RestClient restClient;
    private final UsgsProperties properties;

    UsgsClient(UsgsProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    UsgsResponse fetchUpdatedAfter(Instant since) {

        return restClient.get()
                .uri(u -> u.path("/query")
                        .queryParam("format", "geojson")
                        .queryParam("updatedafter", since.toString())
                        .queryParam("minmagnitude", properties.minMagnitude())
                        .queryParam("orderby", "time")
                        .build())
                .retrieve()
                .body(UsgsResponse.class);
    }
}
