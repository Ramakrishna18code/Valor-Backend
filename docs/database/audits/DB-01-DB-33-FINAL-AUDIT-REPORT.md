# Valor Backend Database Audit Closure Report (DB-01–DB-33)

**Repository:** `Valor-Backend`  
**Disposition:** **PASS WITH GAPS**  
**Scope:** application integrity, schema constraints, transaction/concurrency behavior, migration safety, query shape, and operational evidence reviewed across DB-01–DB-33.

## A. Executive summary

The audit established substantial application-level and isolated-MySQL coverage for payment/refund integrity, webhook idempotency, checklist initialization, inventory reservation/cancellation, rollback, and migration validation. The recorded evidence supports **PASS WITH GAPS**, not production-readiness or complete exactly-once/operational recovery guarantees.

Important validated controls include Flyway V1–V57 installation and Hibernate validation on isolated MySQL 8.0.46 schemas; invoice/payment/refund context validation; webhook duplicate protection and transaction-coupled retry behavior; checklist snapshot uniqueness and concurrent initialization recovery; inventory stock locking, cancellation idempotency, and multi-item rollback; and relevant unique/FK/CHECK constraints.

The highest-priority remaining risks are real physical Hikari retirement after an actual failed `RELEASE_LOCK()`, provider-side recovery/idempotency for an ambiguous Razorpay order creation, unbounded synchronous payment reconciliation, and missing production operational evidence. Technician reassignment rules for parts submission remain a product decision.

This report reconciles recorded DB-01–DB-33 results. Results not rerun during DB-34 are explicitly marked historical. Isolated MySQL evidence does not prove production workload performance, infrastructure controls, or provider behavior.

## B. Evidence classification

1. **Real MySQL/Spring integration evidence** — executed against an isolated MySQL schema through Spring/application lifecycle.
2. **Unit or mocked evidence** — mocks, spies, controlled substitutes, or test seams.
3. **H2/test-database evidence** — tests not executed against the isolated MySQL integration schema.
4. **Source/configuration inspection** — implementation or configuration reviewed without behavioral execution.
5. **Operations evidence required** — cannot be established by repository inspection alone.
6. **Previously reported, not independently reverified** — recorded in DB-01–DB-33 context or prior output, but not rerun during this documentation pass.

These categories are not interchangeable. In particular, H2 and unit results do not prove MySQL locking behavior; source configuration does not prove production operations; and an isolated schema does not represent production data volume.

## C. Validated integrity areas

| Area | Evidence and result | Classification |
|---|---|---|
| Flyway/Hibernate | Fresh isolated MySQL schemas applied 48 migrations through V57; Hibernate `ddl-auto=validate` passed. MySQL 8.0.46 was the recorded server. | Previously reported real MySQL/Spring integration |
| Migration constraints | V44 notification event-key uniqueness, V54 invoice/milestone uniqueness, V56 refund provider-idempotency uniqueness, V57 checklist snapshot/CHECK constraints, generated active-assignment uniqueness, and relevant foreign keys were inspected or exercised. | Real MySQL plus source/configuration; individual results historical unless noted |
| Payment/invoice/refund integrity | `CommerceService` and `CommerceRepositories` changes validate invoice/customer/service-request/AMC context and preserve amount, currency, authorization, purpose, and refund safeguards. `CommercePaymentIntegrityTest` recorded 66 passed; `RazorpayGatewayModeTest` recorded 6 passed. | Unit/mocked; historical reported |
| Checkout ambiguity | Local payment is persisted as `PROCESSING` before provider order creation. Ambiguous provider outcomes fail closed/manual review; no blind retry is claimed. | Source/configuration inspection |
| Webhook deduplication | Unique event ID, MySQL advisory lock, current read after lock acquisition, and transaction-completion release were tested through the Spring path. `WebhookMysqlSpringLifecycleTest` recorded 6 passed; same-event concurrency, rollback/retry, duplicate, and distinct-event scenarios were reported passing. | Previously reported real MySQL/Spring integration |
| Webhook release failure | Controlled release-failure and eviction branches are covered by test seams/mocks. Actual Spring/Hikari physical retirement after a real failed `RELEASE_LOCK()` remains unproven. | Unit/mocked plus source inspection |
| Checklist initialization | Lazy GET initialization is transactional and unique by request; DB-13 recovery reloads the committed checklist after duplicate initialization. `ChecklistInitializationRaceTest` recorded 1 passed. | H2/unit and previously reported MySQL evidence |
| Inventory approval/cancellation | `PartsService`, locked item/request repositories, movement records, sorted item locking, cancellation behavior, and Hibernate proxy/entity-graph fix were exercised. DB-31 recorded 12 passed; DB-33 opposite-order contention recorded 3 passed. | Previously reported real MySQL/Spring integration |
| Inventory rollback | DB-31 reported deterministic test-scoped failure after earlier work, with stock, movements, request state, and retry checked. | Previously reported real MySQL/Spring integration |
| Opposite input order | DB-33 reported 3 passed with opposite line order and production item-ID sorting. This is bounded evidence, not proof that all deadlocks are impossible. | Previously reported real MySQL/Spring integration |
| Authorization/workflow | `PartsServiceAuthorizationTest` recorded 4 passed; `ServiceVerificationFlowTest` recorded 13 passed. Current-vs-historical technician semantics for parts submission remain unresolved. | Unit/mocked; historical reported |
| Service/request/checklist integrity | Assignment, inspection, checklist, payment, and cross-parent service checks were audited; many are service-enforced rather than composite database constraints. | Source/configuration inspection plus focused tests |
| Query plans | Prior isolated `EXPLAIN` work reported indexed aggregate/report queries, full scans for low-stock column comparisons, and indexed service-request/assignment predicates with filesort. Empty/small schemas limit conclusions. | Previously reported real MySQL read-only inspection |

## D. Open findings register

### 1. Webhook advisory-lock connection eviction — high

DB-22 introduced HikariCP 6.3.3 `HikariDataSource.evictConnection(Connection)` handling for release failure, unexpected release results, and release exceptions. Controlled failure-path tests cover the decision branch and DB-20/DB-21 normal lifecycle evidence covers commit, rollback, timeout, retry, and successful reuse. However, a real Spring/MySQL test that forces an actual `RELEASE_LOCK()` failure and proves retirement of the exact lock-owning physical connection has not been completed.

**Acceptance:** an isolated end-to-end test proves the physical session cannot return to normal pool use while holding the lock and that a replacement session can process safely; alternatively the technical owner formally accepts the limitation with a documented mitigation.

### 2. Payment checkout recovery — high

`CommerceService.createRazorpayCheckout()` persists a local `PROCESSING` payment before calling the gateway, then persists the provider order ID after the response. If the provider succeeds but the response or local commit is lost, the provider order may exist without a locally saved order ID. Existing local reconciliation is useful when a provider order ID is known, but the inspected `PaymentGateway` abstraction does not establish provider-supported order-creation idempotency or a reliable lookup from the local receipt. Blind retry is unsafe and is not claimed as a fix.

**Disposition:** safely mitigated but operationally manual for the ambiguous case. **Acceptance:** documented provider-supported idempotency/recovery is implemented and tested, or the manual reconciliation procedure is formally accepted.

### 3. Unbounded payment reconciliation — high

`CommerceService` contains a reconciliation path using `payments.findAll()`. This can load the entire payment table synchronously and extend memory, query, and transaction duration as history grows. Replacing it with naive page numbers could skip or repeat records during concurrent changes.

**Acceptance:** a stable-key/checkpointed or otherwise behavior-preserving bounded design is tested with representative synthetic data for completeness, restart, duplicate handling, transaction semantics, and failure recovery before production implementation.

### 4. Other unbounded queries — medium/high, context-dependent

Source inspection identified material `findAll()` call sites including `CustomerAccountService`, `AmcServiceImpl`, `BuildingServiceImpl`, `InventoryServiceImpl`, `LiftServiceImpl`, `NotificationServiceImpl`, `ServiceRequestServiceImpl`, and catalog/category paths in `PartsService`. Some may intentionally return a bounded catalog or a complete administrative view; none should be changed solely because `findAll()` appears.

Required evidence is caller/API analysis, representative row counts, stable ordering/pagination compatibility, and query plans. Potential impact includes memory growth, latency, and response-size pressure. No broad pagination change is authorized by this report.

### 5. Production infrastructure evidence — high operational gap

Repository configuration demonstrates Flyway, Hibernate validation, datasource/pool configuration, and local connectivity. It does not prove production TLS/certificate verification, least-privilege grants, backup/PITR success, restore drills, RPO/RTO, monitoring/alerting, retention, or live pool behavior under failure.

**Owner:** DBA/SRE/platform. **Acceptance:** redacted production configuration evidence, grants review, backup/PITR and restore-drill records, documented RPO/RTO, alert/retention evidence, and a controlled pool/lock-failure runbook.

### 6. Technician reassignment and parts submission — product decision

`PartsService.submit()` resolves a technician profile but does not visibly establish a current-assignment check. Existing tests cover profile/request/status behavior, but the intended rule after reassignment—current technician only, historical technician, or another policy—is not established sufficiently to change production behavior.

**Owner:** product plus backend authorization owner. **Acceptance:** written policy, tests for current assignment, reassignment, historical/terminal states, and implementation only after approval.

### 7. Historical V54 duplicate invoice data — migration/data risk

The clean V53/V54 path passed through V57. The synthetic duplicate-data path failed at V54 when `uk_invoice_installation_milestone` was added because duplicate non-null milestone IDs violated the unique constraint. Rows were preserved; no repair or cleanup was performed.

Environments with equivalent duplicates require an authorized preflight and reviewed remediation plan before applying V54. This is a historical data risk, not evidence that the clean migration is invalid.

### 8. Other parent/actor consistency and plan limits — medium/open

Several cross-parent relationships are service-validated rather than database-enforced composite relationships. Historical actor references, reassignment semantics, inspection technician semantics, and some nullable/generic billing relationships remain policy-sensitive. Query-plan conclusions are limited by tiny or empty isolated schemas. No speculative foreign keys, indexes, or backfills should be added without data preflight and business approval.

## E. Historical migration and isolated-schema evidence

The recorded baseline is MySQL 8.0.46, Flyway through V57, with 48 successful migrations and Hibernate schema validation passing on fresh isolated installations. Previously used isolated schemas include `valor_backend_audit_20261021`, `valor_backend_constraint_audit_20261021`, V54 valid/duplicate schemas, concurrency and Spring integration schemas, and DB-17 through DB-33 audit schemas including `valor_backend_db29_20261021`, `valor_backend_db30_20261021`, `valor_backend_db31_20261021`, and `valor_backend_db33_20261021`. These are historical evidence inventories; this pass did not alter or clean them.

V54 evidence is specifically split:

- clean isolated V53 data upgraded successfully through V57 and retained its rows;
- synthetic V53 data with duplicate non-null `invoices.installation_milestone_id` values failed at V54 when the unique constraint was applied;
- the duplicate schema was preserved for inspection and no rows were deleted or rewritten.

Any deployment with equivalent historical duplicates requires duplicate detection and an authorized remediation decision before V54.

## F. Query plans and performance limitations

Recorded DB-07/DB-08 query-plan work found:

- report summary aggregates using repository-level count/summary queries rather than loading every service request for those counts;
- low-stock comparisons involving column-to-column predicates that produced full scans in the isolated plan;
- service-request and technician-assignment filters using relevant indexes but showing filesort for requested ordering;
- no representative production-sized data, so row estimates, selectivity, cache behavior, and actual latency remain unverified;
- synchronous full-table payment reconciliation and several context-dependent unbounded list queries.

The evidence does not justify speculative indexes. Any index proposal needs the actual predicate/order, existing prefix analysis, representative MySQL data, and `EXPLAIN`/measurement in an authorized environment. Any reconciliation change must preserve completeness and restart semantics, not merely reduce the number of rows loaded.

## G. Prioritized remediation plan

| Priority | Finding | Next action | Owner | Acceptance evidence | Code change authorized? |
|---|---|---|---|---|---|
| P0 | Ambiguous provider order outcome | Define provider-supported idempotency/lookup or approve manual reconciliation | Commerce/provider integration | Approved design plus failure/retry tests | Requires decision |
| P0 | Real Hikari retirement after failed release unproven | Run isolated end-to-end release-failure test or formally accept limitation | Backend/platform | Physical session identity, eviction, replacement, retry evidence | No change until evidence |
| P1 | Unbounded payment reconciliation | Design stable-key/checkpointed batches and test restart/completeness | Backend/data | Representative synthetic dataset and fault-injection results | Requires design review |
| P1 | Production DB controls unverified | Supply TLS, grants, backup/PITR, restore, RPO/RTO, monitoring evidence | DBA/SRE | Redacted artifacts and drill records | Operational, not repository-only |
| P2 | Parts authorization after reassignment | Decide current vs historical technician rule | Product/backend | Written policy and authorization regression tests | Not authorized until decision |
| P2 | Other unbounded list paths | Rank by caller, volume, and API compatibility; add pagination only where safe | Backend/API | Representative plans and contract-preserving tests | Case-by-case |
| P2 | Historical V54 duplicates | Run authorized duplicate preflight before deployment | DBA/data owner | No duplicate non-null milestone IDs or approved remediation | No migration alteration here |
| P3 | Broader query/index verification | Run MySQL plans with representative data | DBA/backend | Captured `EXPLAIN`, workload assumptions, measured comparison | Deferred |

## H. Final test and environment inventory

The following are recorded DB-01–DB-33 results, not a new combined run in DB-34. Overlapping suites are not summed into a single total.

| Test or validation | Reported result | Evidence status |
|---|---:|---|
| `PartsMysqlSpringLifecycleTest` full DB-31 run | 12 passed | Previously reported real MySQL/Spring integration |
| DB-33 opposite-order contention scenario | 3 passed | Previously reported real MySQL/Spring integration |
| `CommercePaymentIntegrityTest` | 66 passed | Previously reported unit/mocked/service-level |
| `RazorpayGatewayModeTest` | 6 passed | Previously reported unit/mocked |
| `WebhookMysqlSpringLifecycleTest` | 6 passed | Previously reported real MySQL/Spring integration |
| `PartsServiceAuthorizationTest` | 4 passed | Previously reported unit/service-level |
| `ChecklistInitializationRaceTest` | 1 passed | Previously reported H2/test-database or controlled integration evidence; MySQL scope must be read from its run record |
| `ServiceVerificationFlowTest` | 13 passed | Previously reported unit/service-level |
| Report-service focused test | 1 passed where recorded | Previously reported; not independently reverified here |
| `mvn -q -DskipTests compile` | Passed | Previously reported and standard build evidence |
| `git diff --check` | Passed with existing LF/CRLF warnings | Rechecked during DB-34 |

The DB-31 12-pass result and DB-33 3-pass opposite-order result are separate runs. A passing bounded race is evidence for that scenario, not proof that deadlocks are impossible under all workloads.

## I. Safety and worktree preservation

DB-34 added documentation only: this report. The pre-existing tracked modifications and untracked files were preserved, including the prior `CommerceService.java`, `CommerceRepositories.java`, `PartsService.java`, `PartModels.java`, `ChecklistService.java`, reporting/repository changes, prior tests, `WebhookMysqlSpringLifecycleTest.java`, parts tests, and checklist race tests.

This pass did not access or modify `D:\RKKKK\Valor-Website`, `valor_world_dev`, production/shared databases, `.env`, external payment providers, or any audit schema. It did not run migrations, mutate fixtures, commit, push, or deploy. No production code, test code, migration, configuration, or schema was changed.

Final worktree status should contain the baseline entries plus the new untracked report path:

`docs/database/audits/DB-01-DB-33-FINAL-AUDIT-REPORT.md`

The report is intentionally uncommitted for review.

## Closure conclusion

Valor Backend has meaningful, evidence-backed integrity controls and strong isolated MySQL/Spring coverage for the tested payment, webhook, checklist, and inventory scenarios. The correct closure status is **PASS WITH GAPS**. The audit should not be marked fully closed until the owner decisions and evidence above are addressed, particularly provider-supported checkout recovery, bounded payment reconciliation, and either real physical Hikari retirement proof or formal acceptance of that limitation. Production operational readiness remains an operations-owned evidence question, not a conclusion available from this repository alone.
