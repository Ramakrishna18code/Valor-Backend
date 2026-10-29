# Pricing Catalogue Phase B2C — Readiness Audit

Audit date: 2026-10-29

Scope: `D:\RKKKK\Valor-Backend` and `D:\RKKKK\Valor-Admin-Portal` only.

This is a read-only audit. No application source, catalogue data, tests, database schema, migrations, or financial behavior was changed while performing the audit. The report describes the current working tree, including local changes that existed before the audit.

## 1. Executive summary

The approved JSON catalogue is a validated, backend-owned, read-only reference catalogue. It has schema version `1`, stable item codes, exact lookup, defensive item-list immutability, approval-gated snapshot creation, and separate unresolved tax-treatment metadata. The Admin Portal reads it through `GET /api/v1/admin/pricing-catalogue`; no catalogue mutation route or persisted Admin price editor was found.

The catalogue is not yet the source of payable amounts. Current service pricing remains in `PricingService`; AMC purchase checkout remains driven by legacy plan IDs and `CommerceService.amcPlanRate()`; renewal quotes call the legacy AMC formula; installation invoices use approved schedule JSON; generic invoices accept Admin-supplied financial fields; and payment/refund/settlement flows use persisted invoice/payment/provider values. Replacing any of these sources would be a financial migration, not a display-only mapping.

Highest-risk blockers are the semantic mismatch between current service formulas and fixed catalogue values, incompatibility between legacy AMC plans and the proposed annual packages, missing persisted catalogue snapshot fields on invoices/orders, and unresolved tax classification/inclusive-exclusive policy. DB-36 and DB-37 remain separate unresolved operational/database concerns and were not investigated or claimed resolved.

## 2. Working-tree status and limitations

Backend status observed before audit:

```text
 M docs/audits/PRICING-CATALOGUE-PHASE-B2A-MAPPING-AUDIT.md
 M src/main/java/com/valor/pricing/PricingCatalogue.java
 M src/main/resources/pricing/pricing-catalogue.json
 M src/test/java/com/valor/pricing/PricingCatalogueTest.java
```

Admin Portal status observed before audit:

```text
 M src/main.jsx
 M src/pricingCataloguePage.jsx
?? src/pricingCataloguePage.css
```

The requested report path did not exist. No existing B2C report was overwritten. No `.env`, credentials, tokens, private keys, database, Flyway, external service, or other repository was accessed.

This report is based on static source inspection and previously available focused-test evidence. No database or application-startup test was run for this audit.

## 3. Catalogue and snapshot implementation review

### Catalogue contract

`com.valor.pricing.PricingCatalogue` loads `pricing/pricing-catalogue.json` from the classpath in its `@Autowired` `ObjectMapper` constructor. `SUPPORTED_SCHEMA_VERSION` is `1`. The supported pricing types are `FIXED`, `RANGE`, `QUOTE_REQUIRED`, `AMC_FLOOR_RATE`, and `AMC_PACKAGE_RATE`.

Validation in `PricingCatalogue.validate(...)` enforces:

- schema version `1` and INR currency;
- the existing tax-treatment value `REQUIRES_BUSINESS_FINANCE_APPROVAL`;
- non-empty items and unique, nonblank codes;
- closed category, billing-unit, and lift-type sets;
- category/pricing-type compatibility;
- positive fixed amounts;
- valid ranges where ranges remain present in the schema;
- quote-only entries without fabricated amounts;
- AMC lift metadata and positive floor-band boundaries;
- no floor-band metadata on AMC package entries.

`immutableValidated(...)` copies the item list with `List.copyOf`. `read()` therefore exposes an unmodifiable item list. `findByCode(...)` is exact and case-sensitive; `requireByCode(...)` throws `PricingCatalogueException` when absent. `approvedForFinancialUse(...)` requires the exact loaded item instance and `APPROVED` status, preventing an externally constructed same-code item from being treated as approved.

The current JSON contains stable unique codes for all supplied catalogue entries. Price approval is represented by each item's `reviewStatus`; tax-policy status is represented separately by document-level `taxTreatment`. The approved prices therefore do not imply approved GST classification or rate.

### Snapshot safeguards

`com.valor.pricing.PricingSnapshot` is an immutable final value object with a private constructor. The supported creation path is `PricingSnapshot.from(...)`, which performs exact lookup and requires catalogue approval before construction.

Snapshot metadata includes catalogue code and schema version, display name, category, pricing type, billing unit, INR currency, fixed/range/final amount fields, lift type, floor-band minimum, price review status, tax treatment, tax basis/rate/amount, and quote reason/reference.

Validation rejects unsupported schema/currency/metadata, unapproved review status, incompatible category/pricing type, invalid AMC metadata, nonpositive amounts, out-of-range selected amounts, fabricated quote amounts, missing quote references, negative tax amounts, and determined tax metadata without a basis. A tax rate is rejected while the catalogue tax treatment remains unresolved.

The snapshot is not persisted and is not referenced by `Invoice`, `PaymentRecord`, `AmcPurchaseOrder`, renewal, installation, refund, or settlement entities. Consequently it cannot currently preserve catalogue history in financial records.

### Admin Portal behavior

`src/api/pricingCatalogue.js` exposes only `get()` for `/admin/pricing-catalogue`. `src/main.jsx` places Services & Pricing under Finance and renders it through the shared authenticated shell. `src/pricingCataloguePage.jsx` fetches backend data and provides search, category filtering, fixed/range/quote rendering, loading/error/empty states, and responsive presentation.

The tax toggle in `PricingCataloguePage` defaults to `INCLUDED`. Switching to `ON_TOP` only changes local display state and status text. It does not alter item amounts, submit an API request, persist configuration, calculate GST, or change invoice/checkout/payment data. The page explicitly says tax policy is pending.

No Add, Edit, Save, Approve, Publish, Deactivate, Delete, POST, PUT, PATCH, or DELETE catalogue operation was found. The backend controller `PricingCatalogueController` exposes only `GET /api/v1/admin/pricing-catalogue`; authorization is enforced by existing Admin/SuperAdmin security conventions and `AssetIdentityAccess.requireAdmin()`.

## 4. Existing financial-flow mapping

| Flow | Entry point | Pricing source and authority | Tax/persistence/payment linkage | Catalogue mapping and compatibility risk | Evidence |
|---|---|---|---|---|---|
| Routine maintenance | `CommerceController.serviceInvoice()` → `CommerceService.createServiceInvoice()` | `PricingService.servicePrice()` accepts `ROUTINE_MAINTENANCE`; manual base ₹999 or automatic base ₹1,499 plus ₹200 for each floor above five. | Invoice stores subtotal/tax/total/currency. Payment later uses persisted invoice/payment amount. | `PREVENTIVE_MAINTENANCE` is not an exact match: catalogue ₹1,200 has no lift-type/floor surcharge formula. Classification: `BUSINESS_DECISION_REQUIRED`; direct replacement could change charges. | `PricingService.servicePrice(...)`; `CommerceService.createServiceInvoice(...)`; `Invoice`; `PaymentRecord`. |
| Breakdown | Same service-invoice endpoint | Same `PricingService` formula as routine maintenance; service type changes eligibility, not the formula. | Existing invoice and payment linkage remains authoritative. | `NON_AMC_BREAKDOWN` or `STANDARD_BREAKDOWN_CALLOUT` are plausible but not semantically proven. Classification: `BUSINESS_DECISION_REQUIRED`. | `PricingService`; `CommerceService.calculatedAmount(...)`; commerce invoice tests. |
| Generic standalone invoice | `CommerceController.createInvoice()` → `CommerceService.createInvoice()` | Admin-provided `subtotal`, `taxAmount`, `totalAmount`, currency, and description are accepted under the current manual invoice contract; this is not catalogue lookup. | `Invoice` persists subtotal, tax, total, currency, status, dates. Payment may link to the invoice. | No automatic mapping. Classification: `NO_MAPPING`; forcing catalogue lookup would break manual invoice compatibility. | `CommerceController.createInvoice()`; `CommerceService.createInvoice(...)`; `CommerceDtos.InvoiceCreate`; `Invoice`. |
| AMC purchase | `CommerceController.amcPurchaseCheckout()` → `CommerceService.createAmcPurchaseCheckout()` | `amcPlanRate(...)` maps `essential`, `extended`, and `long-term` to legacy terms and subtotals ₹12,000, ₹22,000, and ₹32,000. Checkout applies 18% GST and creates the Razorpay amount from the calculated total. | `AmcPurchaseOrder` persists plan code/name, months/bonus months, subtotal/tax/total, currency, status and linked `PaymentRecord`; verified settlement creates invoice/contract. | Basic/Standard/Comprehensive annual proposals do not match legacy terms or amounts. Classification: `INCOMPATIBLE_WITHOUT_MIGRATION`; do not map automatically. | `CommerceService.createAmcPurchaseCheckout()` and `amcPlanRate(...)`; `AmcPurchaseOrder`; payment integrity tests. |
| AMC renewal quote | `CommerceController.quoteRenewal()` → `CommerceService.quoteRenewal()` | `calculatedAmount(null, contract)` calls `PricingService.amcPrice(lift)`: manual ₹14,999 or automatic ₹24,999 plus floor surcharge. Submitted quote amount is not the calculation source. | Renewal stores quoted amount/currency and optional invoice/payment. `applyRenewal(...)` requires successful customer-matching payment and invoice consistency. | Floor/package choice, annual terms, and legacy contract compatibility are unresolved. Classification: `BUSINESS_DECISION_REQUIRED`. | `CommerceService.quoteRenewal(...)`, `calculatedAmount(...)`, `PricingService.amcPrice(...)`, renewal entities. |
| Installation milestone | `InstallationMilestoneInvoiceController` → `InstallationMilestoneInvoiceService.generate()` | Amount and tax are read from approved installation schedule milestone JSON; amount must match the persisted milestone amount. | Native insert persists invoice subtotal/tax/total/currency and milestone link; eligibility and trigger checks apply. | Modernization catalogue entries are not installation schedules. Classification: `NO_MAPPING`; coupling would risk approved milestone history. | `InstallationMilestoneInvoiceService.generate()`; installation schedule/milestone models. |
| Payment-to-invoice association | Commerce payment/checkout/verification endpoints | Stored `PaymentRecord.amount` and linked invoice total are authoritative; checkout does not re-read JSON catalogue. | Razorpay verification/webhook/cash paths validate provider references, amount, currency, ownership, and linked obligations. | Future catalogue integration must snapshot before checkout and never recalculate from current JSON. Classification: `SAFE_CANDIDATE` only as a future upstream snapshot boundary, not current implementation. | `CommerceController`; `CommerceService.createRazorpayCheckout(...)`, verification, webhook, wallet and cash methods; `PaymentRecord`. |
| Refunds and settlement | `CommerceController` refund/reconciliation endpoints and webhook processing | Persisted payment/refund amounts and provider evidence are authoritative. | Refunds retain original payment/invoice values; settlement validates provider references and ownership. | No catalogue mapping. Repricing a refund from current catalogue would be unsafe. Classification: `NO_MAPPING`. | `CommerceService.requestRefund`, approval/reconciliation/webhook methods; `PaymentRefund`; payment integrity tests. |
| Tax/GST | AMC checkout and invoice/milestone paths | AMC purchase currently calculates `subtotal × 0.18`, rounded HALF_UP. Generic/manual and installation paths receive/store tax values from their request or approved schedule. | `Invoice`, `AmcPurchaseOrder`, and installation invoice persist tax amounts; no universal catalogue tax policy exists. | Catalogue tax treatment is unresolved; no safe universal mapping. Classification: `BUSINESS_DECISION_REQUIRED`. | `CommerceService` AMC purchase and invoice methods; `InstallationMilestoneInvoiceService`; `Invoice`; `AmcPurchaseOrder`. |
| Cash OTP and completion | `CommerceController` cash OTP endpoints; technician workflow endpoints elsewhere in commerce/workflow modules | Cash OTP verifies an existing payment context; job completion/report/OTP state is separate from financial settlement. | Successful cash verification changes payment state only after relationship, OTP, status, and context checks. | Catalogue must not be exposed to technicians or used to mark a completed job paid. Classification: `NO_MAPPING`. | `CommerceService.requestCashPaymentOtp`, `verifyCashPaymentOtp`, `cashContextMatches`; `CashPaymentOtp`; technician payment view. |

## 5. Catalogue mapping matrix

| Catalogue group or entry | Proposed later relationship | Classification | Reason |
|---|---|---|---|
| AMC floor rates | Potential future AMC product/rate table | `BUSINESS_DECISION_REQUIRED` | Floor-count semantics, annual terms, inclusions, tax basis, and interaction with packages are not established. |
| AMC Basic/Standard/Comprehensive | Separate future package proposals | `INCOMPATIBLE_WITHOUT_MIGRATION` | Legacy plan IDs, bonus months, terms, and amounts cannot be reinterpreted without changing existing checkout/contract behavior. |
| Standard/after-hours call-outs | Emergency service reference display | `BUSINESS_DECISION_REQUIRED` | Current `BREAKDOWN` flow has no after-hours classification and uses a floor-based formula. |
| Passenger entrapment | Case-assessment quote workflow | `SAFE_CANDIDATE` | Pricing type is explicitly quote-required, but a future approved quote workflow and persistence are still required. |
| Repairs/components | Service/repair reference catalogue | `UNVERIFIED` | Existing inventory/service taxonomy does not prove equivalence to catalogue codes. |
| Controller repair/replacement | Case-scoped repair reference | `BUSINESS_DECISION_REQUIRED` | Scope wording remains broad despite the selected reference amount. |
| Traction rope and motor/drive | Quotation-only repair workflow | `SAFE_CANDIDATE` | No fabricated price exists; requires explicit quote and approval capture. |
| Modernization entries | Separate modernization quotation workflow | `BUSINESS_DECISION_REQUIRED` | No current service-invoice or milestone mapping is established. |
| One-time inspection/maintenance | Potential standalone service mapping | `BUSINESS_DECISION_REQUIRED` | Similar names do not prove equivalence to current service types or formulas. |
| Payment/refund/settlement | Historical snapshot metadata only | `NO_MAPPING` | These flows must use persisted payment/refund obligations and provider evidence, not current catalogue data. |

## 6. Floor-count and G+ representation

`PricingService.floorCount(Lift)` reads the numeric `Lift.floorCount`, rejects null/negative values, and applies the existing surcharge above `BASE_FLOORS = 5`. The catalogue floor bands use `minFloor`/`maxFloor`, but no catalogue-to-billing selection algorithm exists.

The portal catalogue displays G+ labels from catalogue band metadata. The reviewed code does not establish a separate persisted G+ field or a customer input normalizer. Therefore the safe documented representation is:

- numeric `floorCount` remains authoritative;
- `G+0` is the ground-floor baseline;
- `G+1` is one floor above ground, with subsequent labels incrementing the same numeric concept;
- a G+ label is presentation only and must not independently calculate a price;
- existing floor bands are display metadata, not proof of a live floor selection algorithm.

No source evidence proves whether every existing stored `floorCount` means total served floors or floors above ground in all product contexts. That ambiguity blocks catalogue-backed floor selection. No new conversion or price calculation was introduced.

## 7. Legacy AMC compatibility

Legacy purchase IDs remain `essential`, `extended`, and `long-term`, with existing terms including durations/bonus months and hard-coded subtotals. The catalogue package entries are annual Basic, Standard, and Comprehensive proposals and have separate manual/automatic variants. Source does not establish a one-to-one mapping of names, inclusions, duration, bonus months, lift eligibility, or renewal behavior.

The safe future position is to display the package entries as separate proposals and retain legacy checkout/renewal calculations until a migration decision explicitly defines compatibility and historical treatment. Existing purchase orders, invoices, payment records, AMC contracts, and renewal requests must not be rewritten when catalogue data changes.

## 8. Tax toggle and tax-policy findings

| Concern | Current evidence | Readiness result |
|---|---|---|
| Price approval | JSON item `reviewStatus` values are `APPROVED`; snapshot factory gates on that status. | Price approval exists independently. |
| Tax classification/rate approval | Document tax treatment is `REQUIRES_BUSINESS_FINANCE_APPROVAL`; snapshot rejects a rate against this unresolved value. | Not ready for financial tax calculation. |
| Inclusive/exclusive presentation | Admin `taxMode` defaults to `INCLUDED`; `ON_TOP` only changes local text/status. | Presentation-only foundation; no legal tax conclusion. |
| Actual calculation | Existing AMC checkout still applies its legacy 18% calculation; catalogue toggle does not call it. | Separate current behavior; do not generalize 18% to catalogue items. |
| Financial persistence | Toggle is React state only; no API request or persisted setting. Existing invoices/orders persist their own tax fields. | No catalogue tax data is persisted into financial records. |

The toggle is therefore safe as a display preference but must not be described as a tax-policy approval, GST classification, or invoice calculation control.

## 9. Historical snapshot requirements

The current `PricingSnapshot` already carries in-memory catalogue code/schema, price review status, pricing type, billing unit, fixed/range/final amount, currency, lift type, floor-band minimum, quote reference, tax treatment, tax basis, tax rate, and tax amount. It does not carry a selection timestamp or persisted relationship to an invoice, payment, AMC purchase order, renewal request, or installation milestone.

A future persisted catalogue-backed financial record would minimally need:

- catalogue schema/version and stable code;
- display name/category/pricing type/billing unit;
- selected amount and currency;
- numeric floor input or approved band metadata where relevant;
- price approval status at selection time;
- quote reason/approval reference;
- tax-policy version/status and explicit tax basis;
- tax rate/amount only where approved policy supports them;
- selection timestamp and the resulting invoice/order/quote relationship.

These fields would require entity/schema/API migration. Until then, invoices, payments, refunds, AMC purchase orders, renewal quotes, and approved installation schedule amounts remain the historical authorities. A catalogue update must never recalculate or rewrite those records.

## 10. Test coverage and gaps

Existing focused protection includes:

- `PricingCatalogueTest`: catalogue loading, approval status, exact lookup, duplicate/metadata validation, quote-only behavior, fixed amounts, AMC metadata, snapshot safeguards, and immutability.
- `PricingCatalogueHttpSecurityTest`: Admin/SuperAdmin access, client-role denial, mutation-method denial, and generic Admin fallback behavior.
- Existing commerce/payment tests cover portions of invoice, checkout, provider-reference, refund, webhook, cash OTP, and settlement behavior.
- Admin Portal test suite covers shared navigation/API conventions and existing finance modules, but there is no dedicated catalogue component test proving the tax toggle is local-only.

Before any future billing integration, add focused tests for:

1. one exact mapping per selected service flow and rejection of ambiguous mappings;
2. catalogue version/price snapshot persistence and invoice/order immutability;
3. catalogue changes between quote, checkout, capture, refund, and settlement;
4. legacy AMC plan compatibility and renewal-term preservation;
5. quote-required amount/reference validation;
6. approved tax policy, inclusive/exclusive arithmetic, rounding, and tax persistence;
7. numeric floor-count/G+ normalization using domain-confirmed semantics;
8. Admin, customer, technician, anonymous, and mutation-route authorization;
9. Admin Portal rendering, toggle non-persistence, quote-only display, loading/error/search/filter states.

No database, Flyway, application-startup, or external-service test was run for this audit.

## 11. Recommended staged implementation plan

### Stage 1 — Contract and business gates

Confirm service taxonomy, floor-count convention, AMC package inclusions/durations, legacy-plan compatibility, exact Controller scope, quote workflow, tax classification, inclusive/exclusive policy, and rounding. Exclude every unresolved flow from billing integration.

### Stage 2 — Persisted snapshot foundation

Add the smallest schema/API representation for an immutable catalogue snapshot only after approval of the fields above. Test rollback by changing the JSON after an invoice/order/quote is created and proving the historical snapshot remains unchanged. Do not alter current amount formulas yet.

### Stage 3 — One fixed standalone service pilot

Choose one exact service with a semantic match and approved tax policy. Add server-side mapping, snapshot capture, amount-authority tests, client-contract compatibility tests, and a feature-gated rollback path. Exclude AMC, installation, refunds, and settlement changes.

### Stage 4 — Quote-required service pilot

Add explicit Admin quote approval/reference capture. Never turn a range or quote-only entry into a payable amount without a selected, approved amount and immutable snapshot. Exclude customer/technician catalogue browsing unless a separate contract is approved.

### Stage 5 — AMC decision and migration

Only after legacy-plan decisions, implement new package/floor products separately. Preserve legacy IDs and historical purchase/renewal records. Test checkout amount matching, invoice creation, settlement, renewal activation, and rollback independently.

### Stage 6 — Other flows

Evaluate modernization and installation separately. Do not map catalogue entries into approved installation schedules or generic manual invoices without explicit migration rules.

## 12. Blockers, decisions, and non-goals

Required owner decisions:

- floorCount/G+ semantics in every relevant product context;
- routine-maintenance and breakdown taxonomy/formula mapping;
- legacy AMC plan versus new package/floor product strategy;
- Controller scope and quotation rules;
- range/fixed reference meaning in customer-facing quotes;
- tax classification, rate, inclusive/exclusive policy, basis, and rounding;
- whether the catalogue is visible only to Admin or later to customers/technicians;
- historical snapshot persistence and approval/audit requirements.

Explicit non-goals of this audit:

- no catalogue-backed billing;
- no changes to `PricingService`, `CommerceService`, invoices, AMC purchase/renewal, installation, payments, refunds, settlement, GST, cash OTP, or technician completion;
- no database schema or migration changes;
- no DB-36/DB-37 resolution claim;
- no production-readiness claim.

## Readiness verdict

| Area | Status | Verdict |
|---|---|---|
| Catalogue foundation | READY FOR READ-ONLY REFERENCE | Validated, immutable, backend-owned, approved-price display catalogue exists. |
| Safe catalogue-to-billing mappings | NOT READY | Only future snapshot boundary and quote-only concepts are safe candidates; current payable flows have semantic mismatches. |
| Tax-policy readiness | BLOCKED | Tax classification and inclusive/exclusive policy are unresolved; current 18% AMC logic is not a universal catalogue policy. |
| Historical snapshot readiness | PARTIAL | In-memory snapshot safeguards exist, but financial persistence and invoice/order relationships require schema/API migration. |
| Overall billing-integration readiness | NOT READY | Business decisions, persisted snapshot design, one-flow pilot, and regression evidence are still required. |

