# Payment integration PDF verification

## Executive conclusion

The four-page `Valor_Payment_Integration_Missing_Items_Report.pdf` was available at `C:\Users\pandu\Downloads\Valor_Payment_Integration_Missing_Items_Report.pdf` and was inspected directly. It is a pre-DB-01–DB-33 review checklist generated 21 October 2026, not a source-code or test report. Its “Not tested” labels are therefore stale for several controls, but the document remains useful for unresolved provider-recovery, operations, and product questions.

The PDF is **partly accurate and materially outdated as a description of current gaps**. Current code and recorded evidence establish payment-context validation, ownership protection, refund controls, webhook idempotency, cash OTP gates, mode validation, focused checkout/refund tests, and a bounded diagnostic reconciliation scan. They do not establish automatic recovery after an ambiguous remote order creation, physical Hikari retirement after a real failed `RELEASE_LOCK()`, production infrastructure controls, or production-scale reconciliation/settlement automation.

Evidence labels in this report remain separate: mocked/service-level tests, H2/test-database tests, recorded MySQL/Spring integration runs, source inspection, and operations evidence are not interchangeable. Historical counts are not a new combined execution.

## Finding verdict matrix

| PDF section / priority | Verdict | Current evidence and behavior | Residual risk / next action |
|---|---|---|---|
| P0 Payment idempotency | **Implemented, but evidence incomplete** | `CommerceService.createRazorpayCheckout()` locks the invoice, rejects paid/void/cancelled invoices, reuses `PROCESSING` rows with a saved order ID, blocks uncertain attempts, and blocks a successful payment. `payment_records` has unique provider references/order IDs in the migrations. `CommercePaymentIntegrityTest` includes `repeatedCheckoutReusesConfirmedOrderAndSuccessfulPaymentBlocksNewCheckout`, `uncertainOrderAttemptRemainsProtectedAndDefinitiveRejectionCanRetry`, and `missingProviderOrderIdAfterAmbiguousCreationStaysBlockedWithoutBlindRetry`. | No provider order-creation idempotency or receipt lookup is established. A remote order can exist without its local order ID. Provider integration owner must define recovery or accept manual review. |
| P0 Checkout response verification | **Not established** | No separate client checkout-response verification route was found in `CommerceController`; settlement is driven by signed webhooks or Admin reconciliation. `CommerceService.razorpayWebhook()` verifies the webhook signature, which is a different path. | This may be intentional architecture, not a confirmed defect. If client checkout signature verification is required, add an authorized contract and test; do not equate webhook verification with client-response verification. |
| P0 Webhook recovery and ordering | **Implemented, but evidence incomplete** | `CommerceService.razorpayWebhook()` validates signature/event ID, claims under MySQL advisory locking, re-reads current state, uses transaction-completion release, and protects duplicate/concurrent events. `razorpay_webhook_events.event_id` is unique. `WebhookMysqlSpringLifecycleTest` recorded 6 MySQL/Spring lifecycle passes; service tests cover stale/mismatched/duplicate capture, rollback retry, and provider evidence matching. | Physical Hikari retirement after an actual failed `RELEASE_LOCK()` remains unproven. Out-of-order behavior is bounded by current-state and monotonic settlement rules, not proof of every provider event sequence. |
| P0 Authorization and invoice ownership | **Implemented and adequately evidenced at tested scope** | `CommerceService.authorized()` and customer/profile ownership checks protect payment, invoice, refund, receipt, and refund-document reads. `CommercePaymentIntegrityTest` covers cross-customer payment/invoice/refund history, receipt access, mismatched invoice relationships, and customer refund ownership. `Phase1CommerceTest` covers cross-customer payment/invoice access and checkout denial. | Broader production authorization still depends on route/service composition; keep endpoint-level regression coverage when adding routes. |
| P0 Amount and currency validation | **Implemented and adequately evidenced at tested scope** | Invoice-backed payments use persisted invoice total/currency and reject conflicting service/AMC/customer context. Refunds reject zero, excessive precision, non-INR, and amounts above remaining balance. Webhook/reconciliation paths compare provider references, amount, currency, and invoice/payment context. | Mocked/service evidence does not prove every provider payload shape or production data anomaly. |
| P0 Database transaction safety | **Partially implemented** | Payment/refund transactions, pessimistic locks, payment-before-refund lock order, V56 provider key persistence, webhook rollback/retry, and bounded concurrency controls exist. `CommercePaymentIntegrityTest` and `WebhookMysqlSpringLifecycleTest` cover important service and MySQL/Spring cases. | A remote provider success followed by lost local commit remains ambiguous. The recorded tests do not prove every database failure timing, deadlock, or production race. |
| P0 Production configuration and secrets | **Implemented, but evidence incomplete** | `RazorpayGatewayModeTest` recorded 6 passes for explicit `RAZORPAY_MODE`, key-prefix pairing, and invalid configuration. `CommerceService` fails closed for missing/invalid `VALOR_REFUNDS_SAME_ADMIN_THRESHOLD`; `RazorpayGateway` reads environment/configuration values rather than source constants. Offline mode is separate from Razorpay Test Mode. | TLS, HTTPS webhook delivery, secret rotation, least privilege, deployment separation, and live credential handling require operations evidence. No live credentials were inspected. |
| P1 Failure and retry UX | **Implemented, but evidence incomplete** | Local payment states distinguish `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `REFUNDED`, and `PARTIALLY_REFUNDED`. Ambiguous checkout/refund outcomes remain blocked or unresolved; blind retries are rejected. Client rendering is outside this repository. | Frontend messaging and user experience are not verified here. Product/client owners must verify pending/manual-review presentation. |
| P1 Customer/admin notifications | **Partially implemented** | Payment-result email events and persisted communication/in-app notification infrastructure exist; event idempotency is supported. Payment/refund rows and audit records preserve financial state. | Actual email/SMS/push delivery, overdue notifications, and the PDF’s proposed notification set are not fully established. A persisted message is not delivery proof. Product must confirm required notification events. |
| P1 Receipt and tax details | **Partially implemented** | `GET /api/v1/payments/{id}/receipt.pdf` and refund PDF generation exist with ownership checks. Invoice amounts, currency, status, number, and immutable history are persisted. Tests cover successful receipt content, unsettled rejection, ownership, refund totals, and PDF-only format. | No repository-established GST/tax policy, legal invoice treatment, merchant identity, or tax calculation policy was found. Owner/legal/finance confirmation is required. |
| P1 Reconciliation operations | **Implemented, but evidence incomplete** | Admin payment/refund reconciliation routes verify stored provider references and leave missing, pending, conflicting, or unavailable evidence unresolved. The diagnostic issue scan now uses ascending-ID keyset batches of 200; focused `CommercePaymentIntegrityTest` coverage verifies multi-batch traversal without `payments.findAll()`. | The scan is not a settlement worker and has no scheduled cadence, alerting, or production-scale performance proof. Per-payment reconciliation remains explicit; backend/data owners still need representative-data and operational evidence. |
| P1 Refund controls and audit | **Implemented and adequately evidenced at tested scope** | Refund requests validate status, INR, amount, remaining balance, and active-request duplication. Approval locks payment before refund, requires a distinct second Admin above `VALOR_REFUNDS_SAME_ADMIN_THRESHOLD`, persists approval actors/reasons/timestamps, and stores V56 `provider_idempotency_key`. Unknown/provider-conflict outcomes remain `PROCESSING`. Tests cover threshold, lock order, stable key, unknown outcome, and provider conflict. | Provider-side behavior and notification delivery remain external/operational evidence. |
| P1 Cash collection controls | **Implemented, but evidence incomplete** | Cash OTP creation is customer/invoice scoped; verification is restricted to technician/Admin participants and validates payment, invoice, customer, request, OTP state, expiry, attempts, lock, and persisted context. Successful verification alone settles cash and invoice. `Phase1CommerceTest` covers unauthorized actors, wrong/expired/locked OTP, and no false payment; `CommercePaymentIntegrityTest` covers context and reassignment cases. | Recorded tests are application/H2 or service-level for some cases; no live cash reconciliation or operational collection process is established. |
| P2 Finance reporting | **Partially implemented** | Admin reports include revenue, payments, invoices, services, customers, and technicians; transaction list/detail and CSV routes exist. `Phase1CommerceTest` covers report authorization, filters, date validation, summaries, and CSV content type. | The exact PDF metrics such as gateway fees, overdue/cash-versus-online dimensions, and scalable large-data behavior are not all established. Some list/report queries may be unbounded; product scope and representative EXPLAIN evidence are required. |
| P2 Automated testing and monitoring | **Implemented, but evidence incomplete** | Focused payment, refund, mode, webhook, cash, authorization, checklist, and parts tests exist. Historical DB evidence includes 66 payment-integrity, 6 mode, 6 webhook, and related MySQL/Spring results. | No production alerting, anomaly monitoring, backup/recovery drill, or live provider test evidence exists in this repository. |

## Valor-specific workflows

| PDF recommendation | Verdict | Verification |
|---|---|---|
| Recurring AMC payments/history/due dates/reminders | **Partially implemented** | `amc_contracts`, `amc_renewal_requests`, invoices, payment links, renewal status, and one-time settlement activation exist. Recurring billing automation and a complete due/balance/reminder policy are not established. |
| Repair/breakdown advances, quotations, final invoices, additional charges | **Partially implemented / business decision required** | Service invoices and payment links exist, but the PDF’s full advance/quotation/change-order policy is not established as a complete contract. Product/finance must define required stages. |
| Technician cash collection history | **Partially implemented** | Cash payment, technician verification, OTP, payment reference, and audit state exist. A dedicated collection-history/reconciliation workflow is not established. |
| Admin finance dashboard with proposed filters/metrics | **Partially implemented / optional or scope-dependent recommendation** | Admin reports and transaction endpoints exist, but the PDF’s exact dashboard and filter set is not a confirmed product requirement or complete implementation. |

## API, database, and operations checklist

- `/api/v1/payments/{id}/status` is `PUT`, restricted to Admin/SuperAdmin by `AuthConfig`; gateway-backed payments cannot be manually marked successful by that route.
- Payment/order references and webhook event IDs have database uniqueness constraints; V56 adds `payment_refunds.provider_idempotency_key` with `uk_payment_refunds_provider_idempotency`.
- Payment/invoice/refund status transitions are service-validated; exact state coverage is not equivalent to a formal complete state-machine proof.
- Audit actor/reason/timestamp data exists for relevant approval and settlement paths, but the PDF’s desired audit taxonomy is not a separately verified requirement.
- Payment/invoice lists use pageable routes in the commerce controller, and the diagnostic reconciliation issue scan uses an ascending-ID keyset cursor with batches of 200. Per-payment reconciliation remains locked and explicit.
- Scheduled reconciliation, alerting, backups, restore procedures, RPO/RTO, TLS, and credential rotation are **Operations verification required**.

## Pre-production test status

The PDF’s 12 scenarios were marked “Not tested.” Current evidence changes that status as follows; no tests were executed during this audit.

| Scenario | Current evidence | Strength and remaining gap |
|---|---|---|
| 1. Successful online payment | `Phase1CommerceTest` webhook capture flow; `CommercePaymentIntegrityTest.verifiedCaptureReconcilesPaymentAndInvoiceIdempotently` | Mocked/H2 or service-level plus recorded integration evidence; not live Razorpay. |
| 2. Customer closes checkout | `pendingAndFailedPaymentViewsDoNotImplySuccessfulSettlement`; `uncertainOrderAttemptRemainsProtectedAnd...` | Local safety is tested; actual client-abandonment/provider behavior is not. |
| 3. Duplicate checkout/payment request | `repeatedCheckoutReusesConfirmedOrderAndSuccessfulPaymentBlocksNewCheckout`; invoice/payment uniqueness and locks | Strong local behavior evidence; remote provider order idempotency remains unproven. |
| 4. Duplicate webhook | Webhook lifecycle tests and `duplicateCapturedEventDoesNotApplyDifferentInvoiceEffect` | Recorded MySQL/Spring plus service evidence; physical failed-release retirement gap remains. |
| 5. Wrong amount/currency | `captureAmountMismatchDoesNotPayInvoice...`; invoice/refund amount and currency tests | Service/mock evidence; provider payload diversity and production data are not covered. |
| 6. Provider success with client timeout | `missingProviderOrderIdAfterAmbiguousCreationStaysBlockedWithoutBlindRetry` | Correctly demonstrates safe blocking, not automatic recovery. |
| 7. Refund above remaining balance | `invalidAndExcessiveRefundAmountsAreRejectedBeforePersistence` | Service-level evidence; no live provider call. |
| 8. Refund above threshold | `amountAboveProposedBoundaryRequiresDistinctSecondAdmin`, `highValueRefundRequiresDistinctSecondAdmin` | Service-level evidence plus fail-closed configuration tests. |
| 9. Unauthorized invoice access | `customerCannotReadAnotherCustomersPaymentInvoiceOrRefundHistory`, receipt/document tests, `Phase1CommerceTest` cross-customer checks | Strong tested ownership cases; retain endpoint regression coverage. |
| 10. Invalid/expired cash OTP | `Phase1CommerceTest.wrongCashOtpAttemptsPersistAndLockWithoutPayingInvoice`; cash context tests | Application/H2 and service evidence; no live operations proof. |
| 11. Live credentials in test mode | `RazorpayGatewayModeTest` recorded 6 passes | Unit/configuration evidence; no live secret or provider test. |
| 12. Database failure during settlement | Webhook rollback/retry and controlled failure tests | Important bounded evidence, but not proof of every commit-failure timing after a remote success. |

## Confirmed gaps and decisions

1. **Ambiguous checkout recovery:** local `PROCESSING` and retry blocking reduce risk, but provider order creation can succeed before the local order ID is committed. Provider-supported idempotency/lookup or an approved manual procedure is required.
2. **Reconciliation evidence:** the prior whole-table diagnostic load is replaced by a stable-ID batch scan. Representative-data performance, operational scheduling/alerting, and settlement-worker semantics remain unverified.
3. **Physical webhook connection retirement:** controlled eviction branches exist, but actual physical Hikari retirement after a real failed `RELEASE_LOCK()` is unproven.
4. **Operations:** TLS/certificate validation, least privilege, secret rotation, backups/PITR, restore drills, RPO/RTO, monitoring, retention, and live HTTPS webhook behavior require deployment evidence.
5. **Product scope:** recurring AMC billing, full repair quotation/change-order flows, technician collection reporting, notification requirements, tax/legal policy, and the proposed finance dashboard require owner decisions where not already contracted.

These findings retain the DB-01–DB-33 **PASS WITH GAPS** disposition. DB-31 parts lifecycle (12) and DB-33 opposite-order contention (3) remain separate historical runs and are not payment proof; the payment-specific historical results are likewise not a new combined run.

## Documentation changes

Only this verification report was added. Existing `PAYMENTS.md`, database guides, API/customer/technician references, and the full DB-01–DB-33 report were reviewed and not changed: their current payment wording already preserves the confirmed ambiguity, reconciliation, webhook-eviction, operations, and reassignment limitations. The missing `TARGET_VALOR_SCHEMA_SPEC.md` source is documented in the API contract rather than silently restored.

## Remaining actions

| Priority | Owner | Acceptance evidence |
|---|---|---|
| P0 | Commerce/provider integration | Approved order-recovery/idempotency design with fault-injected remote-success/local-commit failure tests, or formally accepted manual recovery. |
| P0 | Backend/platform | Isolated MySQL/Spring test proving physical lock-owning connection identity, eviction after failed release, and safe replacement-session processing. |
| P1 | Backend/data | Bounded reconciliation design tested for completeness, restart, ambiguity, failure, and concurrent changes. |
| P1 | DBA/SRE/platform | Redacted TLS/grants, secret rotation, backup/PITR, restore, RPO/RTO, monitoring, retention, and webhook runbook evidence. |
| P1/P2 | Product/finance/legal | Decisions on recurring AMC, quotation/change orders, dashboard metrics, notification obligations, tax treatment, and manual uncertain-payment handling. |

## Audit basis

Source reviewed included `CommerceController`, `CommerceService`, `CommerceRepositories`, payment/refund entities and repositories, `RazorpayGateway`, `SecurityConfig`/`AuthConfig`, relevant migrations V9, V44, V49, V53–V57, `CommercePaymentIntegrityTest`, `RazorpayGatewayModeTest`, `WebhookMysqlSpringLifecycleTest`, `Phase1CommerceTest`, current payment documentation, and the DB-01–DB-33 audit report. No live credentials, provider, protected schema, shared database, or external repository was accessed.
