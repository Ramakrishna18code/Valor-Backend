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

Stage 1 uses one canonical `users` table, one role per user, one-to-one customer/technician profiles, BCrypt passwords, hashed OTPs, hashed refresh tokens, signed BIGINT identifiers, Flyway V1, and Hibernate `validate`. Responses use `ApiResponse<T>`.

Tests use H2 in MySQL compatibility mode, execute Flyway V1, and validate the mapped entities with Hibernate. The development SUPER_ADMIN bootstrap is restricted to the `dev` profile, explicit `DEV_BOOTSTRAP_ENABLED=true`, and environment-provided credentials; it is idempotent and never logs or hardcodes credentials. Authentication failures are returned through the Stage-1 `ApiResponse<T>` error handler.

Deferred and unimplemented on this branch: buildings, lifts, AMC contracts, service requests, technician jobs, service reports, notifications, client changes, payments, inventory, and all later domain APIs. Old unversioned authentication mappings are not runtime aliases. `valor_world_dev` is the new database; `valor_lift_db` remains untouched.
