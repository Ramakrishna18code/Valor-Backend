# Protection management API

The protection feature provides a shared insurer/product catalog, customer lift coverage records and quote requests, technician coverage records, claims, renewal requests, expiry notices, and a role-scoped dashboard. All routes require the existing Valor bearer token and use the standard `ApiResponse` envelope. Collection responses are paged as `{ items, page, size, totalItems, totalPages, hasNext }`; page defaults to `0`, size to `20`, and size is limited to `100`. Catalog and policy/application/claim lists support a `search` parameter where applicable.

## Routes

| Method and route | Access |
| --- | --- |
| `GET /api/v1/protection/providers[?search=&page=&size=]` | Authenticated; active catalog for customers/technicians; admins can also see inactive providers |
| `GET /api/v1/protection/providers/{id}` | Authenticated |
| `POST /api/v1/admin/protection/providers`, `PUT /api/v1/admin/protection/providers/{id}` | Admin |
| `GET /api/v1/protection/products[?insuredAssetType=&providerId=&search=&page=&size=]` | Authenticated; active catalog for customers/technicians; admins can also see inactive products |
| `GET /api/v1/protection/products/{id}` | Authenticated |
| `POST /api/v1/admin/protection/products`, `PUT /api/v1/admin/protection/products/{id}` | Admin |
| `GET /api/v1/technicians/me/protection/policies[?status=&search=&page=&size=]`, `POST /api/v1/technicians/me/protection/policies` | Owning technician |
| `GET /api/v1/protection/technician-policies/{id}` and `/documents` | Owner or admin |
| `POST /api/v1/protection/technician-policies/{id}/documents` | Owner or admin |
| `GET /api/v1/admin/protection/technician-policies[?status=&search=&page=&size=]`, `PATCH /api/v1/admin/protection/technician-policies/{id}/insurer-confirmation` | Admin |
| `GET /api/v1/customers/me/protection/lift-policies[?status=&search=&page=&size=]`, `POST /api/v1/customers/me/protection/lift-policies` | Owning customer |
| `GET /api/v1/protection/lift-policies/{id}` and `/documents` | Owner or admin |
| `POST /api/v1/protection/lift-policies/{id}/documents` | Owner or admin |
| `GET /api/v1/admin/protection/lift-policies[?status=&search=&page=&size=]`, `PATCH /api/v1/admin/protection/lift-policies/{id}/insurer-confirmation` | Admin |
| `GET /api/v1/customers/me/protection/applications[?status=&search=&page=&size=]`, `POST /api/v1/customers/me/protection/applications` | Owning customer |
| `GET /api/v1/protection/applications/{id}` and `/documents` | Owner or admin |
| `POST /api/v1/customers/me/protection/applications/{id}/documents`, `/decision`, `/cancel` | Owning customer |
| `GET /api/v1/admin/protection/applications[?status=&search=&page=&size=]`, `POST /api/v1/admin/protection/applications/{id}/manual-quote` | Admin |
| `GET /api/v1/protection/claims[?status=&search=&page=&size=]`, `POST /api/v1/protection/claims` | Authenticated; create only against an owned policy |
| `GET /api/v1/protection/claims/{id}` and `/documents`, `POST /api/v1/protection/claims/{id}/documents`, `/withdraw` | Claimant; admins may read/add claim document metadata |
| `GET /api/v1/admin/protection/claims[?status=&search=&page=&size=]`, `PATCH /api/v1/admin/protection/claims/{id}/insurer-status` | Admin |
| `GET /api/v1/protection/renewals[?status=&page=&size=]`, `POST /api/v1/protection/renewals`, `POST /api/v1/protection/renewals/{id}/cancel` | Request owner |
| `GET /api/v1/admin/protection/renewals[?status=&page=&size=]`, `PATCH /api/v1/admin/protection/renewals/{id}/insurer-status` | Admin |
| `GET /api/v1/protection/expiry-notices[?page=&size=]`, `PATCH /api/v1/protection/expiry-notices/{id}/read` | Notice recipient |
| `GET /api/v1/protection/dashboard` | Authenticated; data scoped to the current user, or global for admin |

Document routes record metadata only; they do not upload, serve, or download file bytes. `storageKey` must be an opaque relative key supplied by the application's authorized document-storage flow. Do not send credentials, public URLs, or signed URLs in metadata.

## Manual insurer workflow and decision boundaries

Provider and product records are admin-maintained shared catalog data, not insurer integrations. No insurer API credentials or secrets are stored or returned.

Technician and lift policy POST routes record an **existing insurer-issued policy** supplied by its owner. New records start as `DECLARED`; Valor does not generate policy numbers, bind coverage, or issue or renew policies. Admins may record an insurer-reported status as `INSURER_CONFIRMED_ACTIVE` or `INSURER_CONFIRMED_INACTIVE` with an external insurer reference and review note. The owner remains responsible for confirming policy details with the provider.

A lift application starts at `SUBMITTED`. An admin may record a manually obtained insurer quote, which changes the application to `QUOTE_RECEIVED`; the customer can then record `CUSTOMER_ACCEPTED` or `CUSTOMER_DECLINED`. Acceptance records customer intent only and does not create a policy or bind cover.

Claims progress from `SUBMITTED` (optionally `DOCUMENTS_REQUESTED`) to `FORWARDED_TO_INSURER`, then `INSURER_DECISION_PENDING`. `INSURER_APPROVED` and `INSURER_REJECTED` are permitted only as insurer-reported outcomes and require an external insurer claim reference and response note. Valor does not adjudicate or settle claims; no settlement/payment state or operation is provided. Claimants may withdraw before forwarding.

Renewal requests are routed for manual insurer follow-up. `INSURER_CONFIRMED` and `INSURER_DECLINED` are insurer-reported outcomes with external reference and response note; neither outcome changes or replaces the policy record. No renewal coverage is issued by Valor.

## Expiry notices and audit

The scheduled task checks insurer-confirmed active policies daily at 09:10 Asia/Kolkata and creates persistent notices for expiry dates exactly 30, 15, 7, and 0 days away. A unique database key prevents duplicate notices for the same policy and stage. `valor.protection.expiry.cron` and `valor.protection.expiry.timezone` may be configured for deployment. Notices appear in the recipient's expiry-notices collection; this feature does not send an email/SMS or claim that a notice was delivered externally.

Mutations are recorded in the existing audit log and protection activity feed. The V62 Flyway migration creates the feature's catalog, policy, application, claim, renewal, document-metadata, notice, and activity tables and indexes. It inserts no providers, products, policies, quotes, or other mock data.

## Local UI demo data

For local development only, start the backend with the `dev` profile and `--valor.protection.demo-seed.enabled=true`. The idempotent demo seeder creates clearly labeled synthetic provider/product, policy, application, claim, and renewal records linked to demo-only customer and technician accounts. The provider and products are inactive. Policy records remain `DECLARED`; applications, claims, and renewals remain pending. No insurer decisions or real coverage are represented. Do not enable this option in production.
