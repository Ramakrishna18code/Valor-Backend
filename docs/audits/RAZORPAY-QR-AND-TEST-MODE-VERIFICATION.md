# Razorpay QR and Test Mode Verification Audit

Audit date: 2026-10-30

Scope: `D:\RKKKK\Valor-Backend` and `D:\RKKKK\Valor-Android-APP` only. This is a read-only audit. No source, configuration, database, migration, payment, provider, or secret state was changed.

## 1. Executive summary

The implemented provider payment flow is Razorpay Standard Checkout backed by a server-created Razorpay Order. The Android app uses the `react-native-razorpay` native SDK for the invoice and AMC checkout screens; the web variant uses Razorpay Checkout.js. UPI is presented as a payment option inside the Standard Checkout experience where supported by Razorpay. There is no distinct Razorpay QR Code API integration.

The app's `qrcode-generator` dependency is unrelated to payments. It creates a static QR containing a Valor customer ID in `CustomerIdQr.tsx`; it does not create a Razorpay payment QR, provider order QR, UPI QR, or dynamic payment payload.

The backend has provider REST calls for orders, payments, refunds, and reconciliation lookups, plus a signed Razorpay webhook endpoint. It does not expose a QR creation endpoint. The backend validates the checkout response signature and then checks the provider payment status and amount/currency against the persisted payment/invoice. Signed webhook processing remains an additional authoritative settlement path.

One Android limitation is confirmed: `ServiceRequestDetailsScreen` labels a button “Pay by UPI” but only creates a backend Razorpay order and displays an alert; it does not call `openRazorpayCheckout`. The invoice flow in `PaymentsScreen` and the AMC flow in `AmcPaymentScreen` do open Standard Checkout.

## 2. Actual payment methods and QR findings

| Capability | Current implementation | Evidence |
|---|---|---|
| Razorpay Standard Checkout | Implemented for invoice checkout and AMC purchase checkout | Android `PaymentsScreen.startCheckout`, `AmcPaymentScreen.checkout`, `src/services/nativePayments.ts`, `src/services/nativePayments.web.ts`; dependency `react-native-razorpay`. |
| UPI inside Standard Checkout | Offered as a displayed checkout option in the AMC payment UI; the SDK receives the normal Checkout options | `AmcPaymentScreen` lists UPI, card, and net banking; actual provider method selection is delegated to Razorpay Checkout. |
| Separate Razorpay QR Code API | Not found | No QR provider endpoint, QR client, or QR order capability exists in inspected backend/controller/gateway source. |
| Static Android QR generation | Implemented for customer identity only | `CustomerIdQr.tsx` imports `qrcode-generator` and encodes `Valor Customer ID: {id}`. |
| Dynamic provider-generated QR payment | Not implemented | No payment QR payload, QR polling, QR status endpoint, or Razorpay QR API call found. |
| UPI intent-specific integration | Not found | No UPI intent payload, intent deep link, or separate UPI SDK/API call found. |
| Cash OTP | Separate non-Razorpay payment method | `CommerceController` cash OTP routes and `ServiceRequestDetailsScreen`; not a QR or Standard Checkout method. |

The customer identity QR must not be described or tested as a payment QR.

## 3. Backend endpoints and implementation

### Checkout/order creation

```text
POST /api/v1/payments/razorpay/checkout
POST /api/v1/payments/razorpay/amc-purchase
```

`CommerceController` delegates to `CommerceService.createRazorpayCheckout(...)` and `createAmcPurchaseCheckout(...)`. The service:

- authorizes the customer or approved Admin path as applicable;
- uses the persisted invoice/payment amount rather than a client amount;
- creates a local `PaymentRecord` in `PROCESSING` before provider order creation;
- uses `RazorpayGateway.createOrder(...)` with `/v1/orders` and receipt `valor-payment-{paymentId}`;
- persists the provider order ID and returns the public key ID, order ID, amount, currency, and local payment/invoice IDs;
- blocks blind retry when the provider-order outcome is uncertain.

`RazorpayGateway` uses the provider base URL `https://api.razorpay.com/v1`, Basic authentication with the configured key ID/secret, and the provider order endpoint. No provider call was made during this audit.

### Checkout response verification

```text
POST /api/v1/payments/razorpay/verify
```

`CommerceService.verifyRazorpayPayment(...)`:

1. Finds and locks the local payment by Razorpay order ID.
2. Validates the returned order ID/payment ID/signature using the server key secret.
3. Looks up the provider payment through `RazorpayGateway.lookupPayment(...)` (`GET /v1/payments/{paymentId}`).
4. Only marks the payment successful when the provider status is `captured` and amount/currency match the persisted invoice/payment.
5. Otherwise retains non-success states such as `PROCESSING` or records failure.

The response signature is not treated as proof of capture by itself.

### Webhook

```text
POST /api/v1/webhooks/razorpay
Header: X-Razorpay-Signature
```

`CommerceController.razorpayWebhook(...)` delegates to `CommerceService.razorpayWebhook(...)`. `RazorpayGateway.validWebhookSignature(...)` validates the HMAC against the configured webhook secret. The service parses event/order/payment/refund identifiers, deduplicates event IDs, uses the existing guarded claim/locking path, and applies event-specific persisted state. Webhook evidence is distinct from client Checkout response evidence.

### Provider lookup/reconciliation

`RazorpayGateway` also contains:

- `GET /v1/orders/{orderId}` via `lookupOrder`;
- `GET /v1/orders` with receipt/time/count/skip filters via `listOrders`;
- `GET /v1/payments/{paymentId}` via `lookupPayment`;
- refund lookup/list and refund creation endpoints.

These are reconciliation/refund/provider-record paths, not QR payment paths. No polling loop for a Razorpay QR payment was found. Cash OTP polling in `ServiceRequestDetailsScreen` is unrelated.

## 4. Android integration and route compatibility

### Invoice checkout: implemented Standard Checkout

`src/screens/PaymentsScreen.tsx` calls:

```text
customerApi.createRazorpayCheckout(invoice.id)
```

It passes the backend response's public key, amount converted to paise, currency, invoice description, and provider order ID to `openRazorpayCheckout(...)`. It requires `razorpay_payment_id`, `razorpay_order_id`, and `razorpay_signature`, then calls `customerApi.verifyRazorpayPayment(...)`. It reloads billing data after success or error and does not infer success from a cancelled/incomplete SDK result.

### AMC checkout: implemented Standard Checkout

`src/screens/amc/AmcPaymentScreen.tsx` calls either invoice checkout or:

```text
customerApi.createAmcRazorpayCheckout({ liftId, planId })
```

It opens `openRazorpayCheckout(...)`, requires the three SDK response fields, and calls backend verification. It routes to the existing activation verification screen using the local payment ID; this does not replace backend payment/webhook authority.

### Service request detail “Pay by UPI”: incomplete client integration

`src/screens/ServiceRequestDetailsScreen.tsx` calls `customerApi.createRazorpayCheckout(serviceInvoice.id)` but currently only shows an alert that the order is ready. It does not invoke `openRazorpayCheckout` or submit a payment verification response. This is not a separate QR integration and is not evidence of a completed UPI checkout path.

### Android dependencies and QR rendering

- `react-native-razorpay`: Standard Checkout native module.
- `qrcode-generator`: static customer-ID QR rendering only.
- No Razorpay QR SDK/API dependency was found.
- `src/services/nativePayments.ts`: native SDK adapter.
- `src/services/nativePayments.web.ts`: browser Checkout.js adapter.
- `src/components/CustomerIdQr.tsx`: non-payment identity QR.

## 5. Razorpay Test Mode configuration contract

The backend maps environment variables through `src/main/resources/application.properties` without reading any `.env` file in this audit:

```text
RAZORPAY_MODE          -> razorpay.mode
RAZORPAY_KEY_ID        -> razorpay.key-id
RAZORPAY_KEY_SECRET    -> razorpay.key-secret
RAZORPAY_WEBHOOK_SECRET -> razorpay.webhook-secret
```

`RazorpayGateway` behavior:

- `RAZORPAY_MODE` is normalized and must be exactly `test` or `live` for live gateway operations.
- Test mode requires a key beginning with `rzp_test_`.
- Live mode requires a key beginning with `rzp_live_`.
- Missing/blank key ID or key secret fails closed before order creation, lookup, or refund operations.
- Mode/key mismatch fails closed before any provider request.
- `RAZORPAY_WEBHOOK_SECRET` is required for valid webhook signature verification; missing or blank webhook secret rejects webhook processing.
- `razorpay.offline-test-mode` is a separate application property used by tests/mocks. When enabled, the gateway returns synthetic order/payment/refund data and bypasses the external mode validation. This is not Razorpay Test Mode and must not be presented as provider verification.
- The public key ID is returned to the client checkout view; key secrets and webhook secrets are not returned by the inspected API DTOs or gateway responses.

The current implementation validates this contract lazily when gateway operations are attempted. This audit found no separate startup validator proving that the application refuses to start before any payment operation when configuration is missing or mismatched.

## 6. Existing test coverage

Safe focused command run from `D:\RKKKK\Valor-Backend`:

```text
mvn -q "-Dtest=RazorpayGatewayModeTest,CommercePaymentIntegrityTest" test
```

Exit code: `0`.

Observed Surefire results:

- `RazorpayGatewayModeTest`: 6 tests, 0 failures, 0 errors.
- `CommercePaymentIntegrityTest`: 73 tests, 0 failures, 0 errors.

The tests use fixtures/mocks and did not call an external provider or database. The integrity tests cover persisted amount authority, signature/webhook handling, duplicate webhook protection, payment state transitions, pending/failed/refund states, and fail-closed reconciliation behavior. Expected synthetic error logs were emitted by tests exercising lock-release failure handling; they did not fail the run.

Not run because they require database/provider infrastructure or are outside the safe boundary:

- `WebhookMysqlSpringLifecycleTest` — DB-backed lifecycle test.
- `Db37WebhookLockEvictionTest` — DB37 test; DB-37 remains unresolved.
- Any live Razorpay, live webhook delivery, provider dashboard, or real payment/refund test.

Android has no dedicated Razorpay checkout test file. Existing Android source inspection found no test covering SDK response handling, public key/order compatibility, provider callback behavior, QR capability, or live payment status. The `qrcode-generator` declaration issue remains a known Android typecheck blocker, but it is unrelated to payment integration.

## 7. Coverage gaps and verification limits

- No test currently proves Android receives a backend order and passes the exact returned order ID/public key into the native SDK.
- No Android test covers missing SDK fields, cancellation, incomplete callback, or verification failure.
- No Android test covers the incomplete `ServiceRequestDetailsScreen` UPI-labelled flow.
- No distinct Razorpay QR capability exists to test.
- No live or sandbox provider call was made, so provider dashboard order creation, payment capture, webhook delivery, and settlement remain unverified.
- No test in this audit proves runtime deployment configuration before gateway operation; the existing mode test proves lazy fail-closed operation validation.
- No live polling path for Razorpay payment status was found. Backend provider lookup/reconciliation is separate from Android UI polling.

## 8. Safe operator Test Mode checklist

An operator can verify Test Mode privately without sharing values:

1. Confirm the deployment environment, secret manager, or process environment—not a checked-in file—contains `RAZORPAY_MODE=test`.
2. Confirm the key ID is a test key with the `rzp_test_` prefix. Do not copy the value into chat, tickets, screenshots, logs, or reports.
3. Confirm the key secret and webhook secret are present and nonblank using the secret manager’s private validation facilities; never print them.
4. Confirm the webhook endpoint configured in the Razorpay Test Dashboard is the intended non-production HTTPS endpoint and uses the matching webhook secret privately.
5. Confirm the app’s configured public key source is the same Test Mode key ID returned by the backend checkout response. Do not hard-code or report the key.
6. Create a disposable test invoice or approved test AMC transaction only in an isolated Test Mode account, with explicit business authorization; do not use production customer/payment data.
7. Confirm the backend creates one local `PROCESSING` record and one provider order, and that retries reuse/block according to the existing uncertain-outcome rules.
8. Complete one provider test payment in Standard Checkout, including the intended UPI option if Razorpay presents it, then confirm the client returns order ID/payment ID/signature to the backend.
9. Confirm backend signature validation, provider capture lookup, amount/currency matching, and persisted status transition.
10. Deliver a signed Test Mode webhook and confirm event acceptance, deduplication on replay, and persisted payment state. Treat dashboard delivery as separate evidence from client callback verification.
11. Confirm cancellation, failed payment, duplicate callback, delayed/pending state, and webhook replay remain non-success until authoritative evidence arrives.
12. Keep Test Mode credentials and test artifacts isolated from live deployments; disable/revoke temporary access when verification is complete.

No step requires opening or sharing an `.env` file. Values should be checked privately and recorded only as pass/fail metadata.

## 9. Explicit non-goals and final safety statement

No QR payment method was added. No new payment method was introduced. No source, `.env`, secret, database schema, migration, financial calculation, invoice, checkout, refund, settlement, AMC, or provider state was changed. No credentials were inspected, printed, copied, or uploaded. No payment, refund, external gateway request, or production configuration change was initiated.
