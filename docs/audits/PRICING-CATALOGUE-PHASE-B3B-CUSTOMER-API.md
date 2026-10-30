# Pricing Catalogue Phase B3B — Customer API Implementation

Implementation date: 2026-10-30

## 1. Executive summary

Implemented the authenticated, read-only customer pricing catalogue endpoint:

`GET /api/v1/customer/pricing-catalogue`

The endpoint publishes exactly the B3A allowlist: 24 fixed-reference entries and 3 quote-required entries. The 13 AMC entries are excluded. Publication is implemented as an explicit stable-code allowlist, not as a category or pricing-type filter. Unknown, newly added, missing, or unapproved entries cannot become customer-visible by default; missing or malformed required entries fail closed.

The endpoint returns a dedicated customer DTO. It does not return `PricingCatalogue.Item` or `CatalogueDocument`, does not expose review/tax-treatment metadata, does not expose lift/floor fields or original ranges, and does not accept a price or any mutation request. No invoice, tax, payment, AMC, refund, settlement, installation, database, or migration behavior was changed.

## 2. Endpoint and HTTP method

```text
GET /api/v1/customer/pricing-catalogue
```

The response uses the existing `ApiResponse` envelope and contains catalogue version, INR currency, a reference-price disclaimer, a tax-policy disclosure, and the allowlisted items in deterministic code order.

## 3. Authentication and authorization

The permitted role is the existing application role `CUSTOMER`, enforced by:

- Spring Security matcher: `.requestMatchers("/api/v1/customer/pricing-catalogue").hasRole("CUSTOMER")`.
- Controller identity check: `AssetIdentityAccess.requireActiveCustomerUser(AssetIdentityAccess.actor())`.

Anonymous requests are rejected with 401. `TECHNICIAN`, `ADMIN`, and `SUPER_ADMIN` are rejected with 403. Admin/Super Admin access is intentionally not granted on the customer route; the existing `/api/v1/admin/pricing-catalogue` route remains unchanged and remains authorized for Admin/Super Admin.

Only `GET` is mapped. POST, PUT, PATCH, and DELETE have no mutation handlers and return 405; they cannot alter catalogue state.

## 4. Exact publication allowlist

### 24 fixed-reference entries

`STANDARD_BREAKDOWN_CALLOUT`, `AFTER_HOURS_EMERGENCY_CALLOUT`, `DOOR_SENSOR_ADJUSTMENT`, `DOOR_ROLLER_REPLACEMENT`, `LANDING_DOOR_LOCK_REPLACEMENT`, `PUSH_BUTTON_REPLACEMENT`, `EMERGENCY_ALARM_REPAIR`, `ARD_BATTERY_REPLACEMENT`, `CONTACTOR_REPLACEMENT`, `LIFT_WIRING_REPAIR`, `DOOR_OPERATOR_REPAIR`, `CONTROLLER_REVIEW`, `PARTIAL_MODERNIZATION`, `CONTROLLER_ELECTRICAL_UPGRADES`, `CABIN_RENOVATION`, `GENERAL_LIFT_INSPECTION`, `PREVENTIVE_MAINTENANCE`, `NON_AMC_BREAKDOWN`, `REPAIRS`, `DOOR_ALIGNMENT_ADJUSTMENT`, `LIFT_LEVELING_ADJUSTMENT`, `ELECTRICAL_FAULT_DIAGNOSIS`, `DETAILED_LIFT_SAFETY_INSPECTION`, and `MAINTENANCE_AUDIT_REPORT`.

Their positive `referenceAmount` values are read from the loaded validated JSON catalogue. No price table is duplicated in the service.

### 3 quote-required entries

`PASSENGER_ENTRAPMENT_RESPONSE`, `TRACTION_ROPE_REPLACEMENT`, and `MOTOR_OR_DRIVE_REPLACEMENT` are returned with `displayMode=QUOTE_REQUIRED`, `quoteRequired=true`, and `referenceAmount=null`. No zero, range, midpoint, estimate, or fabricated amount is returned.

### 13 excluded AMC entries

`AMC_FLOOR_MANUAL_G1_G2`, `AMC_FLOOR_MANUAL_G3_G4`, `AMC_FLOOR_MANUAL_G5_G6`, `AMC_FLOOR_MANUAL_G7_G10`, `AMC_FLOOR_AUTO_G1_G4`, `AMC_FLOOR_AUTO_G5_G6`, `AMC_FLOOR_AUTO_G7_G10`, `AMC_PACKAGE_BASIC`, `AMC_PACKAGE_BASIC_AUTO`, `AMC_PACKAGE_STANDARD`, `AMC_PACKAGE_STANDARD_AUTO`, `AMC_PACKAGE_COMPREHENSIVE`, and `AMC_PACKAGE_COMPREHENSIVE_AUTO` remain Admin-only references.

## 5. Public response DTO and field disclosure

The dedicated `CustomerPricingCatalogueResponse` contains:

- `catalogueVersion` and `currency` at the response level;
- `priceDisclaimer` and `taxDisclosure` at the response level;
- item `code`, `category`, `name`, safe customer-facing `description`, `displayMode`, `referenceAmount`, `quoteRequired`, and `billingUnit`.

The DTO excludes `reviewStatus`, internal `taxTreatment`, original `minAmount`/`maxAmount`, range-selection rationale, lift type, floor metadata, approval notes, and implementation metadata. Fixed descriptions are generated as reference wording; quote descriptions explicitly require assessment and an approved quotation.

The service reads the validated `PricingCatalogue`, exact-matches the allowlist codes, sorts them, verifies approval and pricing shape, and fails closed if a required code is missing or invalid. Catalogue entries outside the allowlist are never projected.

## 6. Response examples

Fixed reference:

```json
{
  "code": "STANDARD_BREAKDOWN_CALLOUT",
  "category": "EMERGENCY_SERVICE",
  "name": "Standard breakdown call-out",
  "description": "Listed reference price for Standard breakdown call-out. The final approved service amount may vary after assessment.",
  "displayMode": "REFERENCE_FIXED",
  "referenceAmount": 2000,
  "quoteRequired": false,
  "billingUnit": "PER_JOB"
}
```

Quote-required:

```json
{
  "code": "PASSENGER_ENTRAPMENT_RESPONSE",
  "category": "EMERGENCY_SERVICE",
  "name": "Passenger entrapment response",
  "description": "Quotation required. We will assess the service and confirm the final approved amount before payment.",
  "displayMode": "QUOTE_REQUIRED",
  "referenceAmount": null,
  "quoteRequired": true,
  "billingUnit": "PER_JOB"
}
```

## 7. Tax disclaimer

The endpoint returns:

`Applicable taxes and the final payable amount will be confirmed in the approved quotation or invoice.`

It does not claim GST inclusion or exclusion, calculate GST, add tax, or reuse the Admin Portal’s local presentation toggle.

## 8. Error behavior

- Authentication and authorization failures use the existing security behavior.
- Unsupported mutation methods return 405.
- A missing, unapproved, or malformed required allowlist item throws a safe server-side publication failure rather than substituting an old price or another item.
- The endpoint has no request body and accepts no client-supplied amount.

## 9. Tests and checks

Focused command run from `D:\RKKKK\Valor-Backend`:

```text
mvn -q "-Dtest=PricingCatalogueHttpSecurityTest,CustomerPricingCatalogueServiceTest,CustomerPricingCatalogueHttpSecurityTest" test
```

Exit code: `0`.

Surefire summary: 14 tests, 0 failures, 0 errors, 0 skipped:

- `CustomerPricingCatalogueServiceTest`: 2 passed.
- `CustomerPricingCatalogueHttpSecurityTest`: 2 passed.
- `PricingCatalogueHttpSecurityTest`: 3 passed.
- `PricingCatalogueTest`: 7 passed.

The focused tests used mocked HTTP dependencies and the classpath catalogue only. No database, SQL, Flyway, application-startup, external-service, or payment-provider test was run.

`git diff --check`:

- Backend: exit code 0.
- Admin Portal: exit code 0.

## 10. Exact changed files

Backend source and tests:

```text
src/main/java/com/valor/auth/AuthConfig.java
src/main/java/com/valor/pricing/CustomerPricingCatalogueController.java
src/main/java/com/valor/pricing/CustomerPricingCatalogueResponse.java
src/main/java/com/valor/pricing/CustomerPricingCatalogueService.java
src/test/java/com/valor/pricing/CustomerPricingCatalogueHttpSecurityTest.java
src/test/java/com/valor/pricing/CustomerPricingCatalogueServiceTest.java
```

Documentation:

```text
docs/audits/PRICING-CATALOGUE-PHASE-B3B-CUSTOMER-API.md
```

The pre-existing untracked B3A report was preserved. No Admin Portal file was modified.

## 11. Financial isolation confirmation

No code in `PricingService`, `CommerceService`, invoice creation, AMC purchase or renewal, installation milestone billing, tax/GST calculation, payment/Razorpay, refund, settlement, receipt, cash OTP, technician workflow, or job completion was changed. The customer catalogue endpoint is not called by invoice creation, checkout, payment settlement, or refund logic. Existing persisted invoice and payment values remain authoritative. Customer-submitted amounts are not accepted.

No database schema, migration, database state, provider state, DB-36, or DB-37 behavior was changed.

## 12. Remaining blockers and next phase

The endpoint is reference publication only. Tax policy, AMC package/floor mapping, service eligibility mapping, persisted financial snapshots, and DB-36/DB-37 remain unresolved. The next phase must not connect this endpoint to invoice creation, checkout, customer charges, or payment settlement without separate approval and financial-isolation evidence.
