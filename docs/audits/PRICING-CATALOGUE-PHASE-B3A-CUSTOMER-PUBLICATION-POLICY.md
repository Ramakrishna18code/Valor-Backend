# Pricing Catalogue Phase B3A — Customer Publication Policy

Audit date: 2026-10-30

Scope: `D:\RKKKK\Valor-Backend` and `D:\RKKKK\Valor-Admin-Portal` only. This is a documentation-only readiness decision. No customer endpoint is added.

## 1. Executive summary

The backend catalogue is a validated, read-only JSON resource with schema version `1`, currency `INR`, 40 entries, and `taxTreatment=REQUIRES_BUSINESS_FINANCE_APPROVAL`. Every current JSON entry has `reviewStatus=APPROVED`, but catalogue approval is not billing approval: the catalogue is not consumed by invoice, checkout, AMC, refund, settlement, or installation calculations.

Customer publication is suitable for specific fixed reference services and for entries explicitly requiring a quotation. AMC floor/package tables remain `ADMIN_ONLY_REFERENCE` because their relationship to existing AMC plan IDs, terms, bonus months, and renewal pricing is unresolved. No entry is presently eligible to be shown as a guaranteed payable amount.

The future customer endpoint should be an authenticated, read-only projection, not a direct exposure of the Admin document. It must return only approved display fields, never accept a customer-supplied price, and disclose that the final approved service amount and applicable taxes are determined later under the approved backend workflow.

## 2. Current implementation verified

- `src/main/resources/pricing/pricing-catalogue.json` contains the current entries and approved fixed selections. Quote-only entries have no `fixedAmount`, `minAmount`, or `maxAmount`.
- `com.valor.pricing.PricingCatalogue` validates schema, INR currency, tax-treatment marker, unique codes, supported categories/types, positive amounts, quote-only shape, and AMC floor metadata. `read()` returns an immutable validated document.
- `PricingCatalogueController` exposes only `GET /api/v1/admin/pricing-catalogue` and calls `identities.requireAdmin()`.
- `AuthConfig` authorizes that Admin path only to `ADMIN` and `SUPER_ADMIN`; the generic `/api/v1/admin/**` fallback is Super Admin only. Existing customer routes use `hasRole("CUSTOMER")`.
- `PricingCatalogueHttpSecurityTest` verifies anonymous requests are unauthorized, customer and technician requests are forbidden, Admin/Super Admin GET is allowed, POST is not allowed, and DELETE is not allowed.
- Admin Portal `src/api/pricingCatalogue.js` calls `/admin/pricing-catalogue`; `src/pricingCataloguePage.jsx` renders the Admin-only catalogue and contains a presentation tax toggle. That toggle is not customer policy and must remain separate from any future customer endpoint.

## 3. Per-entry customer publication classification

Classification meanings:

- `CUSTOMER_DISPLAY_FIXED_REFERENCE`: may be displayed as a listed INR reference price, never as a guaranteed invoice amount.
- `CUSTOMER_DISPLAY_QUOTE_REQUIRED`: may be displayed as a service requiring assessment and quotation; no price is shown.
- `CUSTOMER_DISPLAY_BLOCKED`: must not be customer-published until a stated policy or scope blocker is resolved.
- `ADMIN_ONLY_REFERENCE`: may remain visible to authorized Admin users as an internal reference, but is not customer-published in B3A.

### Customer-displayable fixed reference entries

| Code | Customer classification | Listed INR reference | Reason |
|---|---|---:|---|
| `STANDARD_BREAKDOWN_CALLOUT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹2,000 | Approved fixed call-out reference. |
| `AFTER_HOURS_EMERGENCY_CALLOUT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹3,250 | Approved fixed after-hours call-out reference. |
| `DOOR_SENSOR_ADJUSTMENT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹2,250 | Specific repair reference. |
| `DOOR_ROLLER_REPLACEMENT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹4,250 | Specific repair reference. |
| `LANDING_DOOR_LOCK_REPLACEMENT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹5,500 | Specific repair reference. |
| `PUSH_BUTTON_REPLACEMENT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹2,750 | Specific repair reference. |
| `EMERGENCY_ALARM_REPAIR` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹2,750 | Specific repair reference. |
| `ARD_BATTERY_REPLACEMENT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹11,500 | Specific repair reference. |
| `CONTACTOR_REPLACEMENT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹5,500 | Specific repair reference. |
| `LIFT_WIRING_REPAIR` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹6,250 | Specific repair reference. |
| `DOOR_OPERATOR_REPAIR` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹16,500 | Specific repair reference. |
| `CONTROLLER_REVIEW` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹24,000 | Approved reference, but wording must say scope/eligibility is assessed; it is not a guaranteed replacement price. |
| `PARTIAL_MODERNIZATION` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹1,00,000 | Approved modernization reference; final scope must be confirmed. |
| `CONTROLLER_ELECTRICAL_UPGRADES` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹2,75,000 | Approved modernization reference; final scope must be confirmed. |
| `CABIN_RENOVATION` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹95,000 | Approved modernization reference; final scope must be confirmed. |
| `GENERAL_LIFT_INSPECTION` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹999 | Approved one-time inspection reference. |
| `PREVENTIVE_MAINTENANCE` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹1,200 | Approved catalogue reference; it does not replace the current maintenance formula. |
| `NON_AMC_BREAKDOWN` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹1,500 | Approved non-AMC reference; it does not replace current invoice pricing. |
| `REPAIRS` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹1,800 | Approved broad repair reference; UI must state that exact scope may change the final quote. |
| `DOOR_ALIGNMENT_ADJUSTMENT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹2,500 | Specific repair reference. |
| `LIFT_LEVELING_ADJUSTMENT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹2,250 | Specific repair reference. |
| `ELECTRICAL_FAULT_DIAGNOSIS` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹2,500 | Specific diagnosis reference. |
| `DETAILED_LIFT_SAFETY_INSPECTION` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹3,750 | Approved inspection reference. |
| `MAINTENANCE_AUDIT_REPORT` | `CUSTOMER_DISPLAY_FIXED_REFERENCE` | ₹5,500 | Approved audit/report reference; exact scope remains subject to confirmation. |

### Quote-required entries

| Code | Customer classification | Reason |
|---|---|---|
| `PASSENGER_ENTRAPMENT_RESPONSE` | `CUSTOMER_DISPLAY_QUOTE_REQUIRED` | Case assessment is required; no amount exists in JSON. |
| `TRACTION_ROPE_REPLACEMENT` | `CUSTOMER_DISPLAY_QUOTE_REQUIRED` | Quote-only and unpriced; no fallback amount is permitted. |
| `MOTOR_OR_DRIVE_REPLACEMENT` | `CUSTOMER_DISPLAY_QUOTE_REQUIRED` | Quote-only and unpriced; no fallback amount is permitted. |

### Admin-only entries

| Code(s) | Classification | Reason |
|---|---|---|
| `AMC_FLOOR_MANUAL_G1_G2`, `AMC_FLOOR_MANUAL_G3_G4`, `AMC_FLOOR_MANUAL_G5_G6`, `AMC_FLOOR_MANUAL_G7_G10`, `AMC_FLOOR_AUTO_G1_G4`, `AMC_FLOOR_AUTO_G5_G6`, `AMC_FLOOR_AUTO_G7_G10` | `ADMIN_ONLY_REFERENCE` | Internal floor-band proposal; floor semantics and its relationship to the live AMC purchase/renewal flows are unresolved. |
| `AMC_PACKAGE_BASIC`, `AMC_PACKAGE_BASIC_AUTO`, `AMC_PACKAGE_STANDARD`, `AMC_PACKAGE_STANDARD_AUTO`, `AMC_PACKAGE_COMPREHENSIVE`, `AMC_PACKAGE_COMPREHENSIVE_AUTO` | `ADMIN_ONLY_REFERENCE` | Package proposals do not yet map safely to legacy `essential`, `extended`, and `long-term` plans or their terms/bonus months. |

No current entry is classified `CUSTOMER_DISPLAY_BLOCKED`; the unresolved AMC decisions are represented as `ADMIN_ONLY_REFERENCE` because the data remains useful to authorized Admin users. If product later requests publication of an AMC entry before those decisions are closed, it must be reclassified `CUSTOMER_DISPLAY_BLOCKED` rather than exposed by default.

## 4. Customer-facing wording policy

Use the following terms consistently:

- **Listed reference price:** “Listed reference price: ₹X INR per [job/year]. This is an indicative catalogue reference, not a guaranteed payable amount.”
- **Quotation required:** “Quotation required. We will assess the service and confirm the final approved amount before payment.” Do not render a zero, range midpoint, blank currency value, or guessed amount.
- **Final approved service amount:** “Final approved service amount” is reserved for the amount confirmed by the authorized backend workflow and persisted on the resulting invoice/order.
- **Tax disclosure:** “Applicable taxes, if any, are pending confirmed tax policy and will be shown with the final approved amount.”

Do not say “GST included”, “GST extra”, “tax-free”, or “tax-exclusive” until Finance approves tax treatment, basis, rate, categories, and rounding. Do not advertise a reference amount as the invoice amount. The Admin-only “Tax included” / “Add applicable GST on top” presentation toggle is not a customer tax decision and must not be copied into a customer contract.

## 5. Proposed customer API contract

Proposed endpoint: `GET /api/v1/customer/pricing-catalogue`

The endpoint should require an authenticated active `CUSTOMER` and be read-only. Anonymous requests must be rejected; `TECHNICIAN`, `ADMIN`, and `SUPER_ADMIN` access should not be implicitly granted by the customer route. If staff need the same projection, define a separate explicitly authorized route.

Proposed response envelope, subject to existing `ApiResponse` conventions:

```json
{
  "message": "Customer pricing catalogue",
  "data": {
    "catalogueVersion": 1,
    "currency": "INR",
    "taxDisclosure": "Applicable taxes, if any, are pending confirmed tax policy and will be shown with the final approved amount.",
    "items": [
      {
        "code": "STANDARD_BREAKDOWN_CALLOUT",
        "category": "EMERGENCY_SERVICE",
        "name": "Standard breakdown call-out",
        "description": "Listed reference price for a standard breakdown call-out.",
        "billingUnit": "PER_JOB",
        "displayType": "FIXED_REFERENCE",
        "referenceAmount": 2000,
        "quoteRequired": false
      },
      {
        "code": "PASSENGER_ENTRAPMENT_RESPONSE",
        "category": "EMERGENCY_SERVICE",
        "name": "Passenger entrapment response",
        "description": "Assessment is required before the final service amount is approved.",
        "billingUnit": "PER_JOB",
        "displayType": "QUOTE_REQUIRED",
        "referenceAmount": null,
        "quoteRequired": true
      }
    ]
  }
}
```

The exact implementation must use the repository's existing response and identity conventions. The customer projection must not expose `reviewStatus`, `taxTreatment`, internal approval notes, schema implementation details, raw range-selection history, internal metadata, Admin-only AMC entries, or fields not needed for display. A catalogue version may be exposed only as a stable public version identifier; it must not be treated as a financial snapshot.

The endpoint has no POST, PUT, PATCH, or DELETE operation. Customer or technician request bodies must never contain an authoritative catalogue price. A service-selection request may contain a catalogue code, but the backend must re-load and validate the approved policy server-side and determine the final amount in its authorized workflow.

## 6. Security and redaction policy

- Require active authenticated customer identity and deny anonymous access.
- Deny technicians unless a separately approved role contract is created.
- Return a purpose-built DTO/projection, never `PricingCatalogue.CatalogueDocument` directly.
- Allowlist display fields and allowlist only the entries classified for customer display.
- Preserve exact catalogue code validation; reject unknown, inactive, unapproved, or customer-supplied price data.
- Keep the Admin endpoint and its authorization unchanged.
- Treat all mutation methods as unsupported and denied.
- Ensure customer input cannot change invoice subtotal, tax, total, payment amount, refund amount, or settlement records.
- Log or audit publication-policy changes separately from financial events; do not create an invoice or payment merely by reading the catalogue.

## 7. Future customer UI integration contract

The out-of-scope customer frontend should:

- Present category tabs or grouped sections for services, inspections, repairs, modernization, and quotation-required services.
- Use fixed-reference cards with `₹X INR` and the “Listed reference price” disclaimer adjacent to the amount.
- Use a visually distinct “Quotation required” card with no amount and an assessment/quotation request entry point.
- Provide service detail and quotation-request actions that submit a service/code choice only; they must not submit a price as authoritative.
- Remain mobile-friendly: readable cards, no horizontal table dependency, accessible labels, and touch-sized actions.
- Show loading, empty, unauthorized, and retryable error states without inventing catalogue values.
- Place the price disclaimer near the amount and repeat the tax disclosure before a quotation/request action.
- Clearly distinguish a catalogue reference from an invoice amount: the catalogue screen must not look like checkout, receipt, payment authorization, or a tax invoice.

The Admin Portal must not be modified to impersonate or preview the customer website in this phase. The customer frontend repository is outside scope.

## 8. Relationship with invoices and actual payment amounts

The future flow is:

1. Customer views a published reference price or requests a quotation.
2. Customer selects or requests a service.
3. Backend validates service eligibility and the approved pricing policy.
4. An authorized backend workflow determines the final financial amount.
5. An immutable price snapshot is associated with the resulting invoice/order.
6. Checkout uses the persisted invoice/payment amount.
7. Refund and settlement use the original persisted payment and provider records.

The current catalogue is not authoritative for existing calculations. Current pricing formulas, AMC plans, tax calculations, invoices, payments, refunds, settlement, installation billing, and customer charges must remain unchanged. A later integration must prove that changing the catalogue cannot rewrite an existing invoice, payment, provider amount, refund, or settlement.

## 9. Tax-policy limitations

The JSON explicitly retains `REQUIRES_BUSINESS_FINANCE_APPROVAL`. This report therefore recommends disclosure only, without GST calculation or inclusion/exclusion claims. Finance must still approve tax applicability, tax basis, rate, rounding, and whether each listed amount is before or after tax. The existing AMC purchase behavior that applies its current tax calculation is a separate financial implementation and must not be altered by customer publication.

## 10. Required future tests

Before implementing the endpoint, add focused HTTP/contract tests for:

- anonymous denial;
- active customer access to only the approved projection;
- technician denial unless separately authorized;
- unchanged Admin endpoint behavior;
- POST/PUT/PATCH/DELETE denial;
- no internal metadata leakage;
- quote-only entries having no fabricated amount;
- unknown/unapproved/AMC-admin-only entries being excluded;
- customer-submitted amounts not influencing checkout;
- customer API reads not changing invoice or payment totals.

These are application/HTTP tests only. No database or external-provider tests are part of B3A.

## 11. Files inspected and exact evidence

Backend:

- `src/main/resources/pricing/pricing-catalogue.json` — current 40-entry data, fixed amounts, quote-only entries, INR, schema version, and unresolved tax marker.
- `src/main/java/com/valor/pricing/PricingCatalogue.java` — validation, immutable read model, exact code lookup, and approval gate.
- `src/main/java/com/valor/pricing/PricingCatalogueController.java` — `GET /api/v1/admin/pricing-catalogue` and `requireAdmin()`.
- `src/main/java/com/valor/auth/AuthConfig.java` — Admin-only route authorization, customer route authorization, and Admin fallback.
- `src/test/java/com/valor/pricing/PricingCatalogueHttpSecurityTest.java` — anonymous/customer/technician denial, Admin read access, and mutation-method checks.
- `src/main/java/com/valor/pricing/PricingSnapshot.java` — in-memory validation only; not a persisted billing integration.
- `docs/audits/PRICING-CATALOGUE-PHASE-B2A-MAPPING-AUDIT.md`, `...B2C-READINESS-AUDIT.md`, `...B2D-CONTRACT-DESIGN.md`, `...B3-FIRST-FLOW.md` — prior decisions and unresolved integration/tax boundaries, rechecked against the source above.

Admin Portal:

- `src/api/pricingCatalogue.js` — Admin client calls `/admin/pricing-catalogue`.
- `src/pricingCataloguePage.jsx` and `src/pricingCataloguePage.css` — Admin-only presentation, read-only wording, and separate presentation tax toggle.
- `src/api/runtime.js` and `src/main.jsx` — Admin pricing page wiring and navigation.

## 12. Recommended next implementation phase

Phase B3B should implement the authenticated customer read-only projection only after product/security review of the classification matrix and endpoint DTO. It should add controller/service tests, DTO allowlisting, role tests, and redaction tests. A later, separately approved financial-integration phase must resolve service taxonomy, AMC mapping, snapshot persistence, tax policy, and payment/invoice invariants before any catalogue amount can influence billing.

## 13. Explicit blockers and non-goals

Blockers: unresolved tax policy; unresolved AMC floor/package and legacy-plan mapping; no approved catalogue-to-service eligibility mapping; no persisted catalogue snapshot contract; and unresolved DB-36/DB-37.

Non-goals: adding the customer endpoint; changing Admin UI; changing prices; changing pricing formulas; integrating catalogue data with invoices, payments, AMC, tax, refunds, settlement, installation, or customer charges; database/Flyway changes; database tests; external-provider tests; startup tests; deployment; commit; push; or publication to the customer frontend.

