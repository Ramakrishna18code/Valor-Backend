# Live development CORS verification - 10 September 2026

- Implementation: `295d2ef` on `main`; not pushed. No backend code/configuration/migration changes in this documentation follow-up.
- Direct HTTP after restart: GET `/api/v1/health` returned 200 with data.status=UP.
- Login preflight: OPTIONS `/api/v1/auth/login/admin`, requested method POST and header content-type, returned 200 with exact Access-Control-Allow-Origin for both `http://localhost:5173` and `http://127.0.0.1:5173`.
- Unapproved-origin preflight returned 403 with no Access-Control-Allow-Origin. Unauthenticated GET `/api/v1/me` and `/api/v1/admin/dashboard/summary` each returned a consistent ApiResponse 401. Protected-route authorization remains effective.
- User manually confirmed successful SUPER_ADMIN portal login from `http://localhost:5173` after restart with the CORS fix. No credentials, tokens, authorization headers or sensitive environment values are recorded.
- Correct binding remains CORS_ALLOWED_ORIGINS -> app.cors.allowed-origins. The active security chain consumes the explicit origin list; the retired security configuration remains excluded.
- Existing CORS-commit validation: mvn clean test and mvn clean package passed with 100 tests, zero failures/errors/skips. No rerun required for this documentation-only follow-up.
- Portal verification: 25 tests and production build passed again. Browser automation is unavailable; reload, dashboard live display/network agreement, browser refresh races, logout/Back and console behavior remain manual observations. See the portal ADMIN_MIGRATION_PROGRESS.md for the complete evidence matrix.
- V1-V4, MySQL schema/data, valor_lift_db, client source and README files are unchanged. Existing README/Postman environment edits are preserved and excluded. Direct HTTP used only health, preflight and unauthenticated reads; no direct database access.

# Phase-1 verification complete

- Current backend phase: Phase 1 complete.
- Backend branch: `main`.
- Latest implementation commit before this documentation commit: `1ab6df0`.
- Java tests: 95 passed.
- Maven package: passed.
- Real-MySQL Postman assertions: 1,143 passed.
- Flyway schema version: V4.
- Remaining backend limitation: production OTP delivery provider is not implemented.
- Local development SUPER_ADMIN credentials must never be used for production.
- No client repository has been migrated yet.

### Real MySQL Phase-1 Verification — 10 September 2026

The following completed verification results were supplied by the user for this record; no database access was performed during this documentation update.

- Database: `valor_world_dev`
- Flyway migrations: V1–V4 successfully validated
- Postman collection: `Valor Phase1 Local - retained MySQL verification`
- Iterations: 1
- Assertions executed: 1,143
- Passed: 1,143
- Failed: 0
- Skipped: 0
- Errors: 0
- Result: Full Phase-1 API workflow passed against real MySQL.
- Verified: authentication, roles, staff provisioning, customer assets, AMC contracts, service requests, technician assignments, lifecycle transitions, status history, service reports, notifications, refresh-token rotation, logout and ownership enforcement.
- `valor_lift_db`: untouched.
- Secrets, passwords, tokens and OTPs: not recorded.

## Next execution sequence

1. Push `Valor-Backend/main` only after explicit authorization.
2. Migrate `Valor-Admin-Portal` to the frozen `/api/v1` contract.
3. Migrate `Valor-Android-APP`.
4. Migrate the technician React Native client.
5. Verify the complete technician workflow against `Valor-Backend`.
6. Retire the duplicate Java backend inside `Valor-technician` only after that verification.
7. Keep `Valor-Website` backend-independent because website enquiries are outside Phase 1.

## Historical implementation records

The entries below describe their original implementation-time checks. Their pending real-MySQL verification gates are superseded by the completed verification recorded above.

# Workflow report/history correction

- Branch: main; root README.md and unrelated changes preserved; no push.
- Root causes: customer detail projection suppressed persisted reports and notes; technician reads required an active assignment even for terminal requests; assignment/acceptance emitted events with unchanged lifecycle state. Binding and persistence already retained transition notes.
- Corrected behavior: report lookup by unique request ID for authorized customer/admin/historical technician detail; terminal writes remain forbidden. Terminal technician reads require a canonical historical assignment; nonterminal reads still require current assignment. Transition notes are visible to authorized viewers. Same-state events are skipped at the service boundary and rejected by the history constructor.
- Files: WorkflowService, WorkflowRepositories, ServiceStatusHistory constructor guard, Stage3WorkflowTest, BACKEND_API_CONTRACT.md, this note, and the local Postman collection/environment/instructions. Postman includes no secrets and retains records.
- Coverage: three new regression methods cover completed/cancelled historical reports, terminal write denial and unrelated-user denial, note persistence/projection, and same-state history rejection. Existing reassignment, advanced acceptance, report and all-transition tests have stronger assertions.
- Test audit: 424621b is the older commit (82 test methods); 0522812 adds Phase1ContractTest with ten methods (92 total). git diff shows zero removed test methods; login/OTP/staff fixtures were adapted, not removed. There is no lost coverage to restore.
- Validation: final mvn clean test and mvn clean package both passed (95 tests each, zero failures/errors/skips). No skip flags. The first run caught timestamp precision in a new assertion; it now compares the persisted report before/after completion rather than an unrounded creation timestamp. Postman JSON parses and all 190 scripts compile; corrected assertions and empty exported secrets checked offline.
- Database: Flyway V1-V4 untouched, no new migration, no MySQL access or schema changes. Tests use isolated H2/MockMvc and the existing test-only V3 syntax adapter. valor_lift_db untouched; client repositories unchanged.
- Next step: rerun the corrected retained-record Postman flow against the independently verified local MySQL backend; no real-MySQL verification claimed for this fix.

# Phase-1 API/OpenAPI reconciliation

- Branch: main. README.md and unrelated changes preserved; no branch operations or push.
- Authority: ../TARGET_VALOR_SCHEMA_SPEC.md. Reconciled every active Stage 1-4 operation and added the seven missing specified customer/profile/assets/history and admin dashboard routes using existing canonical tables.
- Contract fixes: strict role-free registration schema; email-only admin/technician login; distinct OTP send/verify DTOs; typed authentication/profile/refresh/current-user/health envelopes; explicit null data for message-only success; restricted staff and notification request enums; integer 0-100 healthScore schema; admin-only workflow fields; nullable assignment/report/profile references; shared error envelope schemas; explicit stable operation IDs.
- Runtime corrections: request-ID/phone-bound locked OTP verification; verified/expiry/attempt/lock checks; registered active customer/profile gate; development/test-only OTP send with production refusal and resend throttling; transactional refresh rotation locking; owner-checked logout; JWT temporary-lock check and persisted failed-password accounting with a five-attempt/fifteen-minute policy; normalized email-only staff login; atomic optional registration profile fields. Existing login identity tests were adapted to the mandated email field and OTP tests now submit requestId; all existing test cases are retained.
- Missing target operations: customer profile GET/PUT, owned building GET/POST, owned lift GET, owned request history GET, dashboard summary GET. Customer identity is always JWT-derived; fields/IDs cannot override it. Dashboard permits ADMIN/SUPER_ADMIN while staff administration stays SUPER_ADMIN-only. Optional asset list paging/status filters preserve existing array response shapes and derived AMC coverage/lift count projections.
- Files: canonical auth/controller/DTO/OpenAPI configuration, asset/workflow DTO/controller/service/repository changes, Phase1ContractTest and adapted Stage1AuthApiTest, BACKEND_API_CONTRACT.md and this note. No entity or migration change.
- Verification: isolated H2/MockMvc only. The pre-existing test adapter removes only V3 STORED for H2; MySQL is never accessed. Final mvn clean test and mvn clean package both passed: 92 tests each, zero failures/errors/skips (all 82 existing cases plus 10 reconciliation cases). No skip flags. Two new OpenAPI tests cover all requested schema constraints and all success/error operations; eight new runtime tests cover corrected auth, OTP, ownership, filters, dashboard and lockout behavior.
- Database status: V1-V4 unchanged; no new migration, manual schema command, MySQL access or change to valor_lift_db. Existing runtime Hibernate validate and disabled SQL initialization remain unchanged. The old production update fallback remains overridden by its later validate setting.
- Client repositories: Android, Admin Portal, Website and Valor-technician unchanged. No providers, payments, inventory or other excluded features added.
- Client migration gate: real-MySQL endpoint verification of the reconciled code is still required. Production OTP requires a separately implemented provider and remains unavailable; dev/test behavior makes no delivery claim. Technician-field guidance preserves the earlier explicit runtime allowance for omitted nullable fields/default availability.
