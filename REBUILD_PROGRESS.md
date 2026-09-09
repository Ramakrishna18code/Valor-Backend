# Valor rebuild progress

- Branch: main. Unrelated README.md changes preserved and excluded; no branch changes or push.
- Phase-1 operational closure implementation completed: SUPER_ADMIN-only staff creation and idempotent soft deactivation. ADMIN has no profile; TECHNICIAN user/profile creation and deactivation are atomic. Customer/SUPER_ADMIN management and reactivation are not exposed.
- Files: StaffController, StaffService, AuthErrorHandler, AuthComponents session-state guard, StaffProvisioningTest, BACKEND_API_CONTRACT.md and this progress note.
- Migration status: no migration added or changed. Existing V1-V4 and canonical tables are unchanged; Hibernate validate and disabled SQL initialization remain effective.
- Commands: mvn clean test and mvn clean package both passed with 79 tests each (69 existing plus 10 staff integration tests), zero failures/errors/skips. No skip flags used.
- Coverage: role enforcement and safe 401/403/400/404/409 responses; normalized email uniqueness; BCrypt-only storage; technician fields/defaults; duplicate employee rollback; no admin profile; user/profile deactivation; idempotency; self/customer/SUPER_ADMIN protection; rejected login and JWT access after deactivation; refresh denial with token rows retained.
- Static review: staff routes use only /api/v1/admin/users and the existing SUPER_ADMIN security rule. No new identity entity, table, plaintext runtime credential, legacy alias or deletion operation. Existing retired legacy source stays excluded by explicit scans. A pre-existing production update fallback is overridden by its later validate value; runtime configuration was not changed.
- MySQL: V1-V4 verification was reported by the user. No MySQL access or backend startup occurred in this task. Real-MySQL endpoint verification is still required for this code-only change. valor_lift_db remained untouched.
- Client repositories (Android, Admin Portal, Website and Valor-technician) remain unchanged. External notification providers and other deferred features remain unimplemented.
- Blockers: none for implementation; pending real-MySQL endpoint verification. Next step: verify staff provisioning/deactivation on the intended MySQL runtime.
