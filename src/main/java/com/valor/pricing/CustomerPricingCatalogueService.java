package com.valor.pricing;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CustomerPricingCatalogueService {
    static final String PRICE_DISCLAIMER = "Displayed prices are reference prices and are not guaranteed payable amounts.";
    static final String TAX_DISCLOSURE = "Applicable taxes and the final payable amount will be confirmed in the approved quotation or invoice.";

    static final Set<String> FIXED_CODES = Set.of(
            "STANDARD_BREAKDOWN_CALLOUT", "AFTER_HOURS_EMERGENCY_CALLOUT", "DOOR_SENSOR_ADJUSTMENT",
            "DOOR_ROLLER_REPLACEMENT", "LANDING_DOOR_LOCK_REPLACEMENT", "PUSH_BUTTON_REPLACEMENT",
            "EMERGENCY_ALARM_REPAIR", "ARD_BATTERY_REPLACEMENT", "CONTACTOR_REPLACEMENT",
            "LIFT_WIRING_REPAIR", "DOOR_OPERATOR_REPAIR", "CONTROLLER_REVIEW", "PARTIAL_MODERNIZATION",
            "CONTROLLER_ELECTRICAL_UPGRADES", "CABIN_RENOVATION", "GENERAL_LIFT_INSPECTION",
            "PREVENTIVE_MAINTENANCE", "NON_AMC_BREAKDOWN", "REPAIRS", "DOOR_ALIGNMENT_ADJUSTMENT",
            "LIFT_LEVELING_ADJUSTMENT", "ELECTRICAL_FAULT_DIAGNOSIS", "DETAILED_LIFT_SAFETY_INSPECTION",
            "MAINTENANCE_AUDIT_REPORT");
    static final Set<String> QUOTE_CODES = Set.of(
            "PASSENGER_ENTRAPMENT_RESPONSE", "TRACTION_ROPE_REPLACEMENT", "MOTOR_OR_DRIVE_REPLACEMENT");
    static final Set<String> PUBLIC_CODES = Set.copyOf(java.util.stream.Stream.concat(FIXED_CODES.stream(), QUOTE_CODES.stream()).toList());

    private final PricingCatalogue catalogue;

    public CustomerPricingCatalogueService(PricingCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public CustomerPricingCatalogueResponse read() {
        var source = catalogue.read();
        Map<String, PricingCatalogue.Item> byCode = source.items().stream()
                .collect(Collectors.toMap(PricingCatalogue.Item::code, item -> item, (left, right) -> left));
        List<CustomerPricingCatalogueResponse.Item> items = PUBLIC_CODES.stream()
                .sorted()
                .map(code -> toPublicItem(code, byCode.get(code)))
                .toList();
        return new CustomerPricingCatalogueResponse(source.schemaVersion(), source.currency(), PRICE_DISCLAIMER, TAX_DISCLOSURE, items);
    }

    private CustomerPricingCatalogueResponse.Item toPublicItem(String code, PricingCatalogue.Item item) {
        if (item == null || !"APPROVED".equals(item.reviewStatus())) {
            throw new IllegalStateException("Customer pricing catalogue publication is incomplete");
        }
        if (FIXED_CODES.contains(code)) {
            if (item.pricingType() != PricingCatalogue.PricingType.FIXED || item.fixedAmount() == null || item.fixedAmount().signum() <= 0)
                throw new IllegalStateException("Customer pricing catalogue publication is invalid");
            return new CustomerPricingCatalogueResponse.Item(code, item.category(), item.name(),
                    "Listed reference price for " + item.name() + ". The final approved service amount may vary after assessment.",
                    "REFERENCE_FIXED", item.fixedAmount(), false, item.billingUnit());
        }
        if (item.pricingType() != PricingCatalogue.PricingType.QUOTE_REQUIRED || !Boolean.TRUE.equals(item.quoteRequired())
                || item.fixedAmount() != null || item.minAmount() != null || item.maxAmount() != null)
            throw new IllegalStateException("Customer pricing catalogue publication is invalid");
        return new CustomerPricingCatalogueResponse.Item(code, item.category(), item.name(),
                "Quotation required. We will assess the service and confirm the final approved amount before payment.",
                "QUOTE_REQUIRED", null, true, item.billingUnit());
    }
}
