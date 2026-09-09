# Valor rebuild progress

- Branch: `main`; no branch changes or push. Unrelated README.md changes preserved and excluded.
- Stage 4 implementation: canonical IN_APP notification records, recipient-scoped inbox, admin creation, due-time visibility and idempotent read marking. External EMAIL/SMS/PUSH providers remain unimplemented and these channels are rejected.
- Files: added notifications entity/enums/repositories/DTOs/service/controller/error handling, V4 and 14 notification integration tests; updated scan packages, POST role security, Stage-3 migration assertions and API contract.
- Commands: `mvn clean test` and `mvn clean package` both passed without skip flags.
- Tests: 69 passed, 0 failures, 0 errors, 0 skipped in each command: 55 existing tests plus 14 Stage-4 tests. Coverage includes V4/validate, signed IDs, restrictive FK, checks/indexes/defaults, canonical entity scanning, role and recipient enforcement, unsupported channels, inactive recipients, scheduling at the exact due time, paging/status filters, idempotent and concurrent reads, safe errors and secret-free responses.
- Schema: V4 creates only notifications. V1, V2 and V3 remain unchanged. Flyway remains the schema authority; Hibernate validate and disabled SQL initialization remain effective.
- Test database: isolated H2 only. The existing test-only migration adapter removes STORED from V3 for H2; V4 is executed unchanged. This does not establish real-MySQL V4 compatibility.
- Static review: no unsigned BIGINT, cascade REMOVE, unversioned mappings or credential/provider secret literals in the new notification implementation. Retired legacy notification source remains excluded by explicit runtime scans; integration tests verify only the canonical notification mapping. The pre-existing production update default is overridden by the later validate property; configuration was not changed.
- MySQL: V1-V3 were reported verified by the user. V4 was not run against real MySQL here. Neither valor_world_dev nor valor_lift_db was accessed or modified; no backend process or manual MySQL operation was started.
- Blockers: no build/test blockers. Real-MySQL V4 verification remains outstanding; backend Phase-1 schema/API implementation is not marked complete until that verification succeeds.
- Next: real-MySQL V4 verification. No external delivery, workers, clients, payments, inventory or other deferred features were implemented.
