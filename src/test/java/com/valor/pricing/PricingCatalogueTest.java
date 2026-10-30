package com.valor.pricing;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingCatalogueTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String TAX = PricingCatalogue.TAX_REQUIRES_FINANCE_APPROVAL;

    @Test void loadsApprovedCatalogueFromClasspath() {
        PricingCatalogue c = new PricingCatalogue(mapper);
        assertEquals(1, c.schemaVersion()); assertEquals("INR", c.read().currency());
        assertTrue(c.read().items().stream().anyMatch(i -> i.pricingType() == PricingCatalogue.PricingType.QUOTE_REQUIRED));
        assertThrows(UnsupportedOperationException.class, () -> c.read().items().clear());
        assertTrue(c.approvedForFinancialUse(c.requireByCode("GENERAL_LIFT_INSPECTION")));
    }

    @Test void approvedCatalogueKeepsQuoteOnlyItemsWithoutAmounts() {
        PricingCatalogue c = new PricingCatalogue(mapper);
        assertTrue(c.read().items().stream().allMatch(i -> "APPROVED".equals(i.reviewStatus())));
        assertTrue(c.read().items().stream().filter(i -> i.pricingType() == PricingCatalogue.PricingType.QUOTE_REQUIRED)
                .allMatch(i -> i.fixedAmount() == null && i.minAmount() == null && i.maxAmount() == null && Boolean.TRUE.equals(i.quoteRequired())));
        assertTrue(c.read().items().stream().filter(i -> i.pricingType() == PricingCatalogue.PricingType.FIXED)
                .allMatch(i -> i.fixedAmount() != null && i.fixedAmount().signum() > 0));
    }

    @Test void rejectsDuplicateCodesInvalidRangesAndTaxTreatment() {
        PricingCatalogue.Item fixed = item("DUP", "ONE_TIME_SERVICE", PricingCatalogue.PricingType.FIXED, "PER_JOB", null, null, null, new BigDecimal("1"), null, null, false, "REVIEW");
        PricingCatalogue.Item duplicate = item("DUP", "ONE_TIME_SERVICE", PricingCatalogue.PricingType.RANGE, "PER_JOB", null, null, null, null, new BigDecimal("5"), new BigDecimal("4"), false, "REVIEW");
        assertThrows(IllegalArgumentException.class, () -> PricingCatalogue.validate(new PricingCatalogue.CatalogueDocument(1, "INR", TAX, List.of(fixed, duplicate))));
        assertThrows(IllegalArgumentException.class, () -> PricingCatalogue.validate(new PricingCatalogue.CatalogueDocument(1, "INR", "", List.of(fixed))));
        assertThrows(IllegalArgumentException.class, () -> PricingCatalogue.validate(new PricingCatalogue.CatalogueDocument(1, "INR", "UNKNOWN", List.of(fixed))));
    }

    @Test void exactLookupAndApprovalAreCatalogueBacked() {
        PricingCatalogue c = new PricingCatalogue(mapper);
        assertTrue(c.findByCode("GENERAL_LIFT_INSPECTION").isPresent()); assertTrue(c.findByCode("general_lift_inspection").isEmpty());
        assertThrows(PricingCatalogueException.class, () -> c.requireByCode("MISSING"));
        PricingCatalogue.Item external = new PricingCatalogue.Item("GENERAL_LIFT_INSPECTION", "ONE_TIME_SERVICE", "x", "", PricingCatalogue.PricingType.FIXED, "PER_JOB", null, null, null, BigDecimal.ONE, null, null, false, "APPROVED");
        assertFalse(c.approvedForFinancialUse(external));
        PricingSnapshot snapshot = PricingSnapshot.from(c, "GENERAL_LIFT_INSPECTION", null, null, null, BigDecimal.ZERO, null);
        assertEquals("GENERAL_LIFT_INSPECTION", snapshot.catalogueCode());
    }

    @Test void factoryCreatesOnlyApprovedCatalogueSnapshots() {
        PricingCatalogue.Item approved = item("APPROVED_FIXED", "ONE_TIME_SERVICE", PricingCatalogue.PricingType.FIXED, "PER_JOB", null, null, null, new BigDecimal("999.00"), null, null, false, "APPROVED");
        PricingCatalogue c = new PricingCatalogue(new PricingCatalogue.CatalogueDocument(1, "INR", TAX, List.of(approved)));
        PricingSnapshot s = PricingSnapshot.from(c, "APPROVED_FIXED", null, null, null, BigDecimal.ZERO, null);
        assertEquals(new BigDecimal("999.00"), s.selectedFixedAmount()); assertEquals("APPROVED_FIXED", s.catalogueCode());
        assertThrows(IllegalArgumentException.class, () -> PricingSnapshot.from(c, "MISSING", null, null, null, null, null));
    }

    @Test void rejectsInvalidRangeQuoteAndTaxCombinations() {
        PricingCatalogue.Item range = item("RANGE", "EMERGENCY_SERVICE", PricingCatalogue.PricingType.RANGE, "PER_JOB", null, null, null, null, new BigDecimal("10"), new BigDecimal("20"), false, "APPROVED");
        PricingCatalogue c = new PricingCatalogue(new PricingCatalogue.CatalogueDocument(1, "INR", TAX, List.of(range)));
        assertThrows(IllegalArgumentException.class, () -> PricingSnapshot.from(c, "RANGE", new BigDecimal("9"), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> PricingSnapshot.from(c, new String("RANGE"), new BigDecimal("10"), null, new BigDecimal("0.18"), BigDecimal.ZERO, null));
        PricingCatalogue.Item quote = item("QUOTE", "EMERGENCY_SERVICE", PricingCatalogue.PricingType.QUOTE_REQUIRED, "PER_JOB", null, null, null, null, null, null, true, "APPROVED");
        PricingCatalogue qc = new PricingCatalogue(new PricingCatalogue.CatalogueDocument(1, "INR", TAX, List.of(quote)));
        assertThrows(IllegalArgumentException.class, () -> PricingSnapshot.from(qc, "QUOTE", null, null, null, null, null));
    }

    @Test void validatesAmcBandMetadataAndCategoryCompatibility() {
        PricingCatalogue.Item floor = item("FLOOR", "AMC_FLOOR_RATE", PricingCatalogue.PricingType.AMC_FLOOR_RATE, "PER_YEAR", "MANUAL", 3, 4, new BigDecimal("15000"), null, null, false, "APPROVED");
        PricingCatalogue c = new PricingCatalogue(new PricingCatalogue.CatalogueDocument(1, "INR", TAX, List.of(floor)));
        PricingSnapshot s = PricingSnapshot.from(c, "FLOOR", null, null, null, BigDecimal.ZERO, null);
        assertEquals(3, s.floorBandMinimum());
        PricingCatalogue.Item bad = item("BAD", "ONE_TIME_SERVICE", PricingCatalogue.PricingType.AMC_FLOOR_RATE, "PER_YEAR", "MANUAL", 1, 2, new BigDecimal("1"), null, null, false, "APPROVED");
        assertThrows(IllegalArgumentException.class, () -> new PricingCatalogue(new PricingCatalogue.CatalogueDocument(1, "INR", TAX, List.of(bad))));
        PricingCatalogue.Item noBoundary = item("BAD_FLOOR", "AMC_FLOOR_RATE", PricingCatalogue.PricingType.AMC_FLOOR_RATE, "PER_YEAR", "MANUAL", 0, 2, new BigDecimal("1"), null, null, false, "APPROVED");
        assertThrows(IllegalArgumentException.class, () -> new PricingCatalogue(new PricingCatalogue.CatalogueDocument(1, "INR", TAX, List.of(noBoundary))));
    }

    private static PricingCatalogue.Item item(String code, String category, PricingCatalogue.PricingType type, String unit, String lift, Integer minFloor, Integer maxFloor, BigDecimal fixed, BigDecimal min, BigDecimal max, boolean quote, String review) {
        return new PricingCatalogue.Item(code, category, code, "", type, unit, lift, minFloor, maxFloor, fixed, min, max, quote, review);
    }
}
