# Backend documentation

This index points to maintained backend references. Source code, migrations, and the audit report remain authoritative when a document and implementation differ.

| Area | Document | Purpose |
|---|---|---|
| API | [Backend API contract](backend/BACKEND_API_CONTRACT.md) | Verified route groups, authorization, validation, and response conventions. |
| Service finance | [Service finance and customer dashboard](backend/SERVICE_FINANCE_AND_DASHBOARD.md) | Current route-to-screen mapping, backend decision boundaries, held policies, and verification limits. |
| Customer | [Customer backend](backend/CUSTOMER_BACKEND.md) | Customer identity, ownership, assets, service, commerce, and support flows. |
| Technician | [Technician backend](backend/TECHNICIAN_BACKEND.md) | Technician assignment, jobs, inspections, parts, tracking, and authorization. |
| Protection | [Protection management API](backend/PROTECTION_MANAGEMENT.md) | Provider/product catalog, owner-scoped policies, lift quote requests, claims, renewals, expiry notices, and insurer decision boundaries. |
| Payments | [Payments](backend/PAYMENTS.md) | Checkout, refunds, webhooks, reconciliation, configuration, and evidence limits. |
| Database | [Database structure](database/DB_BACKEND_STRUCTURE.md) | Configuration, schema relationships, invariants, safe change workflow, and DB-01–DB-33 summary. |
| Database | [Schema reference](database/SCHEMA.md) | Short table and relationship reference; defer to the structure guide for lifecycle and audit evidence. |
| Audit | [DB-01–DB-33 final report](database/audits/DB-01-DB-33-FINAL-AUDIT-REPORT.md) | Full audit narrative and PASS WITH GAPS disposition. |
| Policy | [Privacy policy](policies/PRIVACY_POLICY.md) | Repository-supported data-processing statements and owner-review items. |

`docs/pincode-import-format.md` remains a separate PIN-directory roadmap because it documents proposed operational work rather than the current API or schema contract.

Use the [repository documentation update guide](../../DOCUMENTATION_UPDATE_GUIDE.md) when changing a route, screen, state, or business rule.
