# DB-37 MySQL/Hikari Test Harness

This note describes the opt-in test-only harness prepared for the unresolved DB-36 webhook connection-eviction proof. It is not loaded by Spring automatically and was not executed while preparing this harness.

## Opt-in requirements

Provide all three variables explicitly in the test process:

- `DB37_MYSQL_URL`
- `DB37_MYSQL_USERNAME`
- `DB37_MYSQL_PASSWORD`

The JDBC URL must select a disposable schema named `valor_backend_db37_<unique_suffix>`, where the suffix contains only lowercase letters and digits. The harness rejects missing values, non-MySQL URLs, `valor_world_dev`, and schema names containing `prod`, `shared`, or `audit`.

The harness does not read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `.env`, or production profiles. A future test should call `fromEnvironmentIfOptedIn()` and skip when it returns empty; partial configuration fails closed. It creates a separate Hikari pool only when a future test explicitly calls `createDataSource(...)`, `createVerifiedDataSource(...)`, or the failure-injected variants.

## Preflight boundary

Before Flyway or application setup, a future integration test must explicitly create the DB37 pool and call `verifyConnectedSchema(...)`, preferably through `createVerifiedDataSource(...)` or `createVerifiedFailureInjectedDataSource(...)`, which closes the pool if preflight fails. That check must confirm `select database()` equals the approved disposable schema. URL validation alone is not proof of server or schema isolation.

The failure-injected topology is `HikariDataSource -> failReleaseLock(DataSource) -> MysqlDataSource -> MySQL connection`. Production code therefore receives Hikari's pooled connection proxy, while the underlying MySQL connection proxy intercepts the targeted prepared statement. Hikari can receive the same pooled connection object from the application and retire it through `evictConnection(...)`.

Because Spring Boot and Flyway can initialize a datasource during context startup, this preflight must occur in a separate explicit bootstrap step or a test context whose datasource is supplied only after preflight. This task does not claim that the existing `WebhookMysqlSpringLifecycleTest` is safe; it still activates `prod` and must not be run.

## Failure injection

`Db37MysqlTestHarness.failReleaseLock(...)` is a test-only JDBC proxy. It delegates to the real connection and intercepts only a prepared statement whose normalized SQL is `select release_lock(?)`, throwing a synthetic `SQLException` from `executeQuery()`. The failure-injected Hikari factory places this proxy beneath Hikari, rather than replacing the datasource seen by production code. MySQL advisory locks, Hikari physical connections, and Spring transaction synchronization would be real when a future isolated integration test uses it. The injected release failure itself is simulated, not a server failure.

## Future opt-in command

After an operator has supplied a verified disposable DB37 schema and credentials without exposing them in source or logs, a future test may be selected explicitly, for example:

```text
mvn -Dtest=Db37WebhookLockEvictionTest test
```

The future test must fail closed when the variables are absent, perform the schema preflight before Spring/Flyway initialization, and clean up only its own disposable resources. No such command was run for DB-38.

## Status

DB-36 remains open. This harness prepares guarded test infrastructure only; it does not prove Spring `afterCompletion`, physical Hikari retirement, connection-ID replacement, or advisory-lock release after a failed `RELEASE_LOCK` until the isolated integration test executes successfully.
