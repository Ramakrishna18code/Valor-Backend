# Stage 1 implementation contract

Verified on branch `main`. This file records implemented Stage-1 facts only. No Stage-2 domain is implemented here.

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

Deferred and unimplemented on this branch: buildings, lifts, AMC contracts, service requests, technician jobs, service reports, notifications, client changes, payments, inventory, and all later domain APIs. Old unversioned authentication mappings are not runtime aliases. `valor_world_dev` is the new database; `valor_lift_db` remains untouched.
