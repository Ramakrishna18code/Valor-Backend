# Pricing Catalogue Phase B3 — First Flow Decision

Decision date: 2026-10-29

Scope: `D:\RKKKK\Valor-Backend` and `D:\RKKKK\Valor-Admin-Portal` only.

## 1. Decision

No existing service-pricing flow qualifies for safe B3 integration.

No production code was changed. The catalogue remains read-only reference data. Existing customer charges, invoice calculations, AMC behavior, tax behavior, payment amounts, refunds, settlement, cash OTP, technician workflows, and installation billing remain unchanged.

## 2. Evidence reviewed

- `docs/audits/PRICING-CATALOGUE-PHASE-B2C-READINESS-AUDIT.md`
- `docs/audits/PRICING-CATALOGUE-PHASE-B2D-CONTRACT-DESIGN.md`
- `src/main/java/com/valor/pricing/PricingService.java`
- `src/main/java/com/valor/pricing/PricingCatalogue.java`
- `src/main/java/com/valor/pricing/PricingSnapshot.java`
- `src/main/resources/pricing/pricing-catalogue.json`
- `src/main/java/com/valor/commerce/CommerceController.java`
- `src/main/java/com/valor/commerce/CommerceService.java`
- `src/main/java/com/valor/commerce/Invoice.java`
- `src/main/java/com/valor/commerce/PaymentRecord.java`
- `src/main/java/com/valor/commerce/AmcPurchaseOrder.java`
- `src/main/java/com/valor/installation/InstallationMilestoneInvoiceService.java`
- Admin Portal pricing API/page/navigation implementation and existing focused tests.

## 3. Candidate-flow evaluation

| Candidate | Catalogue relationship | B3 result | Blocking evidence |
|---|---|---|---|
| Routine maintenance | Possible relation to `PREVENTIVE_MAINTENANCE` | **BLOCKED** | Current `PricingService.servicePrice(...)` uses manual ₹999 or automatic ₹1,499 plus ₹200 for every floor above five. Catalogue value is a fixed ₹1,200 and has no equivalent lift/floor surcharge formula. |
| Breakdown pricing | Possible relation to `NON_AMC_BREAKDOWN` or `STANDARD_BREAKDOWN_CALLOUT` | **BLOCKED** | Current `BREAKDOWN` uses the same lift/floor formula as routine maintenance. The catalogue entries have different fixed semantics and no established service-classification equivalence. |
| One-time services | Possible relation to inspection/maintenance entries | **BLOCKED** | Similar names do not prove that existing service types, invoice eligibility, or customer charges mean the same thing. No exact current-flow mapping was established. |
| AMC purchase | Possible relation to AMC package entries | **BLOCKED** | `essential`, `extended`, and `long-term` remain legacy IDs with existing terms, durations/bonus months, and amounts. Basic/Standard/Comprehensive annual proposals cannot be substituted without migration. |
| AMC renewal | Possible relation to floor/package entries | **BLOCKED** | Renewal quotes call the legacy AMC formula using lift type and floor surcharge. Package/floor selection, terms, and tax behavior remain unresolved. |
| Installation milestones | Possible conceptual relation to modernization | **BLOCKED** | Installation invoices use approved schedule JSON and milestone amount/tax. Catalogue entries are not installation schedule definitions. |

## 4. Existing formula differences

The current service formula in `PricingService` is:

```text
service amount = lift-type base + ₹200 × max(0, floorCount − 5)
manual base = ₹999
automatic base = ₹1,499
```

Representative current amounts versus the closest fixed catalogue references:

| Flow/input | Existing server formula | Closest catalogue reference | Difference |
|---|---:|---:|---|
| Manual, floorCount 0–5 | ₹999 | General lift inspection ₹999 or preventive maintenance ₹1,200 | Name and service meaning are not proven equivalent. |
| Automatic, floorCount 0–5 | ₹1,499 | No exact fixed equivalent | Catalogue cannot preserve the current automatic charge by code alone. |
| Manual, floorCount 6 | ₹1,199 | Preventive maintenance ₹1,200 | ₹1 difference, but a close value is not a semantic or compatibility proof. |
| Automatic, floorCount 6 | ₹1,699 | No exact fixed equivalent | Catalogue fixed values do not encode the surcharge formula. |
| Manual, floorCount 10 | ₹1,999 | No exact fixed equivalent | Catalogue lacks the existing per-floor behavior. |
| Automatic, floorCount 10 | ₹2,499 | No exact fixed equivalent | Replacing the formula would change customer charges. |

The catalogue’s fixed reference amount cannot safely replace this formula without an owner-approved compatibility decision and a deliberate pricing migration. No such decision is established in B2C or B2D.

## 5. Tax and invoice/payment compatibility

The catalogue document remains `REQUIRES_BUSINESS_FINANCE_APPROVAL` for tax treatment. `PricingSnapshot.from(...)` blocks tax rates while that treatment is unresolved. Existing AMC purchase code independently calculates 18% GST with `BigDecimal` and HALF_UP rounding. That is current implementation behavior, not an approved universal catalogue tax policy.

Service and generic invoice creation persist invoice subtotal, tax amount, total amount, and currency through existing contracts. Payments use persisted `PaymentRecord.amount` and linked invoice/provider validation. A B3 switch would need to preserve those values and historical links; there is no persisted catalogue snapshot relationship yet.

Therefore, no catalogue amount can be introduced into an existing payable flow without deciding:

- whether the catalogue is reference-only or replaces the current formula;
- how lift type and numeric floor count affect the selected amount;
- whether the customer charge is allowed to change;
- which tax policy and rounding rules apply;
- how catalogue code/version and selected amount are persisted with the invoice/order/quote.

## 6. Required decisions before B3 can proceed

1. Confirm whether routine maintenance and breakdown are distinct catalogue products or retain their existing formula.
2. Define the exact relationship between `Lift.floorCount`, G+ labels, and any future price bands.
3. Select one service type with a demonstrable semantic match and approve its customer-charge migration policy.
4. Define whether fixed catalogue prices are reference display values, quote values, or authoritative payable prices.
5. Approve tax classification, tax basis, inclusive/exclusive semantics, rate, and rounding independently of price approval.
6. Define persisted snapshot fields and invoice/order/quote ownership before changing any financial flow.
7. Keep legacy AMC IDs/terms separate until an explicit compatibility/migration decision exists.

## 7. No implementation occurred

The following were intentionally not changed:

- `PricingService`
- `CommerceService`
- invoice controllers/entities/persistence
- AMC purchase and renewal flows
- installation milestone billing
- tax/GST calculations
- payment, Razorpay, refund, receipt, or settlement behavior
- cash OTP, technician workflows, or job completion
- catalogue endpoint authorization or Admin Portal mutation surface
- database schemas and migrations

No customer-visible charge can change as a result of this B3 phase because no production integration occurred.

## 8. Tests and verification

No database, Flyway, application-startup, external-service, or payment-provider test was run.

Only read-only working-tree checks were run:

```text
git status --short                         (Backend and Admin Portal inspected)
git diff --check                           (Backend: exit code 0)
git diff --check                           (Admin Portal: exit code 0)
```

No focused pricing test was necessary because the B3 gate failed before implementation. Existing focused catalogue and HTTP authorization results remain documented in the B2C/B2D reports.

## 9. Final status

| Item | Status |
|---|---|
| Flow safely integrated | **No — blocked** |
| Catalogue code selected for billing | **None** |
| Customer-visible charge changes | **None** |
| Tax behavior changed | **No** |
| Historical invoice/payment behavior changed | **No** |
| Database/schema/migrations changed | **No** |
| Live environment verification | **Not performed** |
| Production readiness | **Not claimed** |

DB-36 and DB-37 remain separate unresolved issues and are outside this phase.
