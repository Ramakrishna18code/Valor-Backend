# Stage 1 and Stage 2 implementation contract

Implemented on branch `main`. Stage 2 contains buildings, lifts and AMC contracts only. Verification below uses H2 and MockMvc; V2 has not been run against MySQL during this implementation.

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

Deferred and unimplemented: service requests, technician jobs, service reports, notifications, client changes, payments, inventory, and all later domain APIs. `Valor-technician` was not changed. `valor_lift_db` remains untouched.
