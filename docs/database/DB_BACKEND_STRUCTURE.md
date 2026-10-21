# Valor Backend database structure

This is the practical database guide for the current Spring/Flyway backend. Migrations and entity mappings are authoritative; this document summarizes verified structure and audit evidence. The full narrative is [DB-01–DB-33](audits/DB-01-DB-33-FINAL-AUDIT-REPORT.md).

## Configuration and migration lifecycle

- The recorded audit environment is MySQL 8.0.46. Flyway applied 48 migrations through V57 on fresh isolated schemas, and Hibernate `ddl-auto=validate` passed.
- Spring uses a pooled datasource (HikariCP) and Flyway owns schema evolution. Migration files use `V<version>__<description>.sql`. Startup validates mappings; it does not make a protected or shared schema safe for experiments.
- Run migration experiments only against a newly authorized isolated database with redacted configuration. Never use `valor_world_dev`, production, or a shared audit schema for ordinary checks.

## Domain tables and relationships

| Domain | Main tables/entities | Important relationships and rules |
|---|---|---|
| Identity | `users`, `customer_profiles`, `technician_profiles`, roles/permissions | Customer and technician profiles reference `users`; Admin/SuperAdmin identity is represented by `users.role` rather than an `admins` table. Ownership checks are also service-enforced. |
| Assets | `buildings`, `lifts`, documents, locations | Lifts belong to buildings; customer access follows the ownership path. |
| Service | `service_requests`, assignments, visits, inspections, checklists, snapshots, feedback | Requests connect customer/lift context and technician work; status and cross-parent checks are partly service-enforced. |
| Commerce | `invoices`, `payment_records`, `payment_refunds`, webhook events | Payments retain customer/invoice/service or installation context where applicable. |
| Inventory | parts/catalogue, inventory items, stock movements, part requests | Approval/cancellation and movements are transactional; competing item locks use ascending item IDs. |
| Operations | notifications, audit records, technician locations, OTPs | V44 event-key uniqueness and related foreign keys protect selected idempotent operations. |

V54 enforces uniqueness for non-null invoice installation-milestone links; V56 adds nullable `payment_refunds.provider_idempotency_key` and unique index `uk_payment_refunds_provider_idempotency`; V57 adds `job_checklist_items`, including `fk_job_checklist_item_job`, `ck_job_checklist_item_input_type`, `ck_job_checklist_item_snapshot_quality`, `uk_job_checklist_item_source`, and the nullable `checklist_responses.job_checklist_item_id` link. Other active-state and cross-parent rules remain service-enforced unless the migrations prove otherwise.

## Transaction and concurrency invariants

- Payment/refund workflows lock payment before refund when both rows are needed. `CommerceService` validates amount, currency, customer/invoice/service context, same-admin threshold, and gateway mode.
- Webhooks verify signatures, claim a unique event under a MySQL advisory lock, use a current read after lock acquisition, and release at Spring transaction completion. Controlled release-failure paths evict the connection, but physical Hikari retirement after an actual failed `RELEASE_LOCK()` remains unproven.
- Checklist initialization is transactional and unique per request; recovery reloads the committed checklist after a competing initializer. Recorded MySQL evidence produced one committed checklist/snapshot and successful competing HTTP responses.
- Inventory approval, cancellation, rollback, and retry use row locks and sorted multi-item lock order. DB-31 covered competing approval/cancellation and rollback; DB-33 covered opposite input order in three passing controlled iterations. These tests do not prove all races impossible.

## DB-01–DB-33 evidence register

These are historical results, not a newly executed combined suite.

| Finding/invariant | Status and evidence | Limitation |
|---|---|---|
| MySQL 8.0.46; 48 migrations through V57; Hibernate validation | Pass, isolated MySQL/Spring | Not production infrastructure evidence. |
| V44 notification idempotency key | Verified | Scope is the implemented event key. |
| V54 invoice/milestone uniqueness | Clean migration passed; synthetic duplicates failed as expected | Affected datasets need preflight/remediation approval. |
| V56 refund provider-idempotency key | Implemented and tested | Provider behavior remains external. |
| V57 checklist snapshots and checks | Verified | Some invariants remain service-enforced. |
| Payment context, refund lock order, threshold, Razorpay mode | `CommercePaymentIntegrityTest` 66; `RazorpayGatewayModeTest` 6 | Unit/mocked historical evidence. |
| Webhook locking and lifecycle | `WebhookMysqlSpringLifecycleTest` 6 | Physical failed-release retirement gap remains. |
| Checklist initialization recovery | `ChecklistInitializationRaceTest` 1; recorded MySQL concurrency evidence | Historical scope. |
| Parts lifecycle and opposite order | DB-31 12; DB-33 3 | Bounded controlled scenarios. |
| Authorization and verification | `PartsServiceAuthorizationTest` 4; `ServiceVerificationFlowTest` 13 | Technician reassignment parts policy unresolved. |
| Query plans and constraints | Indexed report counts; low-stock full scans and filesort observations | Small/empty schemas limit performance conclusions. |
| Reconciliation and list queries | Payment diagnostic reconciliation uses ascending-ID keyset batches of 200; other context-dependent unbounded calls remain | Exceptions propagate and the cursor is in-memory only, so re-entry restarts at ID 0. Batching preserves scan coverage across a stable ID cursor but is not a production-scale or exactly-once settlement claim. |
| Infrastructure controls | Open: TLS, least privilege, backups/PITR, restore, RPO/RTO, monitoring, retention | DBA/SRE evidence required. |

See the full report for classifications, owners, acceptance evidence, and the PASS WITH GAPS disposition.

## Safe change workflow

1. Trace a field from migration SQL to entity, repository, service validation, and controller DTO.
2. Add an additive migration only after checking current schema, historical data, indexes, and deployment compatibility.
3. Inspect constraints and indexes from an isolated authorized schema; use `EXPLAIN` with representative data and record assumptions.
4. Detect historical duplicates before adding uniqueness. Preserve schemas and evidence; never delete or rewrite rows as an audit shortcut.
5. Run focused tests against an isolated configured database and label MySQL, H2, mocked, and source-only evidence separately.

## Prioritized unresolved risks

- Ambiguous provider order recovery: define provider-supported idempotency/lookup or accept manual review. Local `PROCESSING` plus blocked blind retry is risk reduction, not automatic recovery.
- Payment reconciliation: the diagnostic scan now uses ascending-ID batches of 200 and has focused multi-batch coverage; it still needs representative-data performance evidence and does not itself settle payments.
- Checkout response/recovery: no client callback signature path or receipt-to-order recovery operation is implemented; webhook/reconciliation architecture is intentional, and ambiguous remote order creation remains manual-review work.
- Physical Hikari eviction after failed lock release: platform owner needs an end-to-end physical-session test.
- Technician reassignment and parts submission: product/backend owner must decide current versus historical technician authorization.
- Historical V54 duplicates and cross-parent/actor consistency: DBA/data and backend owners need approved preflight and policy evidence.
- TLS, grants, backups/PITR, restore drills, RPO/RTO, monitoring, and retention: operations evidence is outside this repository.
