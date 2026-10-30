package com.valor.pricing;

import java.math.BigDecimal;
import java.util.List;

public record CustomerPricingCatalogueResponse(
        int catalogueVersion,
        String currency,
        String priceDisclaimer,
        String taxDisclosure,
        List<Item> items) {
    public record Item(
            String code,
            String category,
            String name,
            String description,
            String displayMode,
            BigDecimal referenceAmount,
            boolean quoteRequired,
            String billingUnit) {}
}
