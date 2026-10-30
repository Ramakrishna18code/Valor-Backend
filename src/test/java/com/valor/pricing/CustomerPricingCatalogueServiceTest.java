package com.valor.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerPricingCatalogueServiceTest {
    @Test
    void publishesOnlyTheB3AAllowlistWithDeterministicSafeFields() {
        var response = new CustomerPricingCatalogueService(new PricingCatalogue(new ObjectMapper())).read();

        assertEquals(27, response.items().size());
        assertEquals(CustomerPricingCatalogueService.PUBLIC_CODES.stream().sorted().toList(), response.items().stream().map(CustomerPricingCatalogueResponse.Item::code).toList());
        assertEquals(24, response.items().stream().filter(item -> "REFERENCE_FIXED".equals(item.displayMode())).count());
        assertEquals(3, response.items().stream().filter(item -> "QUOTE_REQUIRED".equals(item.displayMode())).count());
        assertTrue(response.items().stream().allMatch(item -> !item.code().startsWith("AMC_")));
        assertTrue(response.items().stream().filter(item -> "REFERENCE_FIXED".equals(item.displayMode()))
                .allMatch(item -> item.referenceAmount() != null && item.referenceAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(response.items().stream().filter(item -> "QUOTE_REQUIRED".equals(item.displayMode()))
                .allMatch(item -> item.referenceAmount() == null && item.quoteRequired()));
        assertTrue(response.priceDisclaimer().contains("not guaranteed payable"));
        assertTrue(response.taxDisclosure().contains("Applicable taxes"));
        assertFalse(response.taxDisclosure().toLowerCase().contains("gst-inclusive"));
        assertFalse(response.taxDisclosure().toLowerCase().contains("gst-exclusive"));
    }

    @Test
    void missingAllowlistedEntryFailsClosed() {
        var source = new PricingCatalogue.CatalogueDocument(1, "INR", PricingCatalogue.TAX_REQUIRES_FINANCE_APPROVAL,
                List.of(new PricingCatalogue.Item("STANDARD_BREAKDOWN_CALLOUT", "EMERGENCY_SERVICE", "Callout", "Description",
                        PricingCatalogue.PricingType.FIXED, "PER_JOB", null, null, null, new BigDecimal("2000"), null, null, false, "APPROVED")));
        assertThrows(IllegalStateException.class, () -> new CustomerPricingCatalogueService(new PricingCatalogue(source)).read());
    }
}
