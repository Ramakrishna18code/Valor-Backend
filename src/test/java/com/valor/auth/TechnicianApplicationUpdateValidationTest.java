package com.valor.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnicianApplicationUpdateValidationTest {
    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void validUpdateAndOptionalValuesRemainAccepted() {
        var update = new TechnicianApplicationController.Update(
                "field", "hydraulic", "Otis", "certification", "Diploma", "hands-on",
                "Bengaluru", true, "Travel", "notes", "", "  ");

        assertTrue(validator.validate(update).isEmpty());
    }

    @Test
    void nullValuesRemainOptional() {
        var update = new TechnicianApplicationController.Update(
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertTrue(validator.validate(update).isEmpty());
    }

    @Test
    void eachStringFieldRejectsValuesLongerThanItsSchemaBound() {
        var updates = Stream.of(
                new TechnicianApplicationController.Update("x".repeat(81), null, null, null, null, null, null, null, null, null, null, null),
                new TechnicianApplicationController.Update(null, "x".repeat(161), null, null, null, null, null, null, null, null, null, null),
                new TechnicianApplicationController.Update(null, null, "x".repeat(501), null, null, null, null, null, null, null, null, null),
                new TechnicianApplicationController.Update(null, null, null, "x".repeat(501), null, null, null, null, null, null, null, null),
                new TechnicianApplicationController.Update(null, null, null, null, "x".repeat(161), null, null, null, null, null, null, null),
                new TechnicianApplicationController.Update(null, null, null, null, null, "x".repeat(161), null, null, null, null, null, null),
                new TechnicianApplicationController.Update(null, null, null, null, null, null, "x".repeat(501), null, null, null, null, null),
                new TechnicianApplicationController.Update(null, null, null, null, null, null, null, null, "x".repeat(81), null, null, null),
                new TechnicianApplicationController.Update(null, null, null, null, null, null, null, null, null, "x".repeat(1001), null, null),
                new TechnicianApplicationController.Update(null, null, null, null, null, null, null, null, null, null, "x".repeat(21), null),
                new TechnicianApplicationController.Update(null, null, null, null, null, null, null, null, null, null, null, "x".repeat(41)));

        updates.forEach(update -> assertEquals(1, validator.validate(update).size()));
    }

    @Test
    void validationMessagesDoNotEchoSensitiveValues() {
        String sensitive = "SECRET-AADHAAR-" + "x".repeat(20);
        var update = new TechnicianApplicationController.Update(null, null, null, null, null, null, null, null, null, null, sensitive, null);

        var violations = validator.validate(update);
        assertFalse(violations.isEmpty());
        violations.forEach(violation -> assertFalse(violation.getMessage().contains(sensitive)));
    }

    @Test
    void acceptsCityAndPincodeLocationsAndStructuredIdentityNumbers() {
        var update = new TechnicianApplicationController.Update(
                null, null, null, null, null, null, "Hyderabad, Secunderabad, 500001", null,
                null, null, "1234 5678 9012", "KA 01 2023 0012345");

        assertTrue(validator.validate(update).isEmpty());
    }

    @Test
    void rejectsMalformedIdentityNumbersAndLocations() {
        var updates = Stream.of(
                new TechnicianApplicationController.Update(null, null, null, null, null, null, "Hyderabad, 50001", null, null, null, "12345678901", "KA0120230012345"),
                new TechnicianApplicationController.Update(null, null, null, null, null, null, "Hyderabad", null, null, null, "123456789012", "KARNATAKA-123"),
                new TechnicianApplicationController.Update(null, null, null, null, null, null, "Hyderabad, !@#", null, null, null, null, null));

        updates.forEach(update -> assertFalse(validator.validate(update).isEmpty()));
    }
}
