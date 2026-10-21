# Valor payments and financial rules

Status labels used below: **Implemented** means enforced by current backend code; **Configurable** means supplied through deployment configuration; **Not implemented** means no authoritative implementation was found; **Owner confirmation required** means the repository does not establish the policy.

## 1. Scope and authority

Valor uses persisted `invoices`, `payment_records`, `payment_refunds`, and `razorpay_webhook_events` records. Razorpay is the external gateway for online checkout and refund initiation. The backend, not a browser callback, is authoritative for settlement: a verified capture/webhook or an authorized reconciliation result must pass the stored payment/invoice relationship, amount, currency, and provider-reference checks before financial state changes.

The configured currency is INR. Refunds are INR-only in the current commerce service. No currency conversion is performed.

## 2. Checkout and confirmation

- **Implemented:** payable amounts and currency are taken from server-side invoice/pricing records. Client amounts, currency, tax, status, or ownership fields are not authoritative.
- **Implemented:** Razorpay orders/payment references are stored on `payment_records`; provider order and payment identifiers have unique database constraints.
- **Implemented:** checkout creation is not settlement. Provider acceptance, a client callback, or a manually supplied status does not mark an invoice paid.
- **Implemented:** verified Razorpay webhook signatures are required. Webhook event IDs are unique, so duplicate delivery is ignored. Stale, mismatched, pending, failed, or uncertain events do not produce successful settlement.
- **Implemented:** Admin reconciliation can complete eligible payment outcomes after validating provider references and financial fields. It is not a substitute for signature verification when a signed webhook is available.
- **Implemented:** verified successful payment settlement updates only the linked payment/invoice obligation. Linked AMC renewal activation and installation-milestone settlement are idempotent and tied to the validated association.

Gateway mode is explicit. `RAZORPAY_MODE` must be `test` or `live`; the configured key ID must match the recognized Razorpay test/live prefix. A missing, invalid, or mismatched mode is rejected before an external request. `razorpay.offline-test-mode` is an internal mock path and is not Razorpay Test Mode.

### Checkout state sequence

`POST /api/v1/payments/razorpay/checkout` locks the eligible invoice, rejects an existing successful payment, and returns an existing order when a resumable local order ID is present. Otherwise it saves a local `PROCESSING` payment and flushes it before calling `PaymentGateway.createOrder`. A successful response stores the provider order ID, gateway status, and synchronization time. A definitive provider 4xx marks the payment `FAILED`; a timeout, transport error, malformed/empty response, or other non-definitive exception leaves local `PROCESSING` with review required. The transaction intentionally permits that state to be persisted while returning an error.

Local `PROCESSING` state and blocking blind retries reduce duplicate-order risk. The current `PaymentGateway` supports `lookupOrder(String razorpayOrderId)` only; `RazorpayGateway` calls `GET /orders/{id}` and has no receipt-to-order recovery operation. A receipt is a tracking field, not an assumed order-creation idempotency key. If the provider order succeeds before the local order ID commits, automatic recovery is not implemented; manual review or a separately approved provider-supported recovery design is required.

## 3. Refund policy

1. A customer refund request creates a persisted refund request; it does not initiate a gateway refund.
2. An authorized Admin approval is required before gateway initiation. Above the configured same-Admin threshold, a second distinct authorized Admin is required; the first Admin cannot satisfy both approvals.
3. `VALOR_REFUNDS_SAME_ADMIN_THRESHOLD` is required. Missing, blank, malformed, negative, or invalid decimal configuration fails closed with the existing safe configuration error (HTTP 503 at the API boundary).
4. The current approved/test policy is INR-only. The discussed INR 5,000.00 value is a test/recommended configuration, not an application default or universal legal rule. Production must supply its own authorized value through the environment.
5. Amounts at or below the configured threshold use the existing one-Admin path. Amounts strictly above it require distinct second-Admin approval.
6. Gateway initiation is not refund settlement. Only verified terminal provider evidence updates a refund to `SUCCEEDED` or `FAILED` and updates payment refund accounting.
7. Unknown, pending, unavailable, conflicting, or ambiguous provider outcomes remain unresolved/processing. Reconciliation uses the stored provider refund ID where available and is read-only; it never creates or blindly retries a refund.
8. Successful refunds only contribute to refunded totals. The original invoice and original payment amount remain unchanged; net remaining amount is derived from successful refunds. Duplicate webhook/reconciliation delivery is idempotent.
9. Refund settlement cannot mark an invoice or installation milestone paid. Approval identities, reasons, timestamps, provider references, and final state remain auditable.

## 4. Invoices and financial documents

Invoices have immutable financial history in the persisted record: `subtotal`, `tax_amount`, `total_amount`, `currency`, number, status, and issue/due dates. Status values are `DRAFT`, `ISSUED`, `PAID`, `VOID`, and `CANCELLED`. Payment/refund settlement does not rewrite the original amount.

Implemented document routes are PDF-oriented: a successful payment receipt is available at `GET /api/v1/payments/{id}/receipt.pdf`; consolidated refund documents are available at `GET /api/v1/payments/{id}/refunds/document` with the implementation's supported format/query behavior. The backend must not be described as supporting CSV/JSON refund documents unless a separate implementation is added. Documents use persisted records and ownership checks; unsettled payments/refunds are not presented as successful.

## 5. Service, AMC, and installation payments

- Service invoices are generated only for an eligible completed service request, use backend pricing, and are protected against duplicate active invoice creation.
- AMC renewal requests are customer/contract scoped. A quote is backend-priced; renewal status and contract dates change only after authoritative successful payment settlement. Repeated settlement cannot increment `renewal_count` or extend dates twice.
- Installation milestone billing is gated by the exact approved immutable schedule version, required approvals, validated terms, and an authoritative billing trigger. No default percentage, tax, due date, grace period, or trigger is defined by this repository. Incomplete/unapproved schedules fail closed.
- Existing milestone invoice generation is Admin-authorized and idempotent for an eligible milestone. Payment settlement updates only its linked milestone.

## 6. Cash and technician payment handling

Cash settlement uses the existing authorized cash OTP flow. The customer/technician/request relationship and OTP state are validated before settlement; a client-supplied success status is insufficient. No separate installation cash workflow is defined here.

No authoritative salary, earnings, commission, payout cadence, or payroll policy was found in the payment model. Do not infer one from service or payment records.

## 7. Configuration and operations

| Key | Meaning |
|---|---|
| `RAZORPAY_KEY_ID` | Razorpay API key identifier; never log its secret counterpart. |
| `RAZORPAY_KEY_SECRET` | Razorpay API credential, supplied only through secret management. |
| `RAZORPAY_WEBHOOK_SECRET` | Independent webhook-signature secret. |
| `RAZORPAY_MODE` | Required explicit `test` or `live` mode; must match the key-ID mode prefix. |
| `VALOR_REFUNDS_SAME_ADMIN_THRESHOLD` | Required INR decimal threshold for same-Admin refund approval; no application default. |
| `razorpay.offline-test-mode` | Internal mock/test switch; it is not external Razorpay Test Mode. |

Pending/uncertain payments and refunds require safe operational reconciliation. Reconciliation must inspect persisted references and may leave an outcome unresolved; no blind retry is allowed. No live gateway calls are part of automated tests.

Webhook processing verifies the `X-Razorpay-Signature`, requires an event identifier, claims it under a MySQL advisory lock, reads current state after lock acquisition, and releases the lock in Spring transaction completion. Duplicate event IDs are ignored. Normal commit, rollback, timeout/retry, duplicate, and distinct-event paths are historically covered by `WebhookMysqlSpringLifecycleTest` (6); controlled release-failure/eviction branches are test-seam evidence only. Physical Hikari retirement after a real failed `RELEASE_LOCK()` remains unproven.

The admin reconciliation endpoint now reads payment records in ascending-ID keyset batches of 200 using `findByIdGreaterThanOrderByIdAsc`. It is a diagnostic scan: it does not call the provider or mutate payment/invoice/refund state. Each batch advances from the last ID; records inserted before a later batch query are eligible in that run, and records inserted after the scan boundary remain eligible for a later run. This removes the previous whole-table `payments.findAll()` load, but does not claim production-scale performance or exactly-once processing. Provider reconciliation remains an explicit per-payment operation with locking and unresolved-outcome handling.

If a batch query or issue evaluation throws, the exception propagates; the method does not report a successful scan and does not persist a cursor. A later invocation starts at ID 0, so an earlier failed run cannot permanently skip records. Concurrent invocations have no reconciliation-run lock, but this diagnostic scan has no financial side effects; they may repeat issue reads. State changes observed after a batch has been read are evaluated on the next run if they were not included in a later batch query.

Checkout response signatures are not processed by a Valor callback endpoint. Settlement uses the server-side signed webhook path or authorized reconciliation, while the checkout endpoint only creates/returns the server-created order. Webhook signature verification must not be described as client checkout-response signature verification.

## 8. Decision register

| Decision | Current implementation | Configuration required | Unresolved/not implemented |
|---|---|---|---|
| Settlement authority | Verified webhook/capture or validated Admin reconciliation | Razorpay credentials and webhook secret | Real Test Mode verification requires authorized infrastructure |
| Refund threshold | Decimal comparison; above threshold needs distinct Admin | `VALOR_REFUNDS_SAME_ADMIN_THRESHOLD` | Production numeric value requires finance/product authorization |
| Refund currency | INR-only | INR records | No conversion policy |
| Milestone schedule | Exact approved version and trigger gate | Approved schedule/configuration | Financial terms/triggers remain blocked unless explicitly configured |
| Email delivery | Persisted communication plus configured provider/manual processing | SMTP variables when enabled | SMTP inbox delivery is not proven by persistence alone |
| Payroll | No authoritative implementation found | None | Owner decision and domain model required |
