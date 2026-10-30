package com.valor.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PricingCatalogue {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    public static final Set<String> SUPPORTED_CATEGORIES = Set.of("AMC_FLOOR_RATE", "AMC_PACKAGE_RATE", "EMERGENCY_SERVICE", "REPAIR_SPARE_PART", "MODERNIZATION_UPGRADE", "ONE_TIME_SERVICE");
    public static final Set<String> SUPPORTED_BILLING_UNITS = Set.of("PER_JOB", "PER_YEAR");
    public static final Set<String> SUPPORTED_LIFT_TYPES = Set.of("MANUAL", "AUTO");
    public static final String TAX_REQUIRES_FINANCE_APPROVAL = "REQUIRES_BUSINESS_FINANCE_APPROVAL";
    private static final String RESOURCE = "pricing/pricing-catalogue.json";
    private final CatalogueDocument document;
    private final Map<String, Item> itemsByCode;

    @Autowired
    public PricingCatalogue(ObjectMapper mapper) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            CatalogueDocument loaded = mapper.readValue(input, CatalogueDocument.class);
            document = immutableValidated(loaded);
            itemsByCode = document.items().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Item::code, item -> item));
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Pricing catalogue is unavailable or invalid", ex);
        }
    }

    PricingCatalogue(CatalogueDocument source) {
        document = immutableValidated(source);
        itemsByCode = document.items().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Item::code, item -> item));
    }

    private static CatalogueDocument immutableValidated(CatalogueDocument source) {
        validate(source);
        return new CatalogueDocument(source.schemaVersion(), source.currency(), source.taxTreatment(), List.copyOf(source.items()));
    }

    public CatalogueDocument read() { return document; }

    /** Exact, case-sensitive catalogue-code lookup. Duplicate codes are rejected during loading. */
    public Optional<Item> findByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(itemsByCode.get(code));
    }

    public Item requireByCode(String code) {
        return findByCode(code).orElseThrow(() -> new PricingCatalogueException("Pricing catalogue item was not found: " + code));
    }

    public int schemaVersion() { return document.schemaVersion(); }

    public boolean approvedForFinancialUse(Item item) {
        return item != null && itemsByCode.get(item.code()) == item && "APPROVED".equals(item.reviewStatus());
    }

    static boolean categoryMatchesPricingType(String category, PricingType pricingType) {
        return switch (pricingType) {
            case AMC_FLOOR_RATE -> "AMC_FLOOR_RATE".equals(category);
            case AMC_PACKAGE_RATE -> "AMC_PACKAGE_RATE".equals(category);
            case QUOTE_REQUIRED, FIXED, RANGE -> !"AMC_FLOOR_RATE".equals(category) && !"AMC_PACKAGE_RATE".equals(category);
        };
    }

    static void validate(CatalogueDocument value) {
        if (value == null || value.schemaVersion() == null || value.schemaVersion() != SUPPORTED_SCHEMA_VERSION || !"INR".equals(value.currency()) || !TAX_REQUIRES_FINANCE_APPROVAL.equals(value.taxTreatment()) || value.items() == null || value.items().isEmpty())
            throw new IllegalArgumentException("Invalid pricing catalogue header");
        Set<String> codes = new HashSet<>();
        for (Item item : value.items()) {
            if (item == null || item.code() == null || item.code().isBlank() || !codes.add(item.code()) || item.category() == null || !SUPPORTED_CATEGORIES.contains(item.category()) || item.name() == null || item.name().isBlank() || item.pricingType() == null || !categoryMatchesPricingType(item.category(), item.pricingType()) || item.billingUnit() == null || !SUPPORTED_BILLING_UNITS.contains(item.billingUnit()) || item.reviewStatus() == null || item.reviewStatus().isBlank())
                throw new IllegalArgumentException("Invalid pricing catalogue item");
            if (item.pricingType() == PricingType.FIXED && positive(item.fixedAmount()) && item.minAmount() == null && item.maxAmount() == null && item.liftType() == null && item.minFloor() == null && item.maxFloor() == null && !Boolean.TRUE.equals(item.quoteRequired())) { }
            else if (item.pricingType() == PricingType.RANGE && positive(item.minAmount()) && positive(item.maxAmount()) && item.minAmount().compareTo(item.maxAmount()) <= 0 && item.liftType() == null && item.minFloor() == null && item.maxFloor() == null && !Boolean.TRUE.equals(item.quoteRequired())) { }
            else if (item.pricingType() == PricingType.QUOTE_REQUIRED && Boolean.TRUE.equals(item.quoteRequired()) && item.fixedAmount() == null && item.minAmount() == null && item.maxAmount() == null && item.liftType() == null && item.minFloor() == null && item.maxFloor() == null) { }
            else if ((item.pricingType() == PricingType.AMC_FLOOR_RATE || item.pricingType() == PricingType.AMC_PACKAGE_RATE) && positive(item.fixedAmount()) && item.liftType() != null && SUPPORTED_LIFT_TYPES.contains(item.liftType())) {
                if (item.pricingType() == PricingType.AMC_FLOOR_RATE && (item.minFloor() == null || item.maxFloor() == null || item.minFloor() < 1 || item.minFloor() > item.maxFloor())) throw new IllegalArgumentException("Invalid AMC floor range");
                if (item.pricingType() == PricingType.AMC_PACKAGE_RATE && (item.minFloor() != null || item.maxFloor() != null)) throw new IllegalArgumentException("AMC package cannot contain floor-band metadata");
            } else throw new IllegalArgumentException("Invalid pricing values for " + item.code());
        }
    }

    private static boolean positive(BigDecimal n) { return n != null && n.signum() > 0; }

    public record CatalogueDocument(Integer schemaVersion, String currency, String taxTreatment, List<Item> items) {}
    public record Item(String code, String category, String name, String description, PricingType pricingType, String billingUnit, String liftType, Integer minFloor, Integer maxFloor, BigDecimal fixedAmount, BigDecimal minAmount, BigDecimal maxAmount, Boolean quoteRequired, String reviewStatus) {}
    public enum PricingType { FIXED, RANGE, QUOTE_REQUIRED, AMC_FLOOR_RATE, AMC_PACKAGE_RATE }
}

class PricingCatalogueException extends IllegalArgumentException {
    PricingCatalogueException(String message) { super(message); }
}
