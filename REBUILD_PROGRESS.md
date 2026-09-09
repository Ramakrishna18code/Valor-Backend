# Valor rebuild progress

- Branch: `main`; no branch changes or push.
- Completed: Stage 1 authentication/security retained; Stage 2 buildings, lifts and AMC contracts implemented. No later domains or client changes.
- Files: added canonical `assets` entities, enums, repositories, DTOs, service, controller, safe error handler and UTC business clock; added `AssetIdentityAccess`, V2 migration and `Stage2AssetsTest`; updated explicit scan packages, asset role rules, customer-profile status getter and API contract.
- Commands: `mvn clean test` (initially 2 new test failures from lazy profile field access; fixed using getters), then `mvn clean test` and `mvn clean package` both passed. No skip flags.
- Tests: 38 passed, 0 failures, 0 errors, 0 skipped in each final command: all 23 existing Stage-1 tests plus 15 Stage-2 tests.
- Stage-2 coverage: V1/V2 migration and Hibernate validate on H2; canonical-only entity scans; restrictive FKs/indexes; no duplicate lift ownership or persisted derived values; ADMIN/SUPER_ADMIN management and CUSTOMER/TECHNICIAN denial; JWT-owned AMC reads; immutable ownership; inactive/suspended/locked owners; row-retaining deactivation; renewal/date and payload validation; derived counts/coverage including inclusive boundaries; no unversioned route aliases.
- Schema: only V2 added, creating `buildings`, `lifts`, `amc_contracts`; V1 unchanged. Signed BIGINT keys, named checks, restrictive FKs, no physical deletion API or cascade REMOVE.
- Static checks: no unsigned BIGINT or duplicate customer/derived columns in canonical assets/V2; SQL initialization is disabled. Legacy unversioned source/entity files remain outside explicit runtime scans. The pre-existing earlier production `ddl-auto` update default is overridden by the final `validate` property; configuration was not changed. Retired root `schema.sql` is not loaded.
- MySQL verification: not performed for V2. Only in-memory H2 test databases were used. Neither `valor_world_dev` nor `valor_lift_db` was accessed or modified; no backend runtime was started.
- Blockers: none for tested implementation; actual MySQL V2 verification remains outstanding.
- Next stage: separately authorize V2 MySQL verification or the next workflow stage. Service requests, technician jobs, reports, notifications, payments, inventory and clients remain deferred; `Valor-technician` untouched.
- Unrelated pre-existing `README.md` change preserved and excluded from staging.
