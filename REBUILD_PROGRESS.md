# Stage 1 rebuild progress

- Branch: `main`
- Completed: runtime configuration, Flyway V1 identity/auth schema, canonical auth model, JWT role authentication, registration, development OTP, refresh rotation/revocation, logout and current-user route.
- Commands run: `mvn clean test`, `mvn clean package`.
- Tests/build: 14 tests passed, 0 failures, 0 errors, 0 skipped; both Maven commands passed. H2 MySQL-mode test profile runs Flyway V1 and Hibernate `validate`.
- Coverage: registration/customer profile atomicity, role rejection/login boundaries, normalization/uniqueness, inactive/locked accounts, BCrypt storage, hashed OTP and attempt lock, hashed/rotated/revoked refresh tokens, logout, `/api/v1/me` secret exclusion, old endpoint absence, and dev bootstrap guards.
- Development bootstrap: credentialed test-only dev integration verifies one SUPER_ADMIN, BCrypt persistence, no plaintext password, idempotent rerun, and hash preservation; non-dev/opt-in guards remain covered.
- Error envelope: tested for 400, 401, 403, 409, and safe 500 handler behavior; the future `/api/v1/admin/**` rule requires SUPER_ADMIN and returns a secret-free `ApiResponse<T>` 403.
- Remaining blockers: none for the two requested verification gaps; Stage 2 remains deferred.
- `valor_lift_db` was not accessed or modified.
