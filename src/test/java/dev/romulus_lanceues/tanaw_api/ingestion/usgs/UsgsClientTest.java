package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsFeature;
import dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto.UsgsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UsgsClient Tests")
class UsgsClientTest {

    private HttpServer server;
    private UsgsClient usgsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<URI> capturedUri = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("");
    private final AtomicReference<Integer> responseCode = new AtomicReference<>(200);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/query", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedUri.set(exchange.getRequestURI());
                byte[] responseBytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseCode.get(), responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        UsgsProperties properties = new UsgsProperties(
                "http://localhost:" + port,
                4.5,
                Duration.ofMinutes(5),
                new UsgsProperties.BoundingBox(4.0, 21.5, 116.0, 127.0)
        );

        usgsClient = new UsgsClient(objectMapper, properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Nested
    @DisplayName("fetchUpdatedAfter")
    class FetchUpdatedAfterTests {

        @Test
        @DisplayName("successfully fetches and parses USGS earthquake features with default minMagnitude")
        void fetchesAndParsesFeaturesWithDefaultMinMag() {
            Instant since = Instant.parse("2026-10-08T10:00:00Z");
            String sampleGeoJson = """
                    {
                      "type": "FeatureCollection",
                      "features": [
                        {
                          "type": "Feature",
                          "id": "us7000eq01",
                          "properties": {
                            "mag": 5.4,
                            "place": "15 km N of Davao",
                            "time": 1712495000000,
                            "updated": 1712499000000,
                            "status": "reviewed",
                            "alert": "green",
                            "url": "https://earthquake.usgs.gov/earthquakes/eventpage/us7000eq01",
                            "title": "M 5.4 - 15 km N of Davao",
                            "type": "earthquake"
                          },
                          "geometry": {
                            "type": "Point",
                            "coordinates": [125.6, 7.2, 10.0]
                          }
                        }
                      ]
                    }
                    """;
            responseBody.set(sampleGeoJson);

            UsgsResponse response = usgsClient.fetchUpdatedAfter(since);

            URI uri = capturedUri.get();
            assertThat(uri.getPath()).isEqualTo("/query");
            assertThat(uri.getQuery())
                    .contains("format=geojson")
                    .contains("updatedafter=2026-10-08T10:00:00Z")
                    .contains("minmagnitude=4.5")
                    .contains("orderby=time");

            assertThat(response).isNotNull();
            assertThat(response.features()).hasSize(1);

            UsgsFeature feature = response.features().getFirst();
            assertThat(feature.id()).isEqualTo("us7000eq01");
            assertThat(feature.properties().mag()).isEqualTo(5.4);
            assertThat(feature.properties().place()).isEqualTo("15 km N of Davao");
            assertThat(feature.geometry().longitude()).isEqualTo(125.6);
            assertThat(feature.geometry().latitude()).isEqualTo(7.2);
            assertThat(feature.geometry().depthKm()).isEqualTo(10.0);
            assertThat(feature.rawPayload()).contains("\"us7000eq01\"");
        }

        @Test
        @DisplayName("uses explicitly provided minMagnitude parameter")
        void usesExplicitMinMagnitude() {
            Instant since = Instant.parse("2026-10-08T11:00:00Z");
            responseBody.set("{\"type\":\"FeatureCollection\",\"features\":[]}");

            usgsClient.fetchUpdatedAfter(since, 6.0);

            URI uri = capturedUri.get();
            assertThat(uri.getQuery()).contains("minmagnitude=6.0");
        }

        @Test
        @DisplayName("returns empty response when response body is empty or whitespace")
        void returnsEmptyWhenResponseBodyIsBlank() {
            Instant since = Instant.parse("2026-10-08T10:00:00Z");
            responseBody.set("   ");

            UsgsResponse response = usgsClient.fetchUpdatedAfter(since);

            assertThat(response).isNotNull();
            assertThat(response.features()).isEmpty();
        }

        @Test
        @DisplayName("returns empty response when features array is empty")
        void returnsEmptyWhenFeaturesArrayIsEmpty() {
            Instant since = Instant.parse("2026-10-08T10:00:00Z");
            responseBody.set("{\"type\":\"FeatureCollection\",\"features\":[]}");

            UsgsResponse response = usgsClient.fetchUpdatedAfter(since);

            assertThat(response).isNotNull();
            assertThat(response.features()).isEmpty();
        }

        @Test
        @DisplayName("returns empty response when features node is not an array")
        void returnsEmptyWhenFeaturesNodeNotArray() {
            Instant since = Instant.parse("2026-10-08T10:00:00Z");
            responseBody.set("{\"type\":\"FeatureCollection\",\"features\":null}");

            UsgsResponse response = usgsClient.fetchUpdatedAfter(since);

            assertThat(response).isNotNull();
            assertThat(response.features()).isEmpty();
        }

        @Test
        @DisplayName("throws exception when upstream server returns 500 internal server error")
        void throwsExceptionOnHttpServerError() {
            Instant since = Instant.parse("2026-10-08T10:00:00Z");
            responseCode.set(500);
            responseBody.set("Internal Server Error");

            assertThatThrownBy(() -> usgsClient.fetchUpdatedAfter(since))
                    .isInstanceOf(RestClientResponseException.class);
        }
    }
}
