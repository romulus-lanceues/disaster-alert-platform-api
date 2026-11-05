package dev.romulus_lanceues.tanaw_api.ops.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulateEarthquakeRequest Validation Tests")
class SimulateEarthquakeRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("valid request passes validation")
    void validRequestPasses() {
        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(
                UUID.randomUUID(),
                5.5,
                10.0
        );

        Set<ConstraintViolation<SimulateEarthquakeRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("null optional fields pass validation")
    void nullOptionalFieldsPass() {
        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(
                UUID.randomUUID(),
                null,
                null
        );

        Set<ConstraintViolation<SimulateEarthquakeRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("missing alertRuleId fails validation")
    void missingAlertRuleIdFails() {
        SimulateEarthquakeRequest request = new SimulateEarthquakeRequest(
                null,
                5.5,
                10.0
        );

        Set<ConstraintViolation<SimulateEarthquakeRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("alertRuleId");
    }

    @Test
    @DisplayName("magnitude out of bounds fails validation")
    void magnitudeOutOfBoundsFails() {
        SimulateEarthquakeRequest negative = new SimulateEarthquakeRequest(
                UUID.randomUUID(),
                -0.1,
                10.0
        );
        Set<ConstraintViolation<SimulateEarthquakeRequest>> violations = validator.validate(negative);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("magnitude");

        SimulateEarthquakeRequest tooHigh = new SimulateEarthquakeRequest(
                UUID.randomUUID(),
                10.1,
                10.0
        );
        violations = validator.validate(tooHigh);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("magnitude");
    }

    @Test
    @DisplayName("offsetKm out of bounds fails validation")
    void offsetKmOutOfBoundsFails() {
        SimulateEarthquakeRequest negative = new SimulateEarthquakeRequest(
                UUID.randomUUID(),
                5.0,
                -1.0
        );
        Set<ConstraintViolation<SimulateEarthquakeRequest>> violations = validator.validate(negative);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("offsetKm");

        SimulateEarthquakeRequest tooHigh = new SimulateEarthquakeRequest(
                UUID.randomUUID(),
                5.0,
                1000.1
        );
        violations = validator.validate(tooHigh);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("offsetKm");
    }
}
