# Valor database schema

This is a migration-grounded reference for the backend. It summarizes the tables that participate in identity, assets, workflow, finance, installation, communication, and audit. Exact migrations under `src/main/resources/db/migration` and entity mappings are authoritative. For migration lifecycle, concurrency invariants, and audit evidence, use [DB_BACKEND_STRUCTURE.md](DB_BACKEND_STRUCTURE.md).

## A. Database overview

- Schema changes use Flyway SQL migrations named `V<version>__<description>.sql`; the recorded clean baseline is 48 migrations through V57. Version numbers are not assumed to be contiguous.
- Hibernate schema validation is used alongside Flyway. A schema change must be additive where possible, include matching entity/repository updates, and be verified with migration/schema tests.
- The configured application database dialect/credentials are deployment concerns and are not reproduced here.

## B. Table catalogue

Column types below are database types from migrations. `id BIGINT` primary keys are generated unless noted. Foreign keys use the stated `ON DELETE` behavior; where no action is written, the database default applies.

### Identity, authorization, and audit

| Table | Columns and constraints | Relationships |
|---|---|---|
| `users` | `id BIGINT`; `email VARCHAR(254)` nullable unique; `phone VARCHAR(20)` nullable unique; `password_hash VARCHAR(255)` nullable; `role VARCHAR(20)` required (`SUPER_ADMIN`,`ADMIN`,`CUSTOMER`,`TECHNICIAN`); active/locked flags; failed-login count; lock/login timestamps; created/updated/deleted timestamps | Parent for profiles, tokens, OTPs, audit and actor references; deletes are restricted where referenced |
| `customer_profiles` | `id BIGINT`; `user_id BIGINT` required unique; `full_name VARCHAR(160)` required; alternate phone/company/address/rating; status (`ACTIVE`,`INACTIVE`,`SUSPENDED`); active and timestamps | FK to `users` `RESTRICT` |
| `technician_profiles` | `id BIGINT`; unique `user_id`; unique nullable `employee_id`; area/specialization; availability (`AVAILABLE`,`BUSY`,`OFF_DUTY`,`ON_LEAVE`); rating, dates, active flag, timestamps; later latitude/longitude `DECIMAL(9,6)` with range checks | FK to `users` `RESTRICT`; referenced by assignments, locations, applications and work records |
| `roles` / `permissions` | Numeric IDs; role `name VARCHAR(40)` unique; permission `code VARCHAR(80)` unique plus description/category | `role_permissions` joins them; join FKs cascade on delete |
| `audit_logs` | Actor, action, entity type/id, metadata/reason, created timestamp (exact fields in V10) | Actor FK to `users` `SET NULL`; indexes by time, actor, entity, action |
| `refresh_tokens` / `otp_verifications` | Hashed refresh token with expiry/revocation; OTP hash, phone, attempts, expiry/lock/verified timestamps, provider fields added in V19 | User FKs restricted; hashes, not raw tokens/codes, are persisted |

### Customers, assets, and service workflow

| Table | Important columns/constraints | Relationships and lifecycle |
|---|---|---|
| `buildings` | Customer, name, type/address/city/state/pincode, emergency contact, status, active flag, timestamps; V12 latitude/longitude `DECIMAL(9,6)` nullable with range checks | Customer FK `RESTRICT`; indexed by customer/status |
| `lifts` | Building, name/number/model/manufacturer, capacity/floors, serial/installation/location, warranty/maintenance fields, health, QR/specifications, status (`ACTIVE`,`DOWN`,`MAINTENANCE`,`OUT_OF_SERVICE`), active/timestamps | Building FK `RESTRICT`; serial and QR unique; range/nonnegative/date checks |
| `service_requests` | Customer/lift, unique `service_id`, title/description/category/priority, status, service type, remarks, requested/preferred dates, notes, completion and estimate timestamps | Customer/lift FKs `RESTRICT`; statuses include `PENDING`,`ASSIGNED`,`ACCEPTED`,`ON_THE_WAY`,`REACHED_SITE`,`DIAGNOSIS`,`REPAIR_IN_PROGRESS`,`WAITING_FOR_PARTS`,`TESTING`,`COMPLETED`,`CANCELLED`; installation may have nullable lift after V25, other service types require one |
| `technician_assignments` | Request, technician, assigning user, status (`ASSIGNED`,`ACCEPTED`,`REJECTED`,`RELEASED`,`COMPLETED`), assignment/accept/release times, notes, timestamps; generated `active_request_id` | FKs to request/technician/user restricted; unique active request index prevents two active assignments for a request |
| `service_reports` / `service_status_history` | Report links request/assignment/reporter and requires nonblank diagnosis/work/testing; history stores actor, from/to status, notes/time | Request report is unique; all core FKs restrict deletion |
| `service_visits` / `visit_change_requests` / `service_visit_history` | Visit date/time/status (`SCHEDULED`,`IN_PROGRESS`,`COMPLETED`,`CANCELLED`); change request type/status and reviewer; immutable history fields | Active visit per request is unique through generated key; request, technician, actor and reviewer FKs are restricted |
| `service_request_attachments` / `service_request_feedback` | Stored file metadata and unique storage key; feedback rating 1–5 and one row per request | Request/user/customer FKs restricted |
| `lift_service_codes` / `service_code_verifications` | Lift-linked stored code hash/state; verification request, technician, action (`START`,`COMPLETE`), attempts/lock/verified fields; unique `(service_request_id, technician_id, action)` | Lift/request/technician relationships enforce service-code ownership; raw codes are not schema data |
| `job_checklists`, `checklist_templates`, `checklist_items`, `job_checklist_items`, `checklist_responses`, `completion_otps` | Templates/items, one checklist per request, V57 snapshot rows in `job_checklist_items`, responses, and completion OTP hashes/state | `job_checklist_items.job_checklist_id` is required and cascades on checklist delete; `source_item_id` is nullable and unique with checklist ID; `input_type` is limited to `CHECKBOX`, `TEXT`, `NUMBER`, `PHOTO_NOTE`; `snapshot_quality` is `VERIFIED` or `RECONSTRUCTED`; `checklist_responses.job_checklist_item_id` is nullable |

### Payments, invoices, and refunds

| Table | Important columns/constraints | Relationships |
|---|---|---|
| `invoices` | `invoice_number VARCHAR(40)` unique nullable; customer, optional service request/AMC, creator; description; `subtotal`, `tax_amount`, `total_amount DECIMAL(12,2)`; `currency VARCHAR(3)` default INR; status (`DRAFT`,`ISSUED`,`PAID`,`VOID`,`CANCELLED`); issue/due/created/updated dates | Customer/request/AMC/creator FKs `RESTRICT`; amount checks nonnegative; payment does not rewrite original values |
| `payment_records` | Customer, optional request/AMC/invoice, creator; amount `DECIMAL(12,2)` positive; currency; purpose (`SERVICE_REQUEST`,`AMC_RENEWAL`,`INVOICE`,`OTHER`); status (`PENDING`,`PROCESSING`,`SUCCEEDED`,`FAILED`,`CANCELLED`,`REFUNDED`,`PARTIALLY_REFUNDED`); provider refs, failure/gateway status/sync timestamps | All FKs restricted; Razorpay order/payment IDs unique; indexed by customer/request/invoice |
| `payment_refunds` | Payment, requester, positive amount `DECIMAL(12,2)`, currency, status (`REQUESTED`,`PROCESSING`,`SUCCEEDED`,`FAILED`), provider refund ID unique, nullable `provider_idempotency_key` (V56), gateway status/reason, timestamps; V49 approval actor/timestamp/reason fields | Payment/requester/approval actor FKs restricted; unique index `uk_payment_refunds_provider_idempotency` covers the provider idempotency key; refund amount/accounting is derived from persisted successful records |
| `razorpay_webhook_events` | Unique provider `event_id`, type, optional order/payment/refund IDs, processed time | Indexed by provider order/payment; duplicate event IDs cannot be recorded twice |
| `financial_adjustments` (where present in later migrations) | Adjustment identity, payment/invoice/refund linkage, amount/currency/status/reason/audit fields as defined by its migration | Preserve original records; use migration/entity definition as exact authority if extended |

### AMC and installation

| Table | Important columns/constraints | Relationships |
|---|---|---|
| `amc_contracts` | Lift, unique AMC number, plan/coverage, start/end, status (`ACTIVE`,`EXPIRED`,`NON_AMC`,`CANCELLED`,`RENEWED`), renewal date/reminder, renewal count, timestamps | Lift FK restricted; date and count checks |
| `amc_renewal_requests` | Contract/customer/requesting user, optional invoice/payment, status (`REQUESTED`,`QUOTED`,`PAYMENT_PENDING`,`RENEWED`,`REJECTED`,`CANCELLED`), requested dates, optional quote/currency/notes, timestamps | All FKs restricted; dates valid and quote positive when present |
| `installation_requests` | Customer/lift or installation asset, lifecycle/status, site/schedule/proposal references and timestamps as defined by V25/V39+ | Customer and asset relationships are restricted; installation-specific fields are additive across migrations |
| `installation_milestones` | Installation request, stable milestone code/order, status (`PLANNED`,`BILLABLE`,`INVOICED`,`PAID`,`CANCELLED`), nullable positive amount/currency, invoice/payment references, optional schedule, `version BIGINT`, timestamps | Request/invoice/payment/schedule FKs are `RESTRICT`; `(installation_request_id,milestone_code)` is unique. V54 adds the invoice-to-milestone FK and a unique `invoices.installation_milestone_id` index, so the current schema permits one linked invoice row per milestone (including any historical row already linked). |
| `installation_schedules` / definitions / approvals | Versioned schedule identity, lifecycle, ordered terms, exact-version company/customer approval records and actor/timestamps | Approved versions are immutable in application logic; billing gate requires exact version, complete terms, trigger and required approvals |

### Communication and operational records

| Table | Important columns/constraints | Relationships |
|---|---|---|
| `communication_templates` | Event/channel/template key unique; subject/body/html and active fields; channel check (`EMAIL`,`SMS`,`WHATSAPP`,`IN_APP`) | Referenced by messages |
| `communication_preferences` | One row per user; channel/category preference flags including email/SMS/WhatsApp/job/billing/system/report settings | User FK restricted |
| `communication_events` | Idempotency key unique; event type, recipient, status (`PENDING`,`PROCESSING`,`SENT`,`DELIVERED`,`FAILED`,`CANCELLED`), retry/timing/audit fields | Recipient FK restricted; status/retry indexes |
| `communication_messages` | One `(event_id, channel)` unique row; recipient, template, subject/body/html/from/reply-to, status and retry fields | Event/recipient/template FKs restricted; no automatic worker is assumed by this schema |
| `notifications` | In-app notification recipient/title/message/channel/status (`PENDING`,`SENT`,`FAILED`,`READ`), schedule/read/timestamps | Recipient FK restricted |
| `technician_latest_locations` / `technician_location_history` / saved locations | Coordinates with range checks, technician/request relationship and timestamps; one latest row per request | FKs restricted; location access is role/ownership controlled |
| `technician_applications` / documents / private attachments | Application token hash, status, applicant/contact and review/meeting fields; unique storage keys and document type per application | Reviewer/user/application FKs; document deletion rules are migration-defined |

Other operational tables include support tickets, parts/catalog/stock/request/event tables, profile photos, asset documents, admin settings, and installation proposals/inspections. Their exact fields and constraints are defined by their respective migration files and should be changed only through a new Flyway migration.

## C. Relationship reference

- `users` own customer or technician profiles and provide authenticated actors for admins, assignments, approvals, OTPs, refresh tokens, audit, and communication.
- Customers own buildings and lifts, which anchor service requests, AMC contracts, service codes, documents, visits, and location views.
- A service request may have assignments, visits, reports, checklists, verification records, attachments, feedback, parts, status history, payment/invoice links, and installation records.
- Invoices link customer obligations to service requests, AMC contracts, or installation milestones; payment records optionally link to invoices and the same business obligation; refunds link only to payments.
- Webhook events are separate idempotency records. Approval actors and audit rows preserve who changed financial state.
- Installation schedules are versioned; milestone eligibility is exact-version and approval dependent. No unapproved schedule creates a payable obligation.
- Communication events create channel messages; in-app notifications are a separate persisted feature. SMTP delivery status is not proof of recipient inbox delivery.
- There is no verified payroll/earnings relationship in the current financial schema.

## D. Integrity and lifecycle rules

- Foreign keys generally use `ON DELETE RESTRICT` to preserve financial, workflow, and audit history. Explicit exceptions include role-permission join cascades, selected application/document cascades, and audit actor `SET NULL`.
- Unique constraints enforce user identity, service IDs, active assignments/visits, one report/feedback/checklist per request, provider references, webhook event IDs, refund provider IDs, communication idempotency, milestone identity, and the invoice-to-milestone linkage.
- Workflow and financial transitions are service-validated and transactional. Payment/refund settlement uses persisted locks/association checks and idempotent state transitions; H2 tests do not prove production-database concurrency semantics.
- Hashes are stored for passwords, refresh tokens, OTPs, onboarding tokens, and service codes; raw secrets are not schema fields.
- Profile photos remain filesystem-backed rather than rows in the relational schema; the authenticated user ID is the storage filename component and Admin reads resolve the canonical customer/technician profile before loading it.

## E. Change rules

Add a new versioned Flyway migration for schema changes. Preserve existing financial/history rows, use additive changes unless a separately approved compatibility migration is required, update entities/repositories, and add isolated database-backed tests for foreign keys, uniqueness, and lifecycle constraints.
## Email registration challenges (V55)

`email_registration_challenges` stores pending self-registration only. It contains an opaque string primary key, normalized unique email, optional phone, BCrypt password hash, registration profile fields, BCrypt OTP hash, remaining attempts, expiry, lock/resend timestamps, delivery status, verification timestamp, and lifecycle timestamps. It has no relationship to `users` until successful verification; successful verification creates the canonical customer rows and invalidates the challenge. Expired rows are invalidated by state and are not automatically purged.
