package dev.romulus_lanceues.tanaw_api.ingestion.usgs.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UsgsGeometry DTO Tests")
class UsgsGeometryTest {

    @Nested
    @DisplayName("longitude()")
    class LongitudeTests {

        @Test
        @DisplayName("returns first coordinate when coordinates list is present")
        void returnsFirstCoordinateWhenPresent() {
            var geometry = new UsgsGeometry("Point", List.of(121.0, 14.5, 10.0));

            assertThat(geometry.longitude()).isEqualTo(121.0);
        }

        @Test
        @DisplayName("returns null when coordinates list is empty")
        void returnsNullWhenEmpty() {
            var geometry = new UsgsGeometry("Point", List.of());

            assertThat(geometry.longitude()).isNull();
        }

        @Test
        @DisplayName("returns null when coordinates list is null")
        void returnsNullWhenCoordinatesNull() {
            var geometry = new UsgsGeometry("Point", null);

            assertThat(geometry.longitude()).isNull();
        }
    }

    @Nested
    @DisplayName("latitude()")
    class LatitudeTests {

        @Test
        @DisplayName("returns second coordinate when coordinates list has at least two elements")
        void returnsSecondCoordinateWhenPresent() {
            var geometry = new UsgsGeometry("Point", List.of(121.0, 14.5, 10.0));

            assertThat(geometry.latitude()).isEqualTo(14.5);
        }

        @Test
        @DisplayName("returns null when coordinates list has only one element")
        void returnsNullWhenOnlyOneElement() {
            var geometry = new UsgsGeometry("Point", List.of(121.0));

            assertThat(geometry.latitude()).isNull();
        }

        @Test
        @DisplayName("returns null when coordinates list is empty")
        void returnsNullWhenEmpty() {
            var geometry = new UsgsGeometry("Point", List.of());

            assertThat(geometry.latitude()).isNull();
        }

        @Test
        @DisplayName("returns null when coordinates list is null")
        void returnsNullWhenCoordinatesNull() {
            var geometry = new UsgsGeometry("Point", null);

            assertThat(geometry.latitude()).isNull();
        }
    }

    @Nested
    @DisplayName("depthKm()")
    class DepthKmTests {

        @Test
        @DisplayName("returns third coordinate when coordinates list has at least three elements")
        void returnsThirdCoordinateWhenPresent() {
            var geometry = new UsgsGeometry("Point", List.of(121.0, 14.5, 12.34));

            assertThat(geometry.depthKm()).isEqualTo(12.34);
        }

        @Test
        @DisplayName("returns null when coordinates list has fewer than three elements")
        void returnsNullWhenFewerThanThreeElements() {
            var geometry = new UsgsGeometry("Point", List.of(121.0, 14.5));

            assertThat(geometry.depthKm()).isNull();
        }

        @Test
        @DisplayName("returns null when coordinates list is empty")
        void returnsNullWhenEmpty() {
            var geometry = new UsgsGeometry("Point", List.of());

            assertThat(geometry.depthKm()).isNull();
        }

        @Test
        @DisplayName("returns null when coordinates list is null")
        void returnsNullWhenCoordinatesNull() {
            var geometry = new UsgsGeometry("Point", null);

            assertThat(geometry.depthKm()).isNull();
        }
    }
}
