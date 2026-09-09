# Stage 1 rebuild progress

- Branch: `main`
- Completed: runtime configuration, Flyway V1 identity/auth schema, canonical auth model, JWT role authentication, registration, development OTP, refresh rotation/revocation, logout and current-user route.
- Commands run: `mvn clean test`, `mvn clean package`.
- Tests/build: 23 tests passed, 0 failures, 0 errors, 0 skipped in both `mvn clean test` and `mvn clean package`. H2 MySQL-mode test profile runs Flyway V1 and Hibernate `validate`.
- Coverage: registration/customer profile atomicity, role rejection/login boundaries, normalization/uniqueness, inactive/locked accounts, BCrypt storage, hashed OTP and attempt lock, hashed/rotated/revoked refresh tokens, logout, `/api/v1/me` secret exclusion, old endpoint absence, and dev bootstrap guards.
- Development bootstrap: credentialed test-only dev integration verifies one SUPER_ADMIN, BCrypt persistence, no plaintext password, idempotent rerun, and hash preservation; non-dev/opt-in guards remain covered.
- Error envelope: tested for 400, 401, 403, 409, and safe 500 handler behavior; the future `/api/v1/admin/**` rule requires SUPER_ADMIN and returns a secret-free `ApiResponse<T>` 403.
- Remaining blockers: none for the two requested verification gaps; Stage 2 remains deferred.
- `valor_lift_db` was not accessed or modified.
- Runtime security fix: public generic `/api/v1/health`, Swagger redirect/UI and OpenAPI JSON; canonical database-backed UserDetailsService replaces Boot's generated fallback user. Nine new tests cover public paths, protected paths/logout, invalid JWT, CUSTOMER admin-route denial, no in-memory fallback, normalized email/phone lookup, BCrypt/role mapping, and account rejection.
- Files: extracted `AuthConfig`, added `CanonicalUserDetailsService`, `HealthController`, and `RuntimeSecurityTest`; updated security exception handling, authenticated logout test, and API contract. No migrations or MySQL schema/data were changed; backend was not started during this fix.
