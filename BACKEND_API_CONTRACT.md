# Stage 1–3 implementation contract

Implemented on branch `main`. Stage 2 covers assets; Stage 3 covers the service workflow below. Verification for Stage 3 uses H2 and MockMvc, with an explicit test-only adaptation for MySQL's STORED keyword. V1/V2 were reported verified on MySQL before this stage; V3 has not been run against MySQL during this implementation.

Implemented on `main`:

- `POST /api/v1/auth/register` — public CUSTOMER registration; submitted roles are rejected.
- `POST /api/v1/auth/login/customer`
- `POST /api/v1/auth/login/admin`
- `POST /api/v1/auth/login/technician`
- `POST /api/v1/auth/otp/send` — development-only generated behavior.
- `POST /api/v1/auth/otp/verify`
- `POST /api/v1/auth/refresh` — rotates and revokes the previous refresh token.
- `POST /api/v1/auth/logout` — revokes the supplied refresh token.
- `GET /api/v1/me`
- `GET /api/v1/health` — public; returns `ApiResponse` with generic `Healthy` message and `data.status=UP` only.

Swagger is public at `/swagger-ui.html` (redirects to `/swagger-ui/index.html`), `/swagger-ui/**`, `/v3/api-docs`, and `/v3/api-docs/**`. These paths and the health endpoint are verified with MockMvc without authentication.

Spring Security uses a canonical database-backed `UserDetailsService`: email lookup trims and lowercases; normalized international phone lookup is CUSTOMER-only. It returns the stored BCrypt hash internally and maps the single role to `ROLE_<role>`. Unknown, inactive, locked, temporarily locked, and passwordless identities are rejected generically. No fallback `InMemoryUserDetailsManager` is created; no hash is returned by a public endpoint.

Only registration, login, OTP, and refresh auth paths are public. Logout and other `/api/v1/**` routes require authentication; `/api/v1/admin/**` requires SUPER_ADMIN. Security 401/403 responses serialize `ApiResponse<T>`, including its timestamp, without exception details.

Stage 1 uses one canonical `users` table, one role per user, one-to-one customer/technician profiles, BCrypt passwords, hashed OTPs, hashed refresh tokens, signed BIGINT identifiers, Flyway V1, and Hibernate `validate`. Responses use `ApiResponse<T>`.

Tests use H2 in MySQL compatibility mode, execute Flyway V1, and validate the mapped entities with Hibernate. The development SUPER_ADMIN bootstrap is restricted to the `dev` profile, explicit `DEV_BOOTSTRAP_ENABLED=true`, and environment-provided credentials; it is idempotent and never logs or hardcodes credentials. Authentication failures are returned through the Stage-1 `ApiResponse<T>` error handler.

## Stage 2: customer assets

All responses use `ApiResponse<T>`. Successful operations return HTTP 200. No response serializes a JPA entity or user credentials. There are no unversioned aliases or CUSTOMER building/lift mutation endpoints.

| Method | Path | Access | Behavior |
|---|---|---|---|
| GET | `/api/v1/buildings` | ADMIN, SUPER_ADMIN | List buildings, including inactive history; calculated `activeLiftCount` |
| POST | `/api/v1/buildings` | ADMIN, SUPER_ADMIN | Create under a validated active `customerProfileId` |
| PUT | `/api/v1/buildings/{id}` | ADMIN, SUPER_ADMIN | Replace editable fields; owner remains unchanged |
| DELETE | `/api/v1/buildings/{id}` | ADMIN, SUPER_ADMIN | Set `is_active=false`; retain row and descendants |
| GET | `/api/v1/lifts` | ADMIN, SUPER_ADMIN | List lifts, including inactive history; calculated `amcCoverage` and `asOfDate` |
| POST | `/api/v1/lifts` | ADMIN, SUPER_ADMIN | Create under an active building and owner |
| PUT | `/api/v1/lifts/{id}` | ADMIN, SUPER_ADMIN | Replace editable fields; building remains unchanged |
| DELETE | `/api/v1/lifts/{id}` | ADMIN, SUPER_ADMIN | Set `is_active=false`; retain row and contracts |
| GET | `/api/v1/amc-contracts` | ADMIN, SUPER_ADMIN, CUSTOMER | Admin lists all; CUSTOMER query joins lift → building → profile → JWT user |
| POST | `/api/v1/amc-contracts` | ADMIN, SUPER_ADMIN | Create ACTIVE contract for an active lift/building/owner |
| PUT | `/api/v1/amc-contracts/{id}/renew` | ADMIN, SUPER_ADMIN | Replace term/plan, increment renewal count; keep contract identity and lift |

Write DTOs reject unknown properties, including submitted `customerId` on lifts, derived values and `isActive`. DELETE is the only exposed deactivation operation; repeated deactivation is idempotent. Building and lift PUT require their unchanged `customerProfileId`/`buildingId` and required name; omitted optional fields become null and omitted status uses ACTIVE. Ownership reassignment is rejected.

- Building writes: `customerProfileId`, `buildingName` required; optional `buildingType`, `address`, `city`, `state`, `pincode`, `emergencyContactName`, `emergencyContactPhone`, `status`. Status is nonblank free text of at most 20 characters, default ACTIVE, as the schema does not specify a building status enum.
- Lift writes: `buildingId`, `name` required; optional `liftNumber`, `model`, `manufacturer`, `capacity`, `floorCount`, `serialNumber`, `installationDate`, `location`, `currentStatus`, `warrantyStatus`, `warrantyStartDate`, `warrantyEndDate`, `lastMaintenanceDate`, `nextMaintenanceDate`, `healthScore`, `machineRoom`, `qrCode`, `specifications`. `currentStatus`: ACTIVE, DOWN, MAINTENANCE, OUT_OF_SERVICE. Counts are nonnegative, health score is 0–100, and warranty end cannot precede start.
- AMC creation: `liftId`, `amcNumber`, `plan`, `startDate`, `endDate` required; optional `coverageDetails`, `renewalDate`. Status and renewal count are server-managed. End cannot precede start; same-day terms are valid.
- AMC renewal: `plan`, `startDate`, `endDate` required; optional `coverageDetails`, `renewalDate`. The new start must be after the existing end, with new end at or after new start. CANCELLED/NON_AMC contracts cannot renew. Renewal increments `renewalCount`, resets reminder state, and leaves status ACTIVE. A future renewed term does not provide coverage before its start. The existing row is updated; no separate term-history table is implemented.

Views contain the corresponding asset fields, ID, owning profile/building/lift ID and creation/modification timestamps. Building/lift views include `isActive`; AMC views also include contractual `status`, `renewalCount`, `lastReminderSentAt`, calculated `covered`, and `asOfDate`. No user identity graph is included.

New operations reject inactive/locked owners, inactive or non-ACTIVE customer profiles, inactive buildings and inactive lifts. CUSTOMER AMC reads require an active account/profile and preserve access to their historical contracts after asset deactivation. Admin deactivation retains all records; it does not cascade changes to child rows. Parent and asset rows are locked during dependent writes/deactivation.

Coverage uses one service-supplied UTC business date per response. A lift has ACTIVE coverage only when an ACTIVE contract exists with inclusive `startDate <= asOfDate <= endDate`; otherwise NON_AMC. Contract `covered` uses the same date rule for that contract. Contract coverage is independent of asset deactivation and is calculated for historical rows too. Coverage and building active-lift counts are repository/service projections, never persisted columns.

Validation errors return 400, missing assets 404, inactive assets and uniqueness conflicts 409, missing authentication 401, and denied roles 403, all with safe `ApiResponse<T>` bodies. Invalid/inactive target customer profiles return generic 400; inactive CUSTOMER profiles cannot read AMCs (403).

V2 creates only `buildings`, `lifts`, and `amc_contracts`, with signed BIGINT identifiers, restrictive FKs, named checks, and the specified indexes. Hibernate scans only canonical auth/assets entities and remains `validate`; SQL initialization remains disabled. V1 was not modified. Neither MySQL database was accessed for this change.

## Stage 3: service workflow

All operations return `ApiResponse<T>`; successful requests return HTTP 200. Missing authentication returns 401, role/ownership violations 403, absent request/asset IDs 404, malformed/invalid input or missing mandatory notes 400, and lifecycle/report/uniqueness conflicts 409. Unexpected errors return a generic 500 without exception or secret details.

| Method | `/api/v1` path | Access | Response |
|---|---|---|---|
| POST | `/service-requests` | CUSTOMER, ADMIN, SUPER_ADMIN | Request detail with initial PENDING event |
| GET | `/service-requests/{id}` | Owning CUSTOMER, actively assigned TECHNICIAN, ADMIN, SUPER_ADMIN | Request, active assignment, immutable history and permitted report |
| GET | `/service-requests` | ADMIN, SUPER_ADMIN | Paged request summaries; optional status/priority filters |
| POST | `/service-requests/{id}/assignments` | ADMIN, SUPER_ADMIN | Detail after assignment/reassignment |
| POST | `/service-requests/{id}/assignments/{assignmentId}/accept` | Actively assigned TECHNICIAN only | Detail after acceptance |
| POST | `/service-requests/{id}/status` | Actively assigned TECHNICIAN, ADMIN, SUPER_ADMIN | Detail after transition |
| GET | `/technician/me/jobs` | TECHNICIAN | Own active-assignment request summaries; optional request-status filter |
| GET | `/technician/me/jobs/{id}` | Actively assigned TECHNICIAN | Job detail |
| POST | `/technician/me/jobs/{id}/report` | Actively assigned TECHNICIAN | Saved report; does not complete request or add a status event |

No unversioned aliases or second customer creation route exist. Pages use `items`, `page`, `size`, `totalElements`, `totalPages`; defaults are page 0 and size 20, maximum size 100, sorted by descending request ID. Technician job reads include only ASSIGNED/ACCEPTED assignments; completed/released assignments are not in that list. Customer/admin detail remains available for terminal requests.

### Request and response fields

- Creation requires `liftId`, nonblank `title` (max 200), nonblank `description`, and `serviceType`. Optional: `issueCategory` (100), `priority` (default MEDIUM), `customerRemarks` (2000), `preferredVisitDate`, `preferredTimeSlot` (80). Service types: ROUTINE_MAINTENANCE, BREAKDOWN, EMERGENCY, INSPECTION, INSTALLATION, MODERNIZATION. Priorities: LOW, MEDIUM, HIGH, EMERGENCY.
- CUSTOMER ownership is resolved exclusively from JWT user → active customer profile. `customerId` is always rejected; CUSTOMER also cannot supply `customerProfileId`, `internalAdminNotes`, or `estimatedCompletionMinutes`. ADMIN/SUPER_ADMIN must supply `customerProfileId` and may supply those admin fields. The active lift must belong to that customer's active building. Locked/inactive accounts and inactive profiles/assets are rejected for new operations.
- Assignment accepts `technicianProfileId` and optional `notes` (2000); technician profile/account must be active and have TECHNICIAN role. Acceptance uses path IDs only and verifies the active assignment ID and owner.
- Status accepts `toStatus` and optional `notes` (2000). CANCELLED and WAITING_FOR_PARTS require nonblank notes.
- Reports accept only nonblank `diagnosis`, `workPerformed`, `testingResult`, and optional `completionNotes`. Request, assignment and reporting-user IDs are always resolved server-side. Unknown write properties are rejected.
- Detail uses `request`, `activeAssignment`, `history`, `report`. DTOs contain scalar identifiers and workflow fields, never JPA identity graphs or credential fields. CUSTOMER does not receive internal admin notes, technician remarks, assignment/history notes, or the report. Only admin roles receive `internalAdminNotes`; actively assigned technicians can read/update the report.

### Lifecycle and atomicity

| Current | Allowed next |
|---|---|
| PENDING | ASSIGNED, CANCELLED |
| ASSIGNED | ACCEPTED, CANCELLED |
| ACCEPTED | ON_THE_WAY, CANCELLED |
| ON_THE_WAY | REACHED_SITE, CANCELLED |
| REACHED_SITE | DIAGNOSIS, CANCELLED |
| DIAGNOSIS | REPAIR_IN_PROGRESS, WAITING_FOR_PARTS, CANCELLED |
| REPAIR_IN_PROGRESS | WAITING_FOR_PARTS, TESTING, CANCELLED |
| WAITING_FOR_PARTS | REPAIR_IN_PROGRESS, CANCELLED |
| TESTING | COMPLETED, REPAIR_IN_PROGRESS, CANCELLED |
| COMPLETED / CANCELLED | None |

PENDING → ASSIGNED uses the assignment endpoint, not a bare status update. ASSIGNED → ACCEPTED can use technician acceptance or the status endpoint under the stated role/ownership policy. An assignment must be ACCEPTED before further progression; cancellation can release an unaccepted assignment.

Each workflow mutation locks the request row. Creation writes exactly one initial history event with null `fromStatus`; each accepted transition writes exactly one new immutable event. Completion verifies a nonblank report for the current request, current active assignment, and its technician user, then atomically sets `completedAt`, marks the assignment COMPLETED and adds one event. Admins cannot bypass that report requirement. Cancellation releases the active assignment if present and adds one event in the same transaction. Invalid operations leave status/history unchanged.

Reassignment is permitted only before terminal status. It releases and flushes the previous active row before creating another; repeated assignment to the same technician creates another retained row. It preserves the current request lifecycle and records a same-status history event. Accepting a replacement at an advanced lifecycle state records acceptance without regressing request status. A replacement must accept before progressing, and the current technician must update the report to the new assignment before completion. Previously released technicians lose access.

Reports are unique per request and updated in place before terminal status. Reassignment does not silently reattribute an old report. Terminal requests reject report edits, reassignment and further transitions. History entities are immutable and expose no update/delete API or repository operation.

### Migration and verification limits

V3 creates exactly `service_requests`, `technician_assignments`, `service_status_history`, `service_reports`; V1/V2 files remain unchanged. Signed BIGINT keys, VARCHAR enum checks, restrictive FKs and the approved indexes are used. `service_requests` has no direct technician FK. The stored generated `active_request_id` and unique index `uk_assignment_active_request` enforce one ASSIGNED/ACCEPTED assignment; the generated column has no Java field, and there is no request/technician pair uniqueness.

The test-classpath-only `H2WorkflowMigrationAdapter` reads the original migration resources and removes only `STORED` from V3 for H2 execution. Generated expressions, uniqueness, FKs, Hibernate validation and workflow behavior are exercised there. The committed production migration retains the exact MySQL STORED strategy. H2 migration checksums therefore differ for V3; the adapter is absent from the application JAR and must never be used with MySQL. These tests do not establish MySQL V3 startup or storage-engine behavior.

Notifications remain deferred until Stage 4. Visits, checklist items, parts, attachments, payments, inventory and client changes remain unimplemented. `Valor-technician` was not changed. `valor_lift_db` remains untouched.
