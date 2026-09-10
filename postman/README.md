# Valor Phase-1 local Postman verification

These files verify the reconciled API against an already-running local development backend. They do not start the backend, connect to MySQL directly, run migrations, seed SUPER_ADMIN or change application configuration. No real credentials, tokens or OTPs are included.

## Import and run

1. Use the current Postman desktop app with collection scripts enabled. Import `Valor_Phase1_Local.postman_collection.json` and `Valor_Local.postman_environment.json` using **Import > Files**.
2. Select **Valor Local - private development only** in the environment selector. Keep the collection and environment private; do not publish/share them.
3. Confirm independently that the backend is running on `http://localhost:8081`, profile `dev`, with MySQL database `valor_world_dev` and successful Flyway V1-V4/Hibernate validation. Health deliberately exposes no database details, so a passing health request cannot prove which database is connected. Do not point the backend at `valor_lift_db`.
4. In the environment's local values, set these three **secret** variables. Leave their shared/exported values empty:
   - `superAdminPassword`: the existing development SUPER_ADMIN API account password. Default `superAdminEmail` is `admin@valor.local`; change the local email if your account differs. This is not a MySQL root password.
   - `customerPassword`: choose a development-only password for the two newly created customers.
   - `technicianPassword`: choose a development-only password for the newly created technician.
   Use nontrivial, unique-to-local-testing passwords, within BCrypt's 72-byte limit. Do not paste credentials into request bodies or scripts. JSON serialization supports quotes and backslashes in these values.
5. Keep `baseUrl=http://localhost:8081`. The scripts only allow HTTP localhost or 127.0.0.1 on port 8081. Run from the desktop Collection Runner, not a cloud monitor, scheduled run or shared CI runner.
6. Open the collection menu and select **Run collection**. Select **all 94 requests**, preserve their order, use **one iteration**, and do not attach a data file. Use **Keep variable values** if offered to retain generated IDs locally after the run. Disable response persistence/save options if offered. Do not export response-bearing run reports or the populated environment.
7. Run the collection. All tests must pass, including requests intentionally expecting 400, 401, 403/404 or 409. An HTTP error on a named negative test is expected; its tests must still be green. Unexpected statuses, envelopes, secret leakage, missing IDs or ownership failures stop the collection. Fix the cause and restart the full collection at request 01; do not resume a partial run with mixed IDs.
8. On success, inspect the generated non-secret IDs locally if needed. Current and previous session-token environment values are cleared after their revocation/reuse checks. Clear the three password local values when finished. Keep only the supplied empty-secret environment JSON under source control; never overwrite it with a populated export.

The pre-request and stop-on-failure scripts require `pm.execution.skipRequest()` and `pm.execution.setNextRequest()`. Request ordering works in the Collection Runner, not when manually clicking individual requests. See [Postman's workflow documentation](https://learning.postman.com/docs/tests-and-scripts/running-collections/building-workflows) and [execution API reference](https://learning.postman.com/v11/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-execution).

## Run data and environment

Request 01 resets prior run IDs/tokens without removing variable metadata, then generates a timestamp/UUID run ID, customer/technician emails, employee ID, contract number, dates and two distinct canonical-format phone strings. Emails use `example.test`. Phones use synthetic `+1555` numbers; they match the application's canonical international format but are not claims of routability. No OTP, SMS or other provider request is sent. A rare existing-phone collision should fail registration; start a fresh run to generate new identifiers.

The requested environment variables are present. Additional blank variables support assertions: `adminUserId`, `customerUserId`, `runId`, `employeeId`, AMC dates/number, report/history/read-state values, old refresh tokens, and an `otherCustomer` account plus alternate-branch asset/workflow IDs. The second customer reuses the local `customerPassword` only for this development verification. Password and token variables have secret type; their distributed values are empty. Generated IDs are required to be positive safe integers so scripts never silently round a BIGINT value.

The scripts have no console output. Assertions compare booleans rather than printing response objects, passwords or token values. Postman itself can display request/response bodies and Authorization headers: masking a variable is not encryption of traffic or a guarantee that a console/export is secret-free. Do not share those surfaces or exports.

## Coverage and retained records

The ordered flow checks health, SUPER_ADMIN/TECHNICIAN login, customer registration without role, typed current-user ownership, staff provisioning, buildings, lifts, AMC ownership and derived coverage/lift counts. Real cross-owner probes use the second customer; role probes use CUSTOMER/TECHNICIAN and unauthenticated requests.

The main job exercises:

`PENDING -> ASSIGNED -> ACCEPTED -> ON_THE_WAY -> REACHED_SITE -> DIAGNOSIS -> REPAIR_IN_PROGRESS -> WAITING_FOR_PARTS -> REPAIR_IN_PROGRESS -> TESTING -> REPAIR_IN_PROGRESS -> TESTING -> COMPLETED`.

A retained reassignment to the same technician verifies a new authoritative assignment ID and rejection of the old acceptance ID. Reassignment preserves status-history count; assignment rows retain that operational history. Every returned status event must have distinct from/to statuses (except the initial null from-status). WAITING_FOR_PARTS checks the returned notes against the submitted notes. Every transition checks request/customer/technician IDs, the single active-assignment projection and exactly one new history event. The technician inbox must contain exactly one active job during the main assignment. Completion is first rejected for both technician and SUPER_ADMIN without a report; a follow-up read verifies no status/history change. Report creation is verified not to complete the request. Completion then requires the linked report, sets completedAt, removes the active assignment and writes exactly one completion event.

The second customer's job covers the alternate `DIAGNOSIS -> WAITING_FOR_PARTS` edge and completes with a separate report. Together the two jobs cover all non-cancellation lifecycle edges through TESTING and COMPLETED, including testing rework. Cancellation branches are not exercised by this requested completion flow.

Notification tests verify IN_APP/PENDING creation, rejection of EMAIL/SMS/PUSH and non-admin creation, recipient-only inbox/read, unchanged readAt on reread, and no delivery timestamp claim. Every request checks expected HTTP status, ApiResponse success/status consistency, envelope fields, recursively absent password/hash fields and absence of configured password values. Non-authentication responses must not expose token fields.

All four sessions (SUPER_ADMIN, primary customer, technician, second customer) rotate their refresh token, reject the previous token, and verify the rotated access-token identity. They then log out and reject reuse of the revoked refresh token. The current backend does not invalidate already-issued JWT access tokens at logout; the collection deliberately tests refresh revocation, not an unsupported immediate JWT invalidation claim.

A successful run creates and retains one technician, two customers, two buildings, two lifts, one AMC, two service requests, three assignment rows (including the released row), two reports, their history and one notification. Authentication/refresh records are retained. No physical-delete or deactivation request, destructive SQL or database cleanup is included. Re-running creates new retained records; it does not erase previous runs. A failed run may leave partial records and sessions. Do not delete them to make tests pass; inspect the failure and use fresh run identifiers.

## Verification limits

These requests verify API-visible behavior. The detail API exposes a single active assignment, not all assignment rows, so the collection cannot independently prove the MySQL unique index or count hidden active rows. It checks the authoritative projection, job count and stale-assignment denial; database constraint/concurrency verification remains a separate migration/integration-test concern.

The collection has not been run against your backend as part of artifact creation. Offline JSON/script validation does not establish real-MySQL API success. A completed local run with the independently confirmed MySQL-backed process is required before reporting that verification.

Offline artifact checks passed: official Postman collection v2.1 JSON Schema validation; environment JSON and empty-secret/type checks; compilation of all 190 JavaScript scripts; offline execution of all 94 pre-request scripts, including 63 JSON request bodies with synthetic quote/backslash-containing values; local-URL and missing-password guards. Response assertions have not been executed against a running backend.
