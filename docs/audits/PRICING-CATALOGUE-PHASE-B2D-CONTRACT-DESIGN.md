# Pricing Catalogue Phase B2D — Integration Contract and Test Foundation

Audit/design date: 2026-10-29

Scope: `D:\RKKKK\Valor-Backend` and `D:\RKKKK\Valor-Admin-Portal` only.

This phase is documentation-first. No catalogue-to-billing integration, source behavior, database schema, migration, financial calculation, or external service was changed.

## 1. B2C findings relevant to B2D

The Phase B2C audit established that the approved JSON catalogue is a backend-owned read-only reference catalogue, not the current source of payable amounts. Current service pricing remains in `PricingService`; legacy AMC purchase and renewal calculations remain in `CommerceService`/`PricingService`; installation invoices use approved schedule definitions; generic invoices retain their existing manual contract; and payment/refund/settlement flows use persisted obligations and provider evidence.

The catalogue and snapshot safeguards are sufficient for an isolated future selection boundary. A persisted financial snapshot is not yet possible without an entity/API/schema decision. Tax classification and inclusive/exclusive policy remain unresolved, so the Admin Portal tax toggle must remain presentation-only.

## 2. Existing safeguards reused

### `PricingCatalogue`

`com.valor.pricing.PricingCatalogue` loads `pricing/pricing-catalogue.json` from the classpath using its explicit `@Autowired ObjectMapper` constructor. It supports schema version `1` and pricing types `FIXED`, `RANGE`, `QUOTE_REQUIRED`, `AMC_FLOOR_RATE`, and `AMC_PACKAGE_RATE`.

`validate(...)` rejects unsupported schema/currency/tax-treatment values, empty or duplicate codes, unsupported categories/billing units/lift types, incompatible category/type combinations, invalid amounts/ranges, missing quote-only metadata, and invalid AMC floor/package metadata. The loaded item list is copied with `List.copyOf`.

`findByCode(...)` is exact and case-sensitive. `requireByCode(...)` fails closed for an unknown code. `approvedForFinancialUse(...)` requires the exact loaded item instance and `APPROVED` review status, so a caller cannot substitute an arbitrary same-code `Item`.

### `PricingSnapshot`

`com.valor.pricing.PricingSnapshot` is a final immutable value object with a private constructor. `PricingSnapshot.from(...)` is the supported creation path. It performs catalogue lookup and approval gating before validating the snapshot.

The existing snapshot carries catalogue code/schema version, display name, category, pricing type, billing unit, INR currency, fixed/range/final amount fields, lift type, floor-band minimum, price review status, tax treatment, tax basis/rate/amount, and quote reason/reference.

It rejects unapproved entries, unknown schema/currency/metadata, incompatible pricing metadata, invalid fixed/range/quote amounts, invalid AMC metadata, missing quote references, negative tax amounts, determined tax without a basis, and tax rates while the catalogue tax treatment is unresolved.

### HTTP and Portal boundary

`PricingCatalogueController` exposes only:

```text
GET /api/v1/admin/pricing-catalogue
```

It calls `AssetIdentityAccess.requireAdmin()`. `AuthConfig` explicitly allows the route for `ADMIN` and `SUPER_ADMIN`; the existing generic Admin fallback remains protected. `PricingCatalogueHttpSecurityTest` covers authorized Admin/SuperAdmin access, anonymous/client-role denial, unsupported mutation methods, and the generic fallback route.

`src/api/pricingCatalogue.js` exposes only a GET client method. `PricingCataloguePage` fetches catalogue data and keeps the tax mode in local React state. The default is `INCLUDED`; `ON_TOP` changes display state and explanatory text only. It makes no pricing mutation request, does not send a tax rate, and does not change invoices, checkout totals, or payment amounts.

## 3. Proposed future pricing-selection contract

No new production DTO is justified in B2D. The following is the contract to implement only when a specific billing flow has passed its business and tax approval gates.

### Selection request

The future request should contain only selection inputs, not an amount supplied by an untrusted client:

| Field | Authority/requirement |
|---|---|
| `catalogueCode` | Required exact stable code; resolved by the backend catalogue. |
| `catalogueSchemaVersion` | Optional request consistency check; backend-loaded version remains authoritative. |
| `liftType` | Required only where the selected catalogue item requires it; must match the loaded item. |
| `floorCount` or approved floor-band metadata | Numeric domain input only where the approved flow requires it; no G+ string or new conversion algorithm becomes authoritative. |
| `finalApprovedQuoteAmount` | Required only for an approved range/quote workflow; must be selected by an authorized server/Admin quote process and validated against the item. |
| `quoteReasonOrApprovalReference` | Required for quotation-only items. |
| tax inputs | Must not be client-selected until tax-policy metadata is explicitly approved. |

The client must not provide authoritative fixed price, tax rate, tax amount, subtotal, total, plan mapping, or payment amount. The backend resolves the item, approval state, pricing type, currency, billing unit, and catalogue version.

### Selection result

A future result should expose a validated selection/snapshot containing:

- stable catalogue code and loaded schema version;
- item name/category, pricing type, billing unit, and INR currency;
- price approval status at selection time;
- selected fixed amount, or validated final quote amount with range metadata;
- lift type and numeric floor/band metadata where relevant;
- quote reason/approval reference where relevant;
- tax-policy status and basis; tax rate/amount only after explicit policy approval;
- selection timestamp;
- an explicit indication that the result is a quote/reference or an authoritative financial selection.

The current in-memory `PricingSnapshot` covers most value fields but does not contain selection time, caller/approver identity, or a persisted invoice/order/quote relationship.

### Authorized caller

The future selector must be called only from a backend-owned flow or an explicitly authorized Admin quote workflow. Customers and technicians must not choose catalogue codes or amounts to determine payable totals. The current Admin catalogue GET route is visibility-only and must not be repurposed as a selection mutation.

## 4. Required validation and rejection behavior

| Attempt | Expected behavior | Current evidence |
|---|---|---|
| Unknown code | Reject with a safe not-found/invalid-selection error; never fall back to a legacy or guessed price. | `requireByCode(...)` throws `PricingCatalogueException`; focused tests cover missing codes. |
| Unapproved item | Reject authoritative selection. | `approvedForFinancialUse(...)` and `PricingSnapshot.from(...)` gate on exact `APPROVED`. |
| Quote-only item supplied as fixed amount | Reject; require an approved final quote and nonblank reason/reference. | Snapshot validation requires positive final amount and quote reference, with no fixed/range fields. |
| Missing/invalid lift metadata | Reject for AMC entries; reject lift metadata on non-AMC entries. | Catalogue and snapshot validation cover lift type and AMC metadata. |
| Missing/invalid floor metadata | Reject invalid AMC floor-band boundaries; do not infer a customer floor. | Positive `floorBandMinimum` and catalogue band validation exist. |
| Invalid amount/currency | Reject nonpositive amounts, out-of-range selections, and non-INR currency. | Catalogue and snapshot validation cover these cases. |
| Incompatible type/metadata | Reject category/type mismatch, AMC package floor metadata, and fixed/range field conflicts. | `categoryMatchesPricingType(...)` and snapshot validation cover these cases. |
| Unresolved tax policy | Block tax-dependent authoritative use; do not supply a rate or default GST. | `REQUIRES_BUSINESS_FINANCE_APPROVAL` is retained; tax rate is rejected against it. |
| Arbitrary approved snapshot | Must be impossible through supported construction. | Snapshot constructor is private; factory requires the loaded item instance and approval. |

The current catalogue approval status means an item may be displayed and may produce an in-memory approved-price snapshot, but that does not authorize use in an invoice, checkout, AMC order, renewal, or payment. Tax approval remains a separate gate.

## 5. Snapshot and historical-record contract

### Already available in memory

`PricingSnapshot` already has code/version, display metadata, type/unit, currency, selected/final amount fields, lift/floor-band metadata, review status, quote reference, and tax metadata fields with fail-closed validation.

### Required for persisted financial history

The smallest future persisted record should additionally retain:

- selection timestamp;
- selector/approver identity where the workflow requires it;
- catalogue version and stable code;
- price approval status at selection time;
- exact selected amount and currency;
- numeric floor input or band metadata actually used;
- quote approval/reference data;
- tax-policy version/status, explicit basis, and rate/amount only when approved;
- relationship to the resulting invoice, AMC order, renewal quote, or other obligation;
- a way to distinguish reference display from authoritative financial selection.

These fields do not exist as persisted catalogue snapshot columns on `Invoice`, `PaymentRecord`, `AmcPurchaseOrder`, renewal, or installation entities. Adding them would require a future entity/schema/API migration and historical backfill decision. B2D does not add them.

Historical invariants remain:

1. Existing invoice, order, payment, refund, receipt, settlement, and approved-quote values remain authoritative.
2. A later JSON change never recalculates or rewrites an existing record.
3. Payment checkout, provider verification, webhook, refund, cash OTP, and settlement continue to validate against persisted obligations.
4. A failed catalogue lookup or missing approved policy fails closed; it does not silently use an obsolete catalogue or legacy amount inside a future catalogue-integrated flow.

## 6. Tax-policy separation

The five concepts remain separate:

1. Catalogue price approval: item `reviewStatus`, currently `APPROVED`.
2. Tax classification/rate approval: document tax treatment remains `REQUIRES_BUSINESS_FINANCE_APPROVAL`.
3. Presentation: Admin Portal `taxMode` is local state, defaulting to `INCLUDED`; `ON_TOP` does not calculate or add a value.
4. Actual calculation: current AMC purchase code independently applies its existing 18% calculation. That implementation is not a universal catalogue tax policy.
5. Persistence: existing invoices/orders persist their own tax values; the Portal toggle does not persist or alter them.

The future financial-selection contract must reject or explicitly block tax-dependent use until an authoritative policy supplies classification, rate, basis, inclusive/exclusive semantics, and rounding. No 18% default is allowed.

## 7. Current flow blockers

| Flow | B2D status | Blocker |
|---|---|---|
| Routine maintenance/breakdown service invoices | Blocked | `PricingService` uses lift type, floor count, bases ₹999/₹1,499, and a ₹200 surcharge; catalogue fixed references are not an exact formula match. |
| Generic standalone invoices | No mapping | Current Admin DTO accepts manual financial values; no catalogue association exists. |
| AMC purchase | Blocked | `essential`/`extended`/`long-term` IDs, terms, bonus months, and legacy totals cannot be mapped silently to annual packages. |
| AMC renewal | Blocked | Renewal quote uses the legacy AMC formula and contract context; package/floor choice and terms are unresolved. |
| Installation milestones | No mapping | Approved schedule JSON and milestone amount/tax are authoritative; catalogue modernization entries are not schedules. |
| Payment/refund/settlement | Preserve current authority | These flows must use persisted obligations/provider evidence and historical snapshots, never current catalogue lookup. |
| Cash OTP/job completion | No mapping | Payment verification and job completion are separate workflows. |
| Quote-only services | Future safe candidate | Requires explicit quote approval/reference and persisted snapshot before any payable use. |

## 8. Test foundation and gaps

Existing isolated coverage is sufficient for the current selection boundary:

- `PricingCatalogueTest`: loading, approved entries, exact lookup, duplicate/metadata rejection, quote-only entries, fixed amount validation, AMC metadata, tax separation, immutability, and arbitrary-item rejection.
- `PricingCatalogueHttpSecurityTest`: Admin/SuperAdmin authorization, anonymous/customer/technician denial, mutation-method rejection, and generic Admin fallback protection.

Before future integration, add tests at the first flow-specific phase for:

- backend selection with exact code and server-resolved amount;
- all required lift/floor inputs and domain-confirmed floor semantics;
- quote-only approval/reference persistence;
- approved tax policy and inclusive/exclusive arithmetic;
- immutable invoice/order snapshot persistence;
- catalogue changes between quote, checkout, capture, refund, and settlement;
- legacy AMC IDs and terms remaining compatible;
- customer/technician/client attempts to submit prices or bypass selection;
- Admin Portal tax toggle remaining local-only and not invoking a financial API.

No database, Flyway, application-startup, external-service, or provider test was run for B2D.

## 9. Exact files changed

Only this new report is changed by B2D:

```text
docs/audits/PRICING-CATALOGUE-PHASE-B2D-CONTRACT-DESIGN.md
```

No existing source, test, catalogue, configuration, entity, migration, or prior audit file was modified by this phase. The working-tree modifications listed in the B2C audit were preserved.

## 10. Recommended next phase

The next phase should not begin with AMC or existing service repricing. First obtain owner/finance decisions for:

1. service taxonomy and whether any fixed catalogue entry is semantically identical to one current flow;
2. floorCount/G+ semantics and approved floor-band selection rules;
3. legacy AMC versus new package/floor product compatibility;
4. Controller scope and quote workflow;
5. tax classification, rate, basis, inclusive/exclusive treatment, and rounding;
6. persisted snapshot fields and historical/audit ownership.

After those gates, the safest implementation is one narrow fixed service pilot with a persisted immutable snapshot and no simultaneous AMC, installation, refund, or settlement changes. The pilot must have server-authoritative amount matching, rollback/feature gating, and invoice/payment immutability tests.

## 11. Remaining blockers and verdict

### Domain blockers

- No approved catalogue-to-current-service mapping is exact enough to replace current charges safely.
- AMC package/floor proposals are not compatible by implication with legacy IDs, terms, or renewal logic.
- Floor-count/G+ semantics are not proven for every product context.
- Tax policy is unresolved and must not be inferred from the current AMC 18% implementation.
- Persisted historical snapshot representation is not yet implemented.

### Readiness verdict

| Area | Status |
|---|---|
| Existing catalogue selection safeguards | READY FOR ISOLATED FOUNDATION |
| New production selection endpoint | NOT IMPLEMENTED — intentionally deferred |
| Safe catalogue-to-billing mapping | BLOCKED |
| Tax-dependent financial selection | BLOCKED |
| Historical persisted snapshot | NOT READY |
| Overall billing integration | NOT READY |

DB-36 and DB-37 remain separate unresolved issues. This report does not claim production readiness.
