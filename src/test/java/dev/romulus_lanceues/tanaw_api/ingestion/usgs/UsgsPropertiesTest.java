package dev.romulus_lanceues.tanaw_api.ingestion.usgs;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UsgsProperties Validation Tests")
class UsgsPropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private UsgsProperties.BoundingBox validBoundingBox() {
        return new UsgsProperties.BoundingBox(4.0, 21.5, 116.0, 127.0);
    }

    private UsgsProperties validProperties() {
        return new UsgsProperties(
                "https://earthquake.usgs.gov/fdsnws/event/1",
                4.5,
                Duration.ofMinutes(5),
                Duration.ofSeconds(5),
                Duration.ofSeconds(20),
                validBoundingBox()
        );
    }

    @Test
    @DisplayName("valid properties pass validation with no violations")
    void validPropertiesPassValidation() {
        UsgsProperties properties = validProperties();
        Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Nested
    @DisplayName("baseUrl validation")
    class BaseUrlValidation {

        @Test
        @DisplayName("null baseUrl violates @NotBlank")
        void nullBaseUrlFails() {
            UsgsProperties properties = new UsgsProperties(
                    null, 4.5, Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(20), validBoundingBox()
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("baseUrl");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("blank baseUrl violates @NotBlank")
        void blankBaseUrlFails(String blankUrl) {
            UsgsProperties properties = new UsgsProperties(
                    blankUrl, 4.5, Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(20), validBoundingBox()
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("baseUrl");
        }
    }

    @Nested
    @DisplayName("minMagnitude validation")
    class MinMagnitudeValidation {

        @Test
        @DisplayName("null minMagnitude violates @NotNull")
        void nullMinMagnitudeFails() {
            UsgsProperties properties = new UsgsProperties(
                    "https://earthquake.usgs.gov", null, Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(20), validBoundingBox()
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("minMagnitude");
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, -1.0, -0.01})
        @DisplayName("zero or negative minMagnitude violates @Positive")
        void nonPositiveMinMagnitudeFails(double invalidMag) {
            UsgsProperties properties = new UsgsProperties(
                    "https://earthquake.usgs.gov", invalidMag, Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(20), validBoundingBox()
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("minMagnitude");
        }
    }

    @Nested
    @DisplayName("pollInterval validation")
    class PollIntervalValidation {

        @Test
        @DisplayName("null pollInterval violates @NotNull")
        void nullPollIntervalFails() {
            UsgsProperties properties = new UsgsProperties(
                    "https://earthquake.usgs.gov", 4.5, null, Duration.ofSeconds(5), Duration.ofSeconds(20), validBoundingBox()
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("pollInterval");
        }
    }

    @Nested
    @DisplayName("connectTimeout validation")
    class ConnectTimeoutValidation {

        @Test
        @DisplayName("null connectTimeout violates @NotNull")
        void nullConnectTimeoutFails() {
            UsgsProperties properties = new UsgsProperties(
                    "https://earthquake.usgs.gov", 4.5, Duration.ofMinutes(5), null, Duration.ofSeconds(20), validBoundingBox()
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("connectTimeout");
        }
    }

    @Nested
    @DisplayName("readTimeout validation")
    class ReadTimeoutValidation {

        @Test
        @DisplayName("null readTimeout violates @NotNull")
        void nullReadTimeoutFails() {
            UsgsProperties properties = new UsgsProperties(
                    "https://earthquake.usgs.gov", 4.5, Duration.ofMinutes(5), Duration.ofSeconds(5), null, validBoundingBox()
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("readTimeout");
        }
    }

    @Nested
    @DisplayName("boundingBox validation")
    class BoundingBoxValidation {

        @Test
        @DisplayName("null boundingBox violates @NotNull")
        void nullBoundingBoxFails() {
            UsgsProperties properties = new UsgsProperties(
                    "https://earthquake.usgs.gov", 4.5, Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(20), null
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("boundingBox");
        }

        @Test
        @DisplayName("null field within boundingBox violates @NotNull on BoundingBox")
        void nullFieldInBoundingBoxFails() {
            UsgsProperties.BoundingBox invalidBox = new UsgsProperties.BoundingBox(
                    null, 21.5, 116.0, 127.0
            );
            UsgsProperties properties = new UsgsProperties(
                    "https://earthquake.usgs.gov", 4.5, Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(20), invalidBox
            );

            Set<ConstraintViolation<UsgsProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("boundingBox.minLatitude");
        }
    }
}
