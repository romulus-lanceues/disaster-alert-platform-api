package dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;


import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("USGS DTO Serialization Tests")
class UsgsDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("deserializes full USGS FeatureCollection GeoJSON payload correctly")
    void deserializesFullFeatureCollectionPayload() throws Exception {
        String json = """
                {
                  "type": "FeatureCollection",
                  "metadata": {
                    "generated": 1712500000000,
                    "url": "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson",
                    "title": "USGS Earthquakes",
                    "status": 200,
                    "api": "1.14.0",
                    "count": 1
                  },
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {
                        "mag": 5.8,
                        "place": "12 km ENE of San Isidro, Philippines",
                        "time": 1712495000000,
                        "updated": 1712499000000,
                        "tz": null,
                        "url": "https://earthquake.usgs.gov/earthquakes/eventpage/us7000test",
                        "detail": "https://earthquake.usgs.gov/earthquakes/feed/v1.0/detail/us7000test.geojson",
                        "felt": 45,
                        "cdi": 4.1,
                        "mmi": 5.2,
                        "alert": "green",
                        "status": "reviewed",
                        "tsunami": 0,
                        "sig": 518,
                        "type": "earthquake",
                        "title": "M 5.8 - 12 km ENE of San Isidro, Philippines"
                      },
                      "geometry": {
                        "type": "Point",
                        "coordinates": [126.152, 9.851, 25.4]
                      },
                      "id": "us7000test"
                    }
                  ]
                }
                """;

        UsgsResponse response = objectMapper.readValue(json, UsgsResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.features()).hasSize(1);

        UsgsFeature feature = response.features().getFirst();
        assertThat(feature.id()).isEqualTo("us7000test");

        UsgsFeatureProperties props = feature.properties();
        assertThat(props).isNotNull();
        assertThat(props.mag()).isEqualTo(5.8);
        assertThat(props.place()).isEqualTo("12 km ENE of San Isidro, Philippines");
        assertThat(props.time()).isEqualTo(1712495000000L);
        assertThat(props.updated()).isEqualTo(1712499000000L);
        assertThat(props.status()).isEqualTo("reviewed");
        assertThat(props.tsunami()).isZero();
        assertThat(props.alert()).isEqualTo("green");
        assertThat(props.url()).isEqualTo("https://earthquake.usgs.gov/earthquakes/eventpage/us7000test");
        assertThat(props.title()).isEqualTo("M 5.8 - 12 km ENE of San Isidro, Philippines");
        assertThat(props.type()).isEqualTo("earthquake");

        UsgsGeometry geometry = feature.geometry();
        assertThat(geometry).isNotNull();
        assertThat(geometry.type()).isEqualTo("Point");
        assertThat(geometry.longitude()).isEqualTo(126.152);
        assertThat(geometry.latitude()).isEqualTo(9.851);
        assertThat(geometry.depthKm()).isEqualTo(25.4);
    }

    @Test
    @DisplayName("ignores unrecognized JSON properties without error")
    void ignoresUnrecognizedProperties() throws Exception {
        String json = """
                {
                  "unknownField": "something",
                  "features": [
                    {
                      "id": "us12345",
                      "randomCustomAttribute": true,
                      "properties": {
                        "mag": 4.2,
                        "type": "earthquake",
                        "extraProp": 999
                      },
                      "geometry": {
                        "type": "Point",
                        "coordinates": [120.0, 14.0, 5.0],
                        "bbox": [-10, -10, 10, 10]
                      }
                    }
                  ]
                }
                """;

        UsgsResponse response = objectMapper.readValue(json, UsgsResponse.class);

        assertThat(response.features()).hasSize(1);
        UsgsFeature feature = response.features().getFirst();
        assertThat(feature.id()).isEqualTo("us12345");
        assertThat(feature.properties().mag()).isEqualTo(4.2);
        assertThat(feature.geometry().longitude()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("can serialize and deserialize UpsertResult")
    void canSerializeAndDeserializeUpsertResult() throws Exception {
        java.util.UUID id = java.util.UUID.randomUUID();
        UpsertResult result = new UpsertResult(id, true);

        String json = objectMapper.writeValueAsString(result);
        UpsertResult deserialized = objectMapper.readValue(json, UpsertResult.class);

        assertThat(deserialized.id()).isEqualTo(id);
        assertThat(deserialized.inserted()).isTrue();
    }
}
