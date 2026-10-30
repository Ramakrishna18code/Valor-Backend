# Pricing Catalogue Phase B2A — Mapping and Compatibility Audit

Audit date: 2026-10-29

Scope: `D:\RKKKK\Valor-Backend` and `D:\RKKKK\Valor-Admin-Portal` only.

This is a documentation-first audit. No financial flow consumes the JSON catalogue or `PricingSnapshot` today. No prices were approved, and no payment or invoice behavior was changed.

## Current implementation

The catalogue is loaded from `src/main/resources/pricing/pricing-catalogue.json` by `com.valor.pricing.PricingCatalogue`. Loading validates schema version `1`, INR currency, non-empty items, duplicate codes, basic required fields, positive fixed amounts, valid ranges, quotation-required entries, and AMC floor bounds.

`PricingCatalogue` exposes exact case-sensitive lookup through `findByCode()` and `requireByCode()`, the document schema version through `schemaVersion()`, and approval gating through `approvedForFinancialUse()`. The B2B display catalogue marks its approved price entries `APPROVED`; tax treatment remains `REQUIRES_BUSINESS_FINANCE_APPROVAL`, and this approval does not connect the catalogue to authoritative billing.

`PricingSnapshot` is an immutable, catalogue-backed Java value object. Its constructor is private; `PricingSnapshot.from()` performs exact catalogue lookup and requires the loaded item to be approved before creating a snapshot. It validates INR, approved status, category/pricing-type compatibility, fixed/range/quote amount rules, AMC metadata, nonnegative tax amounts, and tax-basis requirements. It is not persisted and is not wired into invoices, payments, AMC orders, renewals, or installation milestones.

## Mapping matrix

| Flow | Current source and authority | Plausible catalogue relationship | Mapping status and decisions | Future snapshot requirements | Compatibility risks |
|---|---|---|---|---|---|
| Routine-maintenance service invoice | `PricingService.servicePrice(ServiceRequest)` accepts `ROUTINE_MAINTENANCE`; manual base ₹999 or automatic base ₹1,499, plus ₹200 for each floor above five. `CommerceService.createServiceInvoice()` and linked `createInvoice()` use `calculatedAmount()`. | `PREVENTIVE_MAINTENANCE` (₹1,200 fixed) is only a possible business relationship. | Ambiguous. The current formula is not the catalogue amount, and routine maintenance is not explicitly defined as preventive maintenance. Owner must approve service taxonomy and price basis. | Code, schema version, name, category, fixed amount, billing unit, lift type, actual floor input, tax basis, final amount. | Changing to ₹1,200 would change existing invoices. Floor surcharge has no catalogue equivalent. |
| Breakdown service invoice | `PricingService.servicePrice()` accepts `BREAKDOWN` and uses the same manual/automatic base plus floor surcharge as routine maintenance. | `NON_AMC_BREAKDOWN` fixed ₹1,500 or `STANDARD_BREAKDOWN_CALLOUT` range ₹1,500–₹2,500. | Ambiguous. No evidence identifies which catalogue item equals the existing `BREAKDOWN` workflow. | Selected code, range metadata if applicable, final approved amount, quote/estimate basis, lift inputs, tax basis. | A range cannot itself become an invoice total. Existing clients expect the current invoice response shape. |
| After-hours emergency | No separate `PricingService` branch; `EMERGENCY` is rejected by the current service pricing method. | `AFTER_HOURS_EMERGENCY_CALLOUT`. | Unavailable without a service classification and workflow rule. | Code, case/service classification, final approved amount, quote reason if inspection is required. | Introducing the item would expand workflow semantics, not merely replace a constant. |
| Passenger entrapment response | No current catalogue-backed pricing path. | `PASSENGER_ENTRAPMENT_RESPONSE`, `QUOTE_REQUIRED`. | Quotation-required; no amount may be fabricated. | Code, approval reference, case facts, final quote amount, quote reason, tax basis. | Must remain separate from ordinary breakdown settlement and emergency workflow behavior. |
| Specific repairs and spare parts | Current catalogue is not used by `PartsService` or service invoicing. Existing part inventory has its own item/category/stock model. | Specific repair codes such as `DOOR_SENSOR_ADJUSTMENT`, `DOOR_ROLLER_REPLACEMENT`, and others. | Relationship unavailable. Names do not prove that inventory items or service requests use the same taxonomy. | Code, final amount, part/service distinction, quantity if later supported, approval reference, tax basis. | Reusing the parts catalogue could incorrectly couple inventory and payable service pricing. |
| Controller | No current authoritative mapping. | `CONTROLLER_REVIEW`, range ₹8,000–₹40,000. | Ambiguous item name and scope; owner must define whether this means controller repair, replacement, or a specific component. | Exact approved item wording, selected/final amount, scope, quote reference. | A range cannot be charged without a selected approved amount. |
| Traction rope and motor/drive | No supplied amount and no current catalogue-backed billing path. | `TRACTION_ROPE_REPLACEMENT` and `MOTOR_OR_DRIVE_REPLACEMENT`, both quote-required. | Quote-required; no fallback price. | Final quote, reason, approval reference, tax basis. | Missing prices must fail closed, not use legacy or guessed defaults. |
| AMC purchase | `CommerceService.amcPlanRate()` maps client plan IDs `essential`, `extended`, and `long-term` to hard-coded terms and subtotals ₹12,000, ₹22,000, and ₹32,000. `createAmcPurchaseCheckout()` applies 18% GST, stores subtotal/tax/total in `AmcPurchaseOrder`, and creates the Razorpay order from `PaymentRecord.amount`. | AMC package codes `AMC_PACKAGE_BASIC`, `AMC_PACKAGE_STANDARD`, `AMC_PACKAGE_COMPREHENSIVE` or floor-rate codes. | No approved mapping. Existing terms include bonus months and do not match the annual package table. Floor and package tables are explicitly separate. | Existing plan ID, catalogue code/version, plan name, months/bonus months, lift type, pricing basis, subtotal, tax basis, tax amount, total. | Changing plan mapping or terms would affect checkout, order amount, invoice creation, and client compatibility. |
| AMC renewal quote | `CommerceService.quoteRenewal()` ignores the submitted amount for calculation and calls `calculatedAmount(null, contract)`, which uses `PricingService.amcPrice(lift)`: ₹14,999/₹24,999 base plus floor surcharge. Renewal settlement requires linked successful payment and valid invoice/payment ownership associations. | AMC floor or package rates. | Unresolved. Business must choose floor-based renewal, package renewal, or explicit Admin quote. | Catalogue code/version, lift type, actual floor input, selected/final amount, approval reference, tax basis, renewal period. | Existing `RenewalView` can remain compatible, but changing quote authority affects invoice/payment association and renewal activation. |
| General standalone invoice | `CommerceService.createInvoice()` accepts Admin-supplied subtotal, tax amount, currency, and description when no service request or AMC contract is linked. | No automatic catalogue mapping. | Unavailable. This is an explicit manual invoice path, not a catalogue-priced flow. | Only if business later requires catalogue association; otherwise preserve manual invoice semantics. | Do not silently force catalogue lookup or reject existing manual invoices. |
| Installation milestone invoice | `InstallationMilestoneInvoiceService.createInvoice()` reads amount and tax amount from the approved installation schedule/milestone definition. It requires approved schedule version, approvals, matching definition, positive amount, explicit tax, currency, and completed installation. | Modernization items may be conceptually related, but no direct mapping exists. | Keep separate. Catalogue ranges are not installation schedules or milestone amounts. | If integrated later: catalogue code/version plus immutable approved schedule version, milestone amount, tax basis, and trigger. | Coupling catalogue changes to approved installation schedules could rewrite or invalidate commercial milestones. |
| Payment linked to invoice | `PaymentRecord.amount` is persisted before gateway checkout; Razorpay capture checks amount and currency against the stored payment and linked invoice total. Wallet payment uses the existing invoice total. | Catalogue is only an upstream source for a future invoice/checkout obligation. | No direct mapping today. Payment must never re-read the current JSON catalogue. | Preserve the invoice/payment amount and catalogue snapshot captured before checkout. | Catalogue edits after checkout must not alter provider amount, settlement, refund, or receipt behavior. |
| Refunds and settlement | Refunds operate on persisted `PaymentRecord` values and provider references. Settlement validates provider references, amount, currency, ownership, and invoice/payment associations. | None directly. | Preserve unchanged. Catalogue metadata is explanatory history, not a refund calculation source. | Original catalogue snapshot may be retained for audit; refund amount remains payment/refund data. | Recalculating refunds from current catalogue data would break historical integrity. |

## Unresolved business decisions

1. **AMC floor versus package pricing:** decide whether a purchase/renewal selects one model, whether package prices override floor rates, or whether one constrains the other. No inference is safe.
2. **G+ floor semantics:** define whether `Lift.floorCount` represents total served floors, floors above ground, or another count. Confirm the meaning of “up to G+4” and all band boundaries.
3. **GST inclusion:** confirm whether each supplied amount is before or after GST. The current AMC purchase implementation applies 18% in code, while catalogue metadata says tax treatment requires business/finance approval.
4. **Range semantics:** decide whether ranges are display-only estimates, inspection-required estimates, or inputs to an Admin-approved final quote.
5. **Legacy AMC compatibility:** decide whether `essential`, `extended`, and `long-term` map to Basic, Standard, and Comprehensive. Terms and bonus months do not currently match the annual package entries.
6. **Unconfirmed names:** define the exact scope of Controller and Maintenance audit and report.
7. **Unpriced items:** confirm that traction rope and motor/drive remain quotation-only until approved prices exist.
8. **Tax basis:** define approved tax categories, rounding, and tax basis before any catalogue tax rate can become authoritative.

## Phase B1 validation findings

### Verified protections

- Duplicate catalogue codes are rejected during `PricingCatalogue.validate()` and therefore cannot produce an ambiguous lookup map.
- `findByCode()` is exact and case-sensitive; it does not silently normalize or select a different item.
- `PricingSnapshot.from()` rejects items whose `reviewStatus` is not exactly `APPROVED`.
- Fixed snapshots require a positive fixed amount.
- Range snapshots require both bounds, a positive final approved amount, and a final amount within the range.
- Quote-required snapshots require a positive final amount and a nonblank approval/quote reference; they cannot use fixed or range metadata as a payable amount.
- A supplied tax rate requires an explicit tax basis and a non-unresolved tax-treatment value.
- Snapshot fields are record components and monetary values use `BigDecimal`; there are no mutators or floating-point calculations.
- The snapshot is not persisted or connected to current billing, so it cannot currently change financial behavior.

### Verified limitations to address before financial integration

- The catalogue now enforces closed sets for category, billing unit, and lift type, and rejects incompatible category/pricing-type combinations.
- Schema version `1` is the only supported version; catalogue loading and snapshot creation reject null, malformed, and future versions.
- The catalogue item list is defensively copied and exposed as an unmodifiable list, so `read()` cannot diverge from exact-code lookup.
- `floorBandMinimum` is explicitly the lower boundary of an AMC floor band. It is positive when present, but no customer-floor selection or upper-bound inference is implemented.
- The current tax treatment remains `REQUIRES_BUSINESS_FINANCE_APPROVAL`; tax rates cannot be supplied against that unresolved treatment. No GST is calculated here.
- Approved catalogue entries can create a validated in-memory snapshot through `PricingSnapshot.from()`; no snapshot is persisted or consumed by current billing flows.

These are integration-readiness limitations, not observed financial vulnerabilities in the current system, because no current invoice, payment, checkout, renewal, refund, or settlement flow calls the catalogue or snapshot.

## Proposed integration sequence and approval gates

## B2B approved display selections

The owner approved the catalogue entries for reference display. This approval is separate from tax-policy approval: the catalogue remains `REQUIRES_BUSINESS_FINANCE_APPROVAL` for tax treatment, and no existing financial flow consumes these values.

Originally ranged entries use the following fixed reference selections. These are catalogue display values, not a migration of service, AMC, invoice, or checkout calculations.

| Code | Original supplied range | Selected fixed reference | Selection rationale |
|---|---:|---:|---|
| `STANDARD_BREAKDOWN_CALLOUT` | ₹1,500–₹2,500 | ₹2,000 | Rounded midpoint |
| `AFTER_HOURS_EMERGENCY_CALLOUT` | ₹2,500–₹4,000 | ₹3,250 | Rounded midpoint |
| `DOOR_SENSOR_ADJUSTMENT` | ₹1,500–₹3,000 | ₹2,250 | Rounded midpoint |
| `DOOR_ROLLER_REPLACEMENT` | ₹2,500–₹6,000 | ₹4,250 | Rounded midpoint |
| `LANDING_DOOR_LOCK_REPLACEMENT` | ₹3,000–₹8,000 | ₹5,500 | Rounded midpoint |
| `PUSH_BUTTON_REPLACEMENT` | ₹1,500–₹4,000 | ₹2,750 | Rounded midpoint |
| `EMERGENCY_ALARM_REPAIR` | ₹1,500–₹4,000 | ₹2,750 | Rounded midpoint |
| `ARD_BATTERY_REPLACEMENT` | ₹8,000–₹15,000 | ₹11,500 | Rounded midpoint |
| `CONTACTOR_REPLACEMENT` | ₹3,000–₹8,000 | ₹5,500 | Rounded midpoint |
| `LIFT_WIRING_REPAIR` | ₹2,500–₹10,000 | ₹6,250 | Rounded midpoint |
| `DOOR_OPERATOR_REPAIR` | ₹8,000–₹25,000 | ₹16,500 | Rounded midpoint |
| `CONTROLLER_REVIEW` | ₹8,000–₹40,000 | ₹24,000 | Rounded midpoint; exact scope remains case-assessed |
| `PARTIAL_MODERNIZATION` | ₹50,000–₹1,50,000 | ₹1,00,000 | Rounded midpoint |
| `CONTROLLER_ELECTRICAL_UPGRADES` | ₹1,50,000–₹4,00,000 | ₹2,75,000 | Rounded midpoint |
| `CABIN_RENOVATION` | ₹40,000–₹1,50,000 | ₹95,000 | Rounded midpoint |
| `DOOR_ALIGNMENT_ADJUSTMENT` | ₹1,500–₹3,500 | ₹2,500 | Rounded midpoint |
| `LIFT_LEVELING_ADJUSTMENT` | ₹1,500–₹3,000 | ₹2,250 | Rounded midpoint |
| `ELECTRICAL_FAULT_DIAGNOSIS` | ₹1,500–₹3,500 | ₹2,500 | Rounded midpoint |
| `DETAILED_LIFT_SAFETY_INSPECTION` | ₹2,500–₹5,000 | ₹3,750 | Rounded midpoint |
| `MAINTENANCE_AUDIT_REPORT` | ₹3,500–₹7,500 | ₹5,500 | Rounded midpoint; developer-selected wording |

`PASSENGER_ENTRAPMENT_RESPONSE`, `TRACTION_ROPE_REPLACEMENT`, and `MOTOR_OR_DRIVE_REPLACEMENT` remain quotation-only and have no fabricated amount. Legacy AMC IDs `essential`, `extended`, and `long-term` remain separate from the approved annual package proposals.

For display semantics, numeric `floorCount` remains authoritative. `G+0` means the ground-floor baseline, `G+1` means one floor above ground, and so on; G+ labels are alternate presentation of the same normalized numeric concept, not a second pricing input. The catalogue floor band is informational and does not introduce a live floor-to-price algorithm.

### Gate 1 — Catalogue contract

- Close category, billing-unit, lift-type, schema-version, and floor-band validation.
- Confirm exact service taxonomy and item wording.
- Confirm all candidate prices and approval status.

### Gate 2 — Tax policy

- Approve inclusive/exclusive treatment.
- Approve tax basis, rate, rounding, and applicable tax categories.
- Keep the existing 18% AMC purchase behavior unchanged until this gate is complete.

### Gate 3 — Fixed service flow

- Select one exact fixed item whose workflow meaning is approved.
- Add a backend-only mapping test.
- Capture catalogue metadata and final amount in a new invoice snapshot mechanism.
- Prove client-submitted amounts cannot override the selected amount.

### Gate 4 — Range and quotation flows

- Define how an estimate becomes a final approved amount.
- Require inspection/approval references where appropriate.
- Reject range-only and quote-only attempts at invoice/payment creation.

### Gate 5 — AMC purchase compatibility

- Define package/floor selection.
- Preserve `essential`, `extended`, and `long-term` request compatibility.
- Prove checkout, invoice, provider amount, capture validation, and settlement all use one persisted amount and snapshot.

### Gate 6 — AMC renewal

- Decide whether renewal is catalogue-priced or explicitly Admin-quoted.
- Preserve successful-payment requirements, ownership checks, and idempotent renewal activation.

### Gate 7 — Installation and historical rollout

- Keep installation schedules authoritative unless a separate approved integration is designed.
- Backfill no historical records.
- Ensure catalogue edits affect only future approved transactions.

## Focused tests required before integration

- Exact service-type-to-catalogue-code mapping tests.
- Manual/automatic lift and floor-band selection tests using confirmed floor semantics.
- Candidate/unapproved item fail-closed tests.
- Fixed, range, and quotation-required invoice tests.
- Client amount tampering tests.
- Catalogue version and snapshot persistence/reproducibility tests.
- Price change between two transactions: first transaction retains its original snapshot; second uses the new approved version.
- Invoice immutability after catalogue changes.
- AMC legacy plan ID compatibility tests.
- AMC package/floor selection and term/bonus-month tests.
- Checkout amount equals persisted payment amount and invoice total.
- Razorpay capture amount/currency validation remains unchanged.
- Renewal quote, invoice association, successful payment, and idempotent settlement tests.
- Installation schedule authority and catalogue non-interference tests.
- Refund and receipt tests proving they use persisted payment/invoice values, not current catalogue data.

## Financial behavior statement

No files involved in `PricingService`, `CommerceService`, invoice calculation, payment creation, Razorpay validation, refund calculation, settlement, cash OTP, technician workflow, or installation milestone billing were modified by this audit. No catalogue entry was approved, and no current financial flow was integrated with the catalogue or snapshot.

## Files and commands

New file created:

- `D:\RKKKK\Valor-Backend\docs\audits\PRICING-CATALOGUE-PHASE-B2A-MAPPING-AUDIT.md`

Commands run:

- `git status --short` in both repositories — existing modifications and untracked files preserved.
- `git diff --check` during inspection — no pre-existing whitespace errors reported.
- Source inspection with `Get-Content` and `rg` only.

No tests were necessary for this documentation-only audit. No database, Flyway, application startup, external service, `.env`, credential, token, commit, push, deploy, reset, clean, revert, or stash operation was performed.

Remaining risks are the unresolved business decisions above and the verified Phase B1 validation limitations. This report does not claim production readiness.
