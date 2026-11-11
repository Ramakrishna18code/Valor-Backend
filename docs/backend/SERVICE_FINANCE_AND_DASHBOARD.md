# Service finance and customer dashboard: current data contract

Reviewed against the backend controllers and the admin and customer API clients on 11 November 2026. This guide documents current behavior. The 11 November admin finance and customer home work changed presentation and client tests; it did **not** change backend routes, persistence, pricing, coverage decisions, or payment settlement rules.

All routes below are under `/api/v1`. Admin finance operations require an authenticated `ADMIN` or `SUPER_ADMIN` role; customer account and billing reads are scoped to the signed-in customer. The backend, not the screen, decides whether work is covered, an invoice is due, a payment is verified, or a payout is eligible. See [API contract](BACKEND_API_CONTRACT.md) and [payments](PAYMENTS.md) for wider request and payment rules.

## Admin finance screens

| Screen or action | Backend source | What the screen does |
| --- | --- | --- |
| Job review | `GET /service-requests/{id}/finance`, `GET /service-requests/{id}/team`, `GET /service-requests/{id}/coverage-decisions` | Shows persisted inspection, work, payment-stage, team, and coverage records for the chosen request. A missing section is shown as an empty or unavailable state; the UI does not create a record to fill it. |
| Coverage review | `GET /admin/amc-contracts/{id}/entitlements`, `PUT /admin/amc-contracts/{id}/entitlements`, `POST /admin/service-requests/{id}/coverage-decisions` | Admin records structured contract terms and captures the decision for the selected request. Quotation release checks the stored decision and work scope. An active contract alone does not make a visit, labour, or parts free. |
| Commission rules and commissions | `GET/POST /admin/finance-workflow/rules`, `POST /admin/finance-workflow/rules/{id}/approve`, `GET/POST /admin/finance-workflow/commissions` | Lists actual rules and entries. A new rule needs an approved rate and effective dates; an empty list is not an enabled default rate. |
| Payouts, expenses, purchases, reports, settings | `GET /admin/finance-workflow/{payouts|expenses|purchases|report|settings}` plus the corresponding write/action routes in `FinanceWorkflowController` | Each tab reads its own records and gives a useful empty state. Payout actions and evidence are backend checked. If a section request fails, the portal shows it as unavailable; the client does not infer a financial result. |
| Service bills and quotations | `GET /admin/pricing-catalogue`, `GET /admin/quotations`, `POST /admin/service-requests/{id}/quotation-drafts`, `POST /admin/service-requests/{id}/quotations`, `POST /admin/quotations/{id}/revisions`, `POST /admin/quotations/{id}/release` | Admin selects approved catalogue work and its scope. Drafts, revisions, release and accepted terms are server records. Combined labour-and-parts items on AMC jobs require both scopes confirmed excluded before a paid quote can be released. |
| Invoice register and detail | `GET /admin/reconciliation/invoices?page&size&q`, `GET /invoices/{id}`, `GET /invoices/{id}/document.pdf` | The register shows server invoice reconciliation rows and supports server search and paging. Detail and PDF come from the invoice routes. A blank register is an empty result, not a generated invoice. |

The finance workspace loads independent sections. A failed section can show an unavailable message while other sections remain usable. Its client-side settings fallback (`MANUAL`, manual bank confirmation disabled) is an **unavailable-data fallback**, not a persisted policy response. Check the settings endpoint before reporting the configured policy. The invoice register's search uses the reconciliation route; the general `GET /invoices` route is a separate customer/admin invoice list without that search parameter.

## Customer home and service summary

| Visible information | Backend source and presentation rule |
| --- | --- |
| Account and asset counts | `GET /customers/me`, `/customers/me/buildings`, `/customers/me/lifts`; the app counts active owned assets for display. |
| Active AMC card | `GET /amc-contracts`, with owned lift `amcCoverage` as the card-count fallback. This count is only a summary. Work coverage comes from the selected lift, scope, service date and stored request decision. |
| Open requests and recent service | Paged `GET /customers/me/service-requests`; the app excludes `COMPLETED` and `CANCELLED` from its open count and maps raw statuses to customer wording in `customerUiText.ts`. `GET /customers/me/visits` supplies actual visits; a preferred date is not a confirmed visit. |
| Service payment summary | Paged `GET /invoices` and `/payments`; `serviceBillingData.ts` associates records with the request and `ServicePaymentSummary` presents them. The UI does not create or settle a charge. If billing reads fail, it shows unavailable information rather than a confirmed zero balance. |
| Notifications | `GET /notifications`; the home preview reads five items and shows the unread count from that preview. It is **not** an all-notification unread total if more items exist. |
| Technician count and promotions | `GET /customers/me/technician-counts` and `/customers/me/amc-promotions`; an unavailable count or empty promotion response must not be represented as a confirmed offer. |

The home redesign changed card hierarchy, typography, spacing, graphics, navigation, and reduced-motion-aware entrance animation. The support action opens `HelpSupport`; the AMC, service history, profile, and asset actions open their existing routes. These changes do not alter the backend contract.

## Rules held pending approval

The user explicitly chose to hold undecided business rules. The team guide's ₹500–₹600 helper allowance and 20% lead commission are examples, **not** approved rates. Do not configure defaults for helper rate or basis, multi-day allowance, lead commission, refund adjustment, cash collection authority, inspection fee, cancellation charge, AMC deductible, limited-parts amount, grace period, or externally paid legacy AMC treatment until approved. Preserve the lead's agreed commission when helpers join; helper allowances are separate records.

## Verification and remaining work

The recent admin UI build and 120 admin tests passed; the customer typecheck, web export and 175 customer tests passed. These results verify the client changes, not a new backend build or a live payment provider run. The six cross-role scenarios on isolated MySQL and Razorpay Test Mode remain outstanding: covered AMC visit, excluded AMC work, revised non-AMC quote with advance, cash collection and remittance, refund, and lead-plus-helpers payout. Do not use the development database as the isolated test environment.

When a future change alters one of these routes, state transitions, persisted fields, or financial guards, update this guide in the same change and record the test evidence. For visual-only work, record the affected screen and state clearly that the backend contract is unchanged. See the [documentation update guide](../../../DOCUMENTATION_UPDATE_GUIDE.md).
