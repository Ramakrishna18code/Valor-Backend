package com.valor.pricing;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable, catalogue-backed snapshot for a future catalogue-priced transaction. */
public final class PricingSnapshot {
    private final String catalogueCode;
    private final int catalogueSchemaVersion;
    private final String itemDisplayName;
    private final String category;
    private final PricingCatalogue.PricingType pricingType;
    private final String billingUnit;
    private final String currency;
    private final BigDecimal selectedFixedAmount;
    private final BigDecimal rangeMinimum;
    private final BigDecimal rangeMaximum;
    private final String liftType;
    private final Integer floorBandMinimum;
    private final String reviewStatus;
    private final String taxTreatment;
    private final String taxBasis;
    private final BigDecimal taxRate;
    private final BigDecimal taxAmount;
    private final BigDecimal finalApprovedQuoteAmount;
    private final String quoteReasonOrApprovalReference;

    private PricingSnapshot(PricingCatalogue.Item item, int schemaVersion, String taxTreatment,
                            BigDecimal finalAmount, String taxBasis, BigDecimal taxRate,
                            BigDecimal taxAmount, String quoteReference) {
        this.catalogueCode = required(item.code(), "catalogueCode"); this.catalogueSchemaVersion = schemaVersion;
        this.itemDisplayName = required(item.name(), "itemDisplayName"); this.category = required(item.category(), "category");
        this.pricingType = Objects.requireNonNull(item.pricingType(), "pricingType"); this.billingUnit = required(item.billingUnit(), "billingUnit");
        this.currency = "INR"; this.selectedFixedAmount = item.fixedAmount(); this.rangeMinimum = item.minAmount(); this.rangeMaximum = item.maxAmount();
        this.liftType = item.liftType(); this.floorBandMinimum = item.minFloor(); this.reviewStatus = required(item.reviewStatus(), "reviewStatus");
        this.taxTreatment = required(taxTreatment, "taxTreatment"); this.taxBasis = taxBasis; this.taxRate = taxRate; this.taxAmount = taxAmount;
        this.finalApprovedQuoteAmount = item.pricingType() == PricingCatalogue.PricingType.RANGE || item.pricingType() == PricingCatalogue.PricingType.QUOTE_REQUIRED ? finalAmount : null;
        this.quoteReasonOrApprovalReference = quoteReference; validate();
    }

    public static PricingSnapshot from(PricingCatalogue catalogue, String code, BigDecimal finalAmount,
                                       String taxBasis, BigDecimal taxRate, BigDecimal taxAmount, String quoteReference) {
        Objects.requireNonNull(catalogue, "catalogue");
        PricingCatalogue.Item item = catalogue.requireByCode(code);
        if (!catalogue.approvedForFinancialUse(item)) throw new IllegalArgumentException("Catalogue item is not approved for financial use: " + code);
        return new PricingSnapshot(item, catalogue.schemaVersion(), catalogue.read().taxTreatment(), finalAmount, taxBasis, taxRate, taxAmount, quoteReference);
    }

    private void validate() {
        if (!"INR".equals(currency)) throw new IllegalArgumentException("Pricing snapshot currency must be INR");
        if (!"APPROVED".equals(reviewStatus)) throw new IllegalArgumentException("Unapproved catalogue item cannot become a financial snapshot");
        if (catalogueSchemaVersion != PricingCatalogue.SUPPORTED_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported catalogue schema version");
        if (!PricingCatalogue.SUPPORTED_CATEGORIES.contains(category)) throw new IllegalArgumentException("Unsupported catalogue category");
        if (!PricingCatalogue.SUPPORTED_BILLING_UNITS.contains(billingUnit)) throw new IllegalArgumentException("Unsupported billing unit");
        if (!PricingCatalogue.categoryMatchesPricingType(category, pricingType)) throw new IllegalArgumentException("Catalogue category does not match pricing type");
        if (liftType != null && !PricingCatalogue.SUPPORTED_LIFT_TYPES.contains(liftType)) throw new IllegalArgumentException("Unsupported lift type");
        boolean amcFloor = pricingType == PricingCatalogue.PricingType.AMC_FLOOR_RATE; boolean amcPackage = pricingType == PricingCatalogue.PricingType.AMC_PACKAGE_RATE;
        if ((amcFloor || amcPackage) && liftType == null) throw new IllegalArgumentException("AMC snapshot requires lift metadata");
        if (amcFloor && (floorBandMinimum == null || floorBandMinimum < 1)) throw new IllegalArgumentException("AMC floor snapshot requires a positive band boundary");
        if (!amcFloor && floorBandMinimum != null) throw new IllegalArgumentException("Floor-band metadata is only valid for AMC floor rates");
        if (!amcFloor && !amcPackage && liftType != null) throw new IllegalArgumentException("Lift metadata is only valid for AMC rates");
        if (pricingType == PricingCatalogue.PricingType.FIXED || amcFloor || amcPackage) {
            requirePositive(selectedFixedAmount, "selectedFixedAmount");
            if (rangeMinimum != null || rangeMaximum != null || finalApprovedQuoteAmount != null) throw new IllegalArgumentException("Fixed snapshot contains incompatible amount fields");
        } else if (pricingType == PricingCatalogue.PricingType.RANGE) {
            requirePositive(rangeMinimum, "rangeMinimum"); requirePositive(rangeMaximum, "rangeMaximum"); requirePositive(finalApprovedQuoteAmount, "finalApprovedQuoteAmount");
            if (rangeMinimum.compareTo(rangeMaximum) > 0 || finalApprovedQuoteAmount.compareTo(rangeMinimum) < 0 || finalApprovedQuoteAmount.compareTo(rangeMaximum) > 0) throw new IllegalArgumentException("Range snapshot amount is invalid");
            if (selectedFixedAmount != null) throw new IllegalArgumentException("Range snapshot cannot use selectedFixedAmount");
        } else if (pricingType == PricingCatalogue.PricingType.QUOTE_REQUIRED) {
            requirePositive(finalApprovedQuoteAmount, "finalApprovedQuoteAmount");
            if (quoteReasonOrApprovalReference == null || quoteReasonOrApprovalReference.isBlank()) throw new IllegalArgumentException("Quote reason or approval reference is required");
            if (selectedFixedAmount != null || rangeMinimum != null || rangeMaximum != null) throw new IllegalArgumentException("Quotation snapshot cannot fabricate catalogue amount");
        }
        if (taxAmount != null && taxAmount.signum() < 0) throw new IllegalArgumentException("taxAmount cannot be negative");
        boolean determinedTax = taxRate != null || taxAmount != null && taxAmount.signum() != 0;
        if (determinedTax && (taxBasis == null || taxBasis.isBlank())) throw new IllegalArgumentException("Tax basis is required when tax metadata is determined");
        if (taxAmount != null && taxAmount.signum() != 0 && taxRate == null) throw new IllegalArgumentException("Nonzero tax amount requires tax rate metadata");
        if (taxRate != null) { requireNonNegative(taxRate, "taxRate"); if (taxAmount == null || PricingCatalogue.TAX_REQUIRES_FINANCE_APPROVAL.equals(taxTreatment)) throw new IllegalArgumentException("Tax rate requires explicit approved tax treatment"); }
    }

    public String catalogueCode() { return catalogueCode; } public int catalogueSchemaVersion() { return catalogueSchemaVersion; }
    public String itemDisplayName() { return itemDisplayName; } public String category() { return category; }
    public PricingCatalogue.PricingType pricingType() { return pricingType; } public String billingUnit() { return billingUnit; }
    public String currency() { return currency; } public BigDecimal selectedFixedAmount() { return selectedFixedAmount; }
    public BigDecimal rangeMinimum() { return rangeMinimum; } public BigDecimal rangeMaximum() { return rangeMaximum; }
    public String liftType() { return liftType; } public Integer floorBandMinimum() { return floorBandMinimum; }
    public String reviewStatus() { return reviewStatus; } public String taxTreatment() { return taxTreatment; }
    public String taxBasis() { return taxBasis; } public BigDecimal taxRate() { return taxRate; }
    public BigDecimal taxAmount() { return taxAmount; } public BigDecimal finalApprovedQuoteAmount() { return finalApprovedQuoteAmount; }
    public String quoteReasonOrApprovalReference() { return quoteReasonOrApprovalReference; }

    private static String required(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value; }
    private static void requirePositive(BigDecimal value, String field) { if (value == null || value.signum() <= 0) throw new IllegalArgumentException(field + " must be positive"); }
    private static void requireNonNegative(BigDecimal value, String field) { if (value.signum() < 0) throw new IllegalArgumentException(field + " cannot be negative"); }
}
