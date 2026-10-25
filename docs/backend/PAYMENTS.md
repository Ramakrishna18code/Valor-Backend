# Valor payments and financial rules

Status labels used below: **Implemented** means enforced by current backend code; **Configurable** means supplied through deployment configuration; **Not implemented** means no authoritative implementation was found; **Owner confirmation required** means the repository does not establish the policy.

## 1. Scope and authority

Valor uses persisted `invoices`, `payment_records`, `payment_refunds`, `razorpay_webhook_events`, and `amc_purchase_orders` records. Razorpay is the external gateway for online checkout and refund initiation. The backend, not a browser callback, is authoritative for settlement: a verified capture/webhook or an authorized reconciliation result must pass the stored payment obligation, amount, currency, and provider-reference checks before financial state changes.

The configured currency is INR. Refunds are INR-only in the current commerce service. No currency conversion is performed.

## 2. Checkout and confirmation

- **Implemented:** payable amounts and currency are taken from server-side invoice/pricing records. Client amounts, currency, tax, status, or ownership fields are not authoritative.
- **Implemented:** Razorpay orders/payment references are stored on `payment_records`; provider order and payment identifiers have unique database constraints.
- **Implemented:** checkout creation is not settlement. Provider acceptance, a client callback, or a manually supplied status does not mark an invoice paid.
- **Implemented:** verified Razorpay webhook signatures are required. Webhook event IDs are unique, so duplicate delivery is ignored. Stale, mismatched, pending, failed, or uncertain events do not produce successful settlement.
- **Implemented:** Admin reconciliation can complete eligible payment outcomes after validating provider references and financial fields. It is not a substitute for signature verification when a signed webhook is available.
- **Implemented:** verified successful payment settlement updates only the linked payment/invoice obligation. Linked AMC renewal activation and installation-milestone settlement are idempotent and tied to the validated association.
- **Implemented:** new AMC purchases use a server-priced Razorpay order tied to the selected customer lift and plan. The paid invoice and AMC contract are created only after verified payment capture; failed or pending attempts create no invoice.

Gateway mode is explicit. `RAZORPAY_MODE` must be `test` or `live`; the configured key ID must match the recognized Razorpay test/live prefix. A missing, invalid, or mismatched mode is rejected before an external request. `razorpay.offline-test-mode` is an internal mock path and is not Razorpay Test Mode.

### Checkout state sequence

`POST /api/v1/payments/razorpay/checkout` locks the eligible invoice, rejects an existing successful payment, and returns an existing order when a resumable local order ID is present. Otherwise it saves a local `PROCESSING` payment and flushes it before calling `PaymentGateway.createOrder`. A successful response stores the provider order ID, gateway status, and synchronization time. A definitive provider 4xx marks the payment `FAILED`; a timeout, transport error, malformed/empty response, or other non-definitive exception leaves local `PROCESSING` with review required. The transaction intentionally permits that state to be persisted while returning an error.

Local `PROCESSING` state and blocking blind retries reduce duplicate-order risk. `PaymentGateway.lookupOrder(String razorpayOrderId)` retrieves an already identified provider order through `GET /orders/{id}`. Separately, `PaymentGateway.listOrders(String receipt, long from, long to, int count, int skip)` supports the bounded receipt-based discovery path described in Section 7. A receipt is a tracking field, not an assumed order-creation idempotency key. If provider order creation succeeds before the local order ID commits, automatic second-order creation is not implemented; manual review or the separately approved bounded discovery path is required.

An ambiguous order-creation result must not trigger a blind retry. Discovery may associate one existing provider order with the local `PROCESSING` payment after exact receipt, amount, currency, and time-window checks, but discovery alone does not prove payment success, capture, invoice payment, or settlement authorization. A later verified provider status or an authorized reconciliation decision is still required.

`POST /api/v1/payments/razorpay/amc-purchase` accepts only a customer-owned lift and a backend catalog plan ID (`essential`, `extended`, or `long-term`). The backend determines the subtotal, 18% GST, and Razorpay amount; client price fields are not accepted. A successful verified capture creates the AMC contract and a `PAID` invoice in the same settlement transaction and links both records to the payment. A failed or uncertain payment does not create an invoice.

## 3. Refund policy

1. A customer refund request creates a persisted refund request; it does not initiate a gateway refund.
2. An authorized Admin approval is required before gateway initiation. Above the configured same-Admin threshold, a second distinct authorized Admin is required; the first Admin cannot satisfy both approvals.
3. `VALOR_REFUNDS_SAME_ADMIN_THRESHOLD` is required. Missing, blank, malformed, negative, or invalid decimal configuration fails closed with the existing safe configuration error (HTTP 503 at the API boundary).
4. The current approved/test policy is INR-only. The discussed INR 5,000.00 value is a test/recommended configuration, not an application default or universal legal rule. Production must supply its own authorized value through the environment.
5. Amounts at or below the configured threshold use the existing one-Admin path. Amounts strictly above it require distinct second-Admin approval.
6. Gateway initiation is not refund settlement. Only verified terminal provider evidence updates a refund to `SUCCEEDED` or `FAILED` and updates payment refund accounting.
7. A local provider idempotency key is persisted with the refund and reused when the same refund initiation is attempted. Local retry blocking prevents a refund in `REQUESTED` or `PROCESSING` from being blindly initiated again. Provider-side interpretation of the idempotency key and duplicate-refund behavior remain unverified until an authorized Razorpay Test Mode validation.
8. Unknown, pending, unavailable, conflicting, or ambiguous provider outcomes remain unresolved/processing. Reconciliation uses the stored provider refund ID where available and is read-only; it never creates or blindly retries a refund. Ambiguous outcomes require provider investigation and an authorized recovery decision.
9. Successful refunds only contribute to refunded totals. The original invoice and original payment amount remain unchanged; net remaining amount is derived from successful refunds. Duplicate webhook/reconciliation delivery is idempotent.
10. Refund settlement cannot mark an invoice or installation milestone paid. Approval identities, reasons, timestamps, provider references, and final state remain auditable.

## 4. Invoices and financial documents

Invoices have immutable financial history in the persisted record: `subtotal`, `tax_amount`, `total_amount`, `currency`, number, status, and issue/due dates. Status values are `DRAFT`, `ISSUED`, `PAID`, `VOID`, and `CANCELLED`. Payment/refund settlement does not rewrite the original amount.

Implemented document routes are PDF-oriented: a successful payment receipt is available at `GET /api/v1/payments/{id}/receipt.pdf`; consolidated refund documents are available at `GET /api/v1/payments/{id}/refunds/document` with the implementation's supported format/query behavior. The backend must not be described as supporting CSV/JSON refund documents unless a separate implementation is added. Documents use persisted records and ownership checks; unsettled payments/refunds are not presented as successful.

## 5. Service, AMC, and installation payments

- Service invoices are generated only for an eligible completed service request, use backend pricing, and are protected against duplicate active invoice creation.
- AMC renewal requests are customer/contract scoped. A quote is backend-priced; renewal status and contract dates change only after authoritative successful payment settlement. Repeated settlement cannot increment `renewal_count` or extend dates twice.
- New AMC plan purchases go directly to Razorpay from the plan selection. The backend keeps the purchase details while payment is pending and generates the AMC contract and paid invoice only after capture verification.
- Installation milestone billing is gated by the exact approved immutable schedule version, required approvals, validated terms, and an authoritative billing trigger. No default percentage, tax, due date, grace period, or trigger is defined by this repository. Incomplete/unapproved schedules fail closed.
- Existing milestone invoice generation is Admin-authorized and idempotent for an eligible milestone. Payment settlement updates only its linked milestone.

## 6. Cash and technician payment handling

Cash settlement uses the existing authorized cash OTP flow. The customer/technician/request relationship and OTP state are validated before settlement; a client-supplied success status is insufficient. No separate installation cash workflow is defined here.

No authoritative salary, earnings, commission, payout cadence, or payroll policy was found in the payment model. Do not infer one from service or payment records.

## 7. Configuration and operations

### Invoice reminders

Collectible invoices with a persisted due date are checked by a bounded scheduled task when Spring scheduling is enabled, at five stages: three days before the due date, on the due date, and three, seven, and fourteen days overdue. The default schedule is 09:15 in `Asia/Kolkata`; deployments may set `VALOR_PAYMENT_REMINDERS_CRON`, `VALOR_PAYMENT_REMINDERS_TIMEZONE`, and `VALOR_PAYMENT_REMINDERS_BATCH_SIZE`. The implementation bounds the batch size to 1-500.

Each invoice/stage/recipient has a durable communication idempotency key. Re-entry therefore reuses the existing communication event rather than creating another notification. Paid, cancelled, and void invoices are excluded. Remaining balance is calculated from the invoice total less successful/partially-refunded payment amounts and successful refunds; no late fees or settlement are applied.

The task enqueues in-app, email, and SMS communication records subject to existing billing preferences. The default email/SMS providers are mock or disabled unless deployment configures a real provider, so enqueueing, a persisted communication row, a mock provider result, or a queued retry is not evidence that a recipient received an external message. Provider availability, provider acceptance, delivery latency, and end-recipient delivery require separately configured providers and operational evidence. Communication retry state remains the delivery authority.

### Payment issue scan and provider-backed reconciliation

The Admin payment issue scan is a bounded, read-only local diagnostic. It reads payment records in ascending-ID batches of 200, evaluates persisted references and status relationships, does not call Razorpay, and does not mutate payment, invoice, or refund state. Failed scans propagate an exception and do not persist a cursor; a later invocation starts again at ID 0. Concurrent scans may repeat reads because the scan has no financial side effects.

The Admin provider-backed payment reconciliation operation is separate. It is authorized, uses provider calls only when the stored state requires them, validates provider references and financial fields, and may update a payment/invoice association or settlement state after those checks. Provider errors, mismatches, unavailable evidence, incomplete discovery, and ambiguous results remain unresolved and require operational review.

### Ambiguous Razorpay order recovery

If checkout creates a local `PROCESSING` payment but no Razorpay order ID is persisted, checkout retries remain blocked. An authorized admin reconciliation may perform a bounded, read-only `GET /v1/orders` scan using the persisted deterministic receipt `valor-payment-{paymentId}`, the persisted payment creation time with a one-hour margin, and a one-hour forward clock margin. The scan uses documented `receipt`, `from`, `to`, `count`, and `skip` parameters, at most 100 results per page and ten pages.

Only an exact receipt, amount, currency, and plausible creation-time match is eligible. No match, multiple matches, malformed responses, provider errors, or an incomplete page scan leave the payment unresolved. A single complete exact match may associate the existing order ID under the payment row lock; discovery itself never marks a payment successful, pays an invoice, captures, refunds, or creates another order. The existing verified order/payment reconciliation then remains responsible for any later status transition. A provider contract or operational guarantee beyond the documented list endpoint is not assumed.

| Key | Meaning |
|---|---|
| `RAZORPAY_KEY_ID` | Razorpay API key identifier; never log its secret counterpart. |
| `RAZORPAY_KEY_SECRET` | Razorpay API credential, supplied only through secret management. |
| `RAZORPAY_WEBHOOK_SECRET` | Independent webhook-signature secret. |
| `RAZORPAY_MODE` | Required explicit `test` or `live` mode; must match the key-ID mode prefix. |
| `VALOR_REFUNDS_SAME_ADMIN_THRESHOLD` | Required INR decimal threshold for same-Admin refund approval; no application default. |
| `razorpay.offline-test-mode` | Internal mock/test switch; it is not external Razorpay Test Mode. |

Pending/uncertain payments and refunds require safe operational reconciliation. Reconciliation must inspect persisted references and may leave an outcome unresolved; no blind retry is allowed. No live gateway calls are part of automated tests.

For an ambiguous checkout outcome, keep the payment in `PROCESSING`, do not create another order, inspect the persisted receipt and local payment record, and use the bounded receipt discovery or authorized manual provider review. Associate an order only after one exact match is established. Then reconcile the provider order/payment status; do not mark the invoice paid from discovery alone. Escalate no-match, multiple-match, malformed, incomplete, or unavailable-provider cases.

For an ambiguous refund outcome, keep the refund in `PROCESSING`, retain and use the stored provider refund ID when available, and reconcile only terminal provider evidence matching the stored payment, amount, currency, and references. Do not submit another refund blindly. Escalate missing identifiers, conflicting evidence, provider unavailability, and duplicate-refund uncertainty for an authorized decision.

Webhook processing verifies the `X-Razorpay-Signature`, requires an event identifier, claims it under a MySQL advisory lock, reads current state after lock acquisition, and releases the lock in Spring transaction completion. Duplicate event IDs are ignored. Normal commit, rollback, timeout/retry, duplicate, and distinct-event paths are historically covered by `WebhookMysqlSpringLifecycleTest` (6); controlled release-failure/eviction branches are test-seam evidence only. Physical Hikari retirement after a real failed `RELEASE_LOCK()` remains unproven.

The local payment issue scan reads payment records in ascending-ID keyset batches of 200 using `findByIdGreaterThanOrderByIdAsc`. Each batch advances from the last ID; records inserted before a later batch query are eligible in that run, and records inserted after the scan boundary remain eligible for a later run. This removes the previous whole-table `payments.findAll()` load, but does not claim production-scale performance or exactly-once processing. It is distinct from the explicit provider-backed per-payment reconciliation operation.

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
| DB-36/DB-37 connection eviction | Test-only failure-injection harness and static datasource/session assertions | Isolated disposable MySQL schema and DB-37 credentials | Real failed `RELEASE_LOCK()` execution, physical Hikari retirement, changed replacement `CONNECTION_ID()`, and advisory-lock cleanup remain unverified |
| Live Razorpay behavior | Gateway mode validation, mocked/service tests, and bounded provider-call implementations | Authorized Razorpay Test Mode credentials and webhook infrastructure | Live checkout, webhook delivery/retry, order discovery, refund idempotency, duplicate-refund behavior, and provider evidence remain unverified |
| GST/tax compliance | Persisted invoice tax fields and explicit non-tax receipt wording | Finance/tax-owned policy and approved configuration | GST registration, HSN/SAC, place-of-supply, CGST/SGST/IGST, rounding, exemptions, and compliant tax-invoice workflow are not established |
| Reminder delivery | Scheduled durable communication enqueue with idempotency and retry state | Configured communication providers and operational monitoring | Real provider acceptance and recipient delivery are unverified |
| Production operational readiness | Fail-closed configuration and bounded application workflows | Production grants, monitoring, alerting, backup/recovery, runbooks, and authorized provider setup | Production readiness, recovery drills, delivery SLAs, and infrastructure evidence remain unverified |
