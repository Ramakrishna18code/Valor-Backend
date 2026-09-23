# Canonical backend API contract

Scope: canonical Stage 1-4 API on main, reconciled with ../TARGET_VALOR_SCHEMA_SPEC.md, plus the approved Scheduling / Service Visit contract below. Flyway V1-V4 remain unchanged; Scheduling / Service Visits are added in V5, Admin Settings in V6, request attachments/feedback in V7, payments/invoices/support tickets/AMC renewal requests/asset documents in V8, Razorpay/live-tracking storage in V9, and roles/permissions/audit logging in V10. This reconciliation is covered by automated tests and the V1-V10 migration chain/current `/api/v1` endpoint set has been verified against the local MySQL dev database.

## Common contract

All paths below have prefix `/api/v1`. Success is HTTP 200 with `ApiResponse<T>`: success, message, data, status, timestamp. Staff and domain views are flat DTOs; no identity entities or passwordHash/otpHash/tokenHash fields are serialized. Input passwords are writeOnly with password format in OpenAPI. Every operation has an explicit stable operationId. Shared ApiErrorResponse components describe safe 400 validation/authentication/OTP/refresh failures, 401 unauthenticated, 403 forbidden, 404 missing resources and 409 conflicts. Error data is null or an empty object; errors never return submitted secrets or internal exception details. Logout and asset deactivation retain their existing message envelopes with data=null, not a fabricated result object.

Default port: 8081. Swagger: /swagger-ui.html. OpenAPI: /v3/api-docs. Public health: GET /api/v1/health with data.status=UP. Other routes require authentication except register, login, OTP send/verify and refresh. Runtime configuration is unchanged: Flyway enabled, Hibernate validate, SQL initialization disabled; local development may load optional `.env` values through Spring config import, while production requires external credentials and never enables development bootstrap.

## Authentication and identity

| Method/path | Input | Data DTO / behavior |
|---|---|---|
| POST /auth/register | email?, phone?, password?, fullName; alternatePhone?, companyName?, address? | Authentication; atomic CUSTOMER and customer profile creation only |
| POST /auth/login/customer | identity (normalized email or international phone), password | Authentication, CUSTOMER only |
| POST /auth/login/admin | email, password | Authentication, ADMIN or SUPER_ADMIN only |
| POST /auth/login/technician | email, password | Authentication, TECHNICIAN only |
| POST /auth/otp/send | phone only | OtpSent: requestId, expiresAt, developmentOnly, otp (development/test only) |
| POST /auth/otp/verify | phone, otp, requestId, all required | Authentication for the matching registered active CUSTOMER |
| POST /auth/refresh | refreshToken | Rotation: accessToken, refreshToken |
| POST /auth/logout | refreshToken; authenticated user must own it | null data; success message |
| GET /me | bearer JWT | CurrentUser: userId, role, email, phone, nullable customerProfile and technicianProfile |

Authentication data contains accessToken, refreshToken, role, userId, nullable customerProfile and technicianProfile. CustomerSummary contains id, fullName, alternatePhone, companyName, address, status, active. TechnicianSummary contains id, employeeId, assignedArea, specialization, availabilityStatus, active. Administrators have no profile. No tokens appear in /me.

Registration does not advertise role and rejects any submitted role, including null, through strict unknown-field validation. Email is trimmed/lowercased; phone is trimmed with spaces/hyphens/parentheses removed and must be canonical +international digits. A phone identity or email/password identity is required. Password storage is BCrypt; refresh-token storage is SHA-256. Duplicate normalized identities return 409. Login failures for an active matching-role account increment a persisted counter under a user-row lock; five wrong passwords temporarily lock it for fifteen minutes. Successful login resets the counter and temporary lock. Staff login now requires email rather than the old identity field; customer login preserves identity.

OTP send is available only under dev/test, never with prod active. No production delivery provider exists; other profiles reject send before persistence. The generated code is never logged by the service and only its BCrypt hash is persisted. The response requestId is the existing otp_verifications.id, and verification locks that row and checks both ID and phone, unverified state, expiry, attempt budget and lock state. Codes expire after five minutes; three wrong attempts lock the attempt for fifteen minutes. Resending an outstanding attempt is throttled for sixty seconds and during lockout. Successful verification requires an existing active CUSTOMER with an active profile; it never creates a profileless identity. Refresh rotation locks the token row, revokes the previous token atomically and checks account state before issuing a new session. Logout cannot revoke another user's token. Deactivated accounts cannot use login, JWT or refresh.

## Staff provisioning

| Method/path | Role | Input / data |
|---|---|---|
| POST /admin/users | SUPER_ADMIN | StaffCreateRequest -> StaffResponse |
| DELETE /admin/users/{userId} | SUPER_ADMIN | StaffResponse with active=false |

StaffCreateRequest: email, password, role (ADMIN/TECHNICIAN only, example TECHNICIAN), employeeId?, assignedArea?, specialization?, availabilityStatus?. Technician guidance calls for these fields; existing runtime permits nullable employeeId/area/specialization and defaults availability to AVAILABLE. ADMIN must omit profile fields. Availability values are AVAILABLE, BUSY, OFF_DUTY, ON_LEAVE. StaffResponse contains userId, email, role (ADMIN/TECHNICIAN only), active and applicable technicianProfileId/employeeId/assignedArea/specialization/availabilityStatus; no password fields. Staff creation/deactivation is atomic, retains related rows, is idempotent for deactivation and disallows customer/SUPER_ADMIN management and self-deactivation. No reactivation route exists. Development SUPER_ADMIN bootstrap remains profile/opt-in controlled and is not an API.

## Admin roles, permissions, and audit log

Roles and permissions are database-backed in V10 and extend the existing JWT role model. JWT roles remain authoritative for CUSTOMER and TECHNICIAN application access. Admin permission checks add granular backend authorization for active Admin modules; SUPER_ADMIN keeps all permissions and cannot be edited through the role-permission API.

| Method/path | Authorization | Input / data |
|---|---|---|
| GET /admin/permissions | `PERM_ROLE_READ` | `PermissionView[]` |
| GET /admin/roles | `PERM_ROLE_READ` | `RoleView[]` |
| GET /admin/roles/{name} | `PERM_ROLE_READ` | `RoleView` |
| PUT /admin/roles/{name}/permissions | `PERM_ROLE_WRITE`, SUPER_ADMIN actor only | `PermissionUpdate` -> `RoleView` |
| GET /admin/audit-logs | `PERM_AUDIT_READ` | filters -> `PageView<AuditView>` |
| GET /admin/audit-logs/{id} | `PERM_AUDIT_READ` | `AuditView` |

Role names are the existing application roles: `SUPER_ADMIN`, `ADMIN`, `CUSTOMER`, and `TECHNICIAN`. Permission codes are uppercase capability names for implemented Admin capabilities, including customer/assets, service requests/visits, AMC, payment/refund, transactions, reports/export, notifications, settings, role management, and audit reading. The seeded ADMIN role can read roles but cannot write roles or read audit logs unless a SUPER_ADMIN explicitly grants those permissions. CUSTOMER and TECHNICIAN receive no Admin permissions.

`PermissionView` contains `id`, `code`, `description`, and `category`. `RoleView` contains `id`, `name`, `description`, `enabled`, `systemRole`, `permissions`, `createdAt`, and `updatedAt`. `PermissionUpdate` contains `permissions`, a complete replacement list of valid permission codes. Attempts to edit `SUPER_ADMIN`, grant unknown permissions, or call role-write operations as a non-SUPER_ADMIN return safe 400/403 envelopes. A user-supplied actor or role is never trusted.

Audit logs are append-only through normal application APIs. No update/delete audit API exists. `AuditView` contains `id`, nullable `actorUserId`, nullable `actorRole`, `action`, `entityType`, nullable `entityId`, `resultStatus`, nullable `summary`, nullable `beforeSummary`, nullable `afterSummary`, and `createdAt`. Filters support `actorUserId`, `role`, `action`, `entityType`, `entityId`, `dateFrom`, `dateTo`, `page`, and `size`. Results are ordered newest first by createdAt/id. Audited mutations include staff create/deactivate, customer create/update/deactivate/reactivate, building and lift create/update/deactivate, AMC create/renew and renewal-request state changes, service-request create/assignment/reassignment/status changes, service-visit create/update/technician-change/cancel, visit-change-request create/approve/reject, Admin notification creation, document deletes, Admin settings updates, payment refund requests, transaction/report exports, and role-permission changes. Audit summaries are intentionally compact and omit passwords, JWTs, refresh tokens, API keys, Razorpay secrets, OTP values, card data, CVV and PIN values.

## Admin workflow directories

| Method/path | Authorization | Page items / operation ID |
|---|---|---|
| GET /admin/technicians | ADMIN, SUPER_ADMIN | TechnicianDirectoryEntry / getAdminTechnicians |
| GET /admin/customers | ADMIN, SUPER_ADMIN | CustomerDirectoryEntry / getAdminCustomers |

Both use `/api/v1`, HTTP 200 `ApiResponse<PageView<T>>`, with data `{items, page, size, totalElements, totalPages}`. Query parameters: page=0 (nonnegative), size=20 (1-100), optional q (trimmed, case-insensitive literal substring, maximum 254 characters), optional active=true/false. Invalid parameters or an offset greater than Integer.MAX_VALUE return 400. Empty results return items=[] and zero totals; pages beyond the last page retain matching totals and return items=[]. Ordering is profile ID ascending. No user-controlled sorting is accepted.

TechnicianDirectoryEntry: userId, email, active, technicianProfileId, employeeId, assignedArea, specialization, availabilityStatus (AVAILABLE/BUSY/OFF_DUTY/ON_LEAVE). Search covers email, employeeId, assignedArea and specialization. CustomerDirectoryEntry: userId, customerProfileId, fullName, email, phone, active, status (ACTIVE/INACTIVE/SUSPENDED). Search covers fullName, email and canonical phone. Nullable model fields remain null. Only canonical profiles with their matching user role are included.

active is the conjunction of users.is_active and the profile's is_active. Omitted includes both; false matches either inactive flag. Customer status is returned separately. Active directory results do not guarantee workflow eligibility: lock state, customer status, asset state and assignment rules remain validated by existing write services. Use customerProfileId for service-request ownership and technicianProfileId for assignments; userId identifies the account, not the profile.

Only these exact GET routes additionally admit ADMIN under the admin namespace; staff provisioning/deactivation remains SUPER_ADMIN-only. Unauthenticated requests return the existing 401 envelope; CUSTOMER and TECHNICIAN receive 403. Shared OpenAPI error schemas and typed page responses apply. Queries use Criteria scalar projections and a count query, with no entity graph serialization or per-row user loading. Passwords, authentication hashes/tokens, lock/login audit fields, customer address/company/alternate contact/rating and unrelated technician metrics are intentionally omitted. Email and customer phone are included only for authorized selection. These reads add no tables, migrations, fake records or write operations.

## Admin customer management

ADMIN and SUPER_ADMIN can manage canonical CUSTOMER accounts and profiles through `/api/v1/admin/customers`. Customer records are retained permanently; lifecycle operations change account/profile state and never delete users, profiles, assets, AMCs, service requests, visits, history or reports.

| Method/path | Authorization | Input / data |
|---|---|---|
| GET /admin/customers | ADMIN, SUPER_ADMIN | `q?`, `active?`, `page=0`, `size=20` -> `PageView<CustomerDirectoryEntry>` |
| POST /admin/customers | ADMIN, SUPER_ADMIN | `CustomerCreateRequest` -> `AdminCustomerDetail` |
| GET /admin/customers/{customerProfileId} | ADMIN, SUPER_ADMIN | `AdminCustomerDetail` |
| PUT /admin/customers/{customerProfileId} | ADMIN, SUPER_ADMIN | `CustomerUpdateRequest` -> `AdminCustomerDetail` |
| POST /admin/customers/{customerProfileId}/deactivate | ADMIN, SUPER_ADMIN | optional reason -> `AdminCustomerDetail` with inactive state |
| POST /admin/customers/{customerProfileId}/reactivate | ADMIN, SUPER_ADMIN | optional reason -> `AdminCustomerDetail` with active state |

`CustomerCreateRequest` accepts `email`, `phone`, `password`, `fullName`, `hasLift`, `referralCode`, `alternatePhone`, `companyName`, and `address`. `fullName` and `password` are required. `hasLift` is an optional onboarding preference captured from the Admin create-customer screen's "Do you have a lift?" Yes/No choice; it does not create any lift or service request by itself. `referralCode` is optional; when omitted the backend generates a unique code, and when supplied the backend validates format and uniqueness. At least one canonical identity (`email` or `phone`) is required, and email/phone use the same normalization and duplicate checks as customer self-registration. Password input is writeOnly, limited by the existing BCrypt byte-length rule, and is never returned. The response never includes passwordHash, OTP data, refresh-token hashes, lock counters, deletedAt, or submitted credentials. Creation creates exactly one canonical `users` row with role CUSTOMER and one canonical `customer_profiles` row; it does not create buildings, lifts, AMCs, service requests or visits.

`CustomerUpdateRequest` accepts only profile fields already supported by customer self-service: `fullName`, `alternatePhone`, `companyName`, and `address`. Admin update cannot change email, phone, role, password, authentication state, profile ID, user ID, rating, or ownership identifiers. Unknown fields are rejected.

`AdminCustomerDetail` contains `userId`, `customerProfileId`, `email`, `phone`, `fullName`, nullable `hasLift`, `referralCode`, `alternatePhone`, `companyName`, `address`, `active`, `status`, `createdAt`, `updatedAt`, and an operational summary: `buildingCount`, `liftCount`, `serviceRequestCount`, plus lightweight `buildings`, `lifts`, and recent `serviceRequests` arrays where canonical relationships already exist. Building rows include id/name/type/city/buildingPreference/status/isActive. Lift rows include id/buildingId/name/liftNumber/currentStatus/isActive. Recent service-request rows use the existing `RequestView` shape. These summary arrays are read-only convenience projections; use the canonical asset and service-request APIs for writes.

Deactivation sets the canonical user inactive, sets the customer profile inactive, and sets profile status to `INACTIVE`. Authentication and refresh already reject inactive users, so deactivated customers cannot log in or rotate sessions. Existing bearer tokens become unusable on the next authenticated request because account usability is rechecked. Existing refresh-token rows are retained for audit/rotation semantics and remain rejected by account-state checks. Deactivation is idempotent and non-destructive; historical service requests, buildings, lifts, AMCs, visits and history remain visible to admins.

Reactivation sets the canonical user active, sets the customer profile active, and restores profile status to `ACTIVE`. It does not change existing passwords, identities, assets, service requests, assignments, visits, or history. Reactivation is idempotent. CUSTOMER and TECHNICIAN users cannot call these routes. ADMIN and SUPER_ADMIN have the same customer-management authority; staff management remains separately restricted to SUPER_ADMIN.

## Customer profile and owned assets

| Method/path | Role | Input / data |
|---|---|---|
| GET /customers/me | CUSTOMER | CustomerSummary including backend-owned `referralCode` |
| PUT /customers/me | CUSTOMER | fullName, alternatePhone?, companyName?, address? -> CustomerSummary |
| GET /customers/me/buildings | CUSTOMER | owned BuildingView[] |
| GET /customers/me/buildings/{id} | CUSTOMER owner | BuildingView |
| POST /customers/me/buildings | CUSTOMER | buildingName; buildingType/address/city/state/pincode/emergencyContactName/emergencyContactPhone optional -> BuildingView |
| PUT /customers/me/buildings/{id} | CUSTOMER owner | same fields as customer building creation -> BuildingView |
| DELETE /customers/me/buildings/{id} | CUSTOMER owner | non-destructive deactivate; null data |
| GET /customers/me/lifts | CUSTOMER | owned LiftView[] |
| GET /customers/me/lifts/{id} | CUSTOMER owner | LiftView |
| POST /customers/me/lifts | CUSTOMER owner of building | LiftWrite with buildingId and supported descriptive lift fields -> LiftView |
| PUT /customers/me/lifts/{id} | CUSTOMER owner | LiftWrite with unchanged buildingId and supported descriptive lift fields -> LiftView |
| DELETE /customers/me/lifts/{id} | CUSTOMER owner | non-destructive deactivate; null data |
| GET /customers/me/service-requests | CUSTOMER | status?, page=0, size=20 -> PageView<RequestView> |

Ownership always resolves from JWT; customer building creation/update accepts no customer/profile ID, status, or active flag. Customer lift creation/update accepts buildingId only to identify the owned parent building and never accepts customerProfileId, userId or owner IDs. Customers can only read, update or deactivate their own buildings and lifts; foreign assets return the safe missing-resource envelope rather than exposing ownership. Customer lift update cannot move a lift to another building. Customer asset deletes are soft deactivations and never remove buildings, lifts, AMCs, service requests, visits or history. Inactive profiles cannot initiate operations. Inactive buildings block new/updated lifts; inactive lifts cannot be used for new service requests. There is exactly one customer request-creation route: POST /service-requests.

## Admin assets and AMC

| Method/path | Role | Input / data |
|---|---|---|
| GET /buildings | ADMIN, SUPER_ADMIN | BuildingView[] |
| POST /buildings | ADMIN, SUPER_ADMIN | BuildingWrite -> BuildingView |
| PUT /buildings/{id} | ADMIN, SUPER_ADMIN | BuildingWrite -> BuildingView |
| DELETE /buildings/{id} | ADMIN, SUPER_ADMIN | deactivate; null data |
| GET /lifts | ADMIN, SUPER_ADMIN | LiftView[] |
| POST /lifts | ADMIN, SUPER_ADMIN | LiftWrite -> LiftView |
| PUT /lifts/{id} | ADMIN, SUPER_ADMIN | LiftWrite -> LiftView |
| DELETE /lifts/{id} | ADMIN, SUPER_ADMIN | deactivate; null data |
| GET /amc-contracts | ADMIN, SUPER_ADMIN, CUSTOMER owner | AmcView[] |
| POST /amc-contracts | ADMIN, SUPER_ADMIN | AmcWrite -> AmcView |
| PUT /amc-contracts/{id}/renew | ADMIN, SUPER_ADMIN | AmcRenew -> AmcView |

Admin GET /buildings, /lifts and /amc-contracts accept optional status, page and size filters. Omitting both page and size preserves the full existing array response; supplying either defaults page to 0 and size to 20, with size 1-100 and nonnegative page. Filters apply before paging and never widen customer AMC ownership.

BuildingWrite requires customerProfileId and buildingName; accepts buildingType, address, city, state, pincode, emergencyContactName, emergencyContactPhone, buildingPreference (`MAIN`, `SECONDARY`, `OTHER`), and status. BuildingView includes those fields, id, isActive, activeLiftCount, createdAt, updatedAt. Building ownership cannot be transferred by update.

LiftWrite requires buildingId and name; accepts liftNumber, model, manufacturer, capacity, floorCount, serialNumber, installationDate, location, currentStatus, warrantyStatus, warrantyStartDate, warrantyEndDate, lastMaintenanceDate, nextMaintenanceDate, healthScore, machineRoom, qrCode, specifications. customerId is rejected. LiftView includes these fields, id, isActive, amcCoverage, asOfDate, createdAt, updatedAt. `liftNumber` is globally unique when supplied; null is allowed. healthScore is a nullable JSON integer 0-100 in both directions (numeric Byte mapping to existing TINYINT); no String conversion or migration. Lift statuses: ACTIVE, DOWN, MAINTENANCE, OUT_OF_SERVICE. Derived amcCoverage is ACTIVE or NON_AMC. Ownership cannot be transferred by update.

AmcWrite requires liftId, plan, startDate, endDate; accepts coverageDetails, renewalDate, and an optional legacy amcNumber. When amcNumber is omitted, the backend generates the contract number and returns it in AmcView. AmcRenew requires plan/startDate/endDate and accepts coverageDetails/renewalDate. AmcView adds id, amcNumber, status, lastReminderSentAt, renewalCount, covered, asOfDate and timestamps. AMC statuses: ACTIVE, EXPIRED, NON_AMC, CANCELLED, RENEWED. Renewal must begin after the existing end date; endDate cannot precede startDate. Coverage uses one service-supplied business date and contract queries. Lift counts remain repository projections; no derived columns are introduced. Deactivation never deletes rows. New asset operations require active account/profile/building/lift parents.

## Service workflow

| Method/path | Role | Input / data |
|---|---|---|
| POST /service-requests | CUSTOMER, ADMIN, SUPER_ADMIN | CreateRequest -> Detail |
| GET /service-requests/{id} | owner CUSTOMER, current TECHNICIAN (historically assigned for terminal requests), ADMIN, SUPER_ADMIN | Detail |
| GET /service-requests | ADMIN, SUPER_ADMIN | status?, priority?, page=0, size=20 -> PageView<RequestView> |
| POST /service-requests/{id}/assignments | ADMIN, SUPER_ADMIN | technicianProfileId, notes? -> Detail |
| POST /service-requests/{id}/assignments/{assignmentId}/accept | assigned TECHNICIAN | Detail |
| POST /service-requests/{id}/status | assigned TECHNICIAN, ADMIN, SUPER_ADMIN | toStatus, notes? -> Detail |
| GET /technician/me/jobs | TECHNICIAN | status?, page=0, size=20 -> PageView<RequestView> |
| GET /technician/me/jobs/{id} | current TECHNICIAN; historically assigned TECHNICIAN for terminal requests | Detail |
| POST /technician/me/jobs/{id}/report | assigned TECHNICIAN | diagnosis, workPerformed, testingResult; completionNotes? -> ReportView |

CreateRequest requires title, description, serviceType. `liftId` is required for every non-`INSTALLATION` request and may be omitted only for `INSTALLATION` requests that create a new-lift installation request before a lift exists. Optional issueCategory, priority (MEDIUM default), customerRemarks, preferredVisitDate, preferredTimeSlot; customerProfileId is required for admin creation and forbidden for CUSTOMER. internalAdminNotes and estimatedCompletionMinutes are also admin-only and rejected from CUSTOMER. Customer ownership derives exclusively from JWT and must match the lift's building owner when a lift is supplied. Submitted customerId and unknown fields are rejected.

There is no separate "booking for friend" customer/request model. A signed-in customer can create an owned `INSTALLATION` request without `liftId` for the no-existing-lift flow. Friend/third-party booking would need a distinct product decision and contract before implementation.

Customer technician availability counts are exposed by `GET /customers/me/technician-counts?district=&state=`. The response contains real active technician profile counts for exact assigned-area matches: `districtCount` and `stateCount`; clients may display district first and fall back to state. No fake technician counts are returned.

Detail contains request (RequestView), nullable activeAssignment (AssignmentView), immutable history (HistoryView[]) and nullable report (ReportView). Reports are loaded by unique serviceRequestId independently of active assignment. Authorized customer owners, admins and technicians with historical assignment rows can read reports on COMPLETED/CANCELLED requests, including technician job detail. Nonterminal technician access still requires the active assignment; unrelated users remain forbidden. History notes are returned to all authorized viewers; internalAdminNotes remain admin-only. RequestView contains id/serviceId, customerProfileId/liftId, title/description/category, priority/status/serviceType, remarks, scheduling/completion/estimate and timestamps. Internal notes are hidden from customers. AssignmentView contains id, serviceRequestId, technicianProfileId, status, assignedByUserId, assignedAt, acceptedAt, releasedAt, notes. HistoryView contains id, nullable fromStatus, toStatus, changedByUserId, notes, changedAt. ReportView contains id, serviceRequestId, assignmentId, diagnosis, workPerformed, testingResult, completionNotes, reportedByUserId and timestamps. PageView contains items, page, size, totalElements, totalPages; page must be nonnegative and size is 1-100.

Request statuses: PENDING, ASSIGNED, ACCEPTED, ON_THE_WAY, REACHED_SITE, DIAGNOSIS, REPAIR_IN_PROGRESS, WAITING_FOR_PARTS, TESTING, COMPLETED, CANCELLED. Assignment statuses: ASSIGNED, ACCEPTED, REJECTED, RELEASED, COMPLETED. Priorities: LOW, MEDIUM, HIGH, EMERGENCY. Service types: ROUTINE_MAINTENANCE, BREAKDOWN, EMERGENCY, INSPECTION, INSTALLATION, MODERNIZATION. Java enums and V3 checks remain aligned.

The approved transition graph is unchanged. Request row locking coordinates assignment, reassignment, report, status and completion; the existing generated unique active-assignment index remains authoritative. Creation writes one initial PENDING event; every accepted transition writes one event. Reassignment retains released rows and allows the same technician later without a status-history event when lifecycle state is unchanged. Acceptance after advanced reassignment likewise does not emit a same-status event. Event creation rejects equal from/to states; the initial NULL -> PENDING event remains valid. Supplied transition notes are persisted and returned, including waiting notes and cancellation reasons. Cancellation requires a reason and releases the active assignment. WAITING_FOR_PARTS requires notes. TESTING -> COMPLETED requires a nonblank report for the current active assignment, even for admins; completion atomically records completedAt, completes the assignment and writes history. Report POST does not complete a job; terminal reports are immutable.

### Service Request Attachments and Feedback

| Method/path | Role | Input / data |
|---|---|---|
| GET /service-requests/{id}/attachments | owner CUSTOMER, ADMIN, SUPER_ADMIN | AttachmentView[] |
| POST /service-requests/{id}/attachments | owner CUSTOMER | multipart `file` -> AttachmentView |
| GET /service-requests/{id}/attachments/{attachmentId} | owner CUSTOMER, ADMIN, SUPER_ADMIN | file download |
| DELETE /service-requests/{id}/attachments/{attachmentId} | owner CUSTOMER uploader | void |
| GET /service-requests/{id}/feedback | owner CUSTOMER | nullable FeedbackView |
| PUT /service-requests/{id}/feedback | owner CUSTOMER | FeedbackWrite -> FeedbackView |

AttachmentView contains id, serviceRequestId, originalFilename, contentType, fileSize, uploadedByUserId and createdAt. Upload accepts JPEG, PNG, WebP and PDF only, uses the existing 10 MB multipart file limit, and validates content type, file size and basic file signatures server-side. Storage returns only opaque references internally; filesystem paths, storage credentials and provider details are never exposed to clients. Customer ownership derives from JWT and the Service Request owner, never from a submitted customerProfileId. Customers cannot access or delete another customer's attachment. Admin/SUPER_ADMIN may list/download from their normal operational view but do not use the customer upload/delete route.

Feedback belongs to one completed Service Request. FeedbackWrite contains rating 1-5 and optional comment up to 2000 characters. FeedbackView contains id, serviceRequestId, customerProfileId, rating, comment, createdAt and updatedAt. A customer can create or update feedback only for their own completed request. Active or uncompleted requests return conflict, foreign requests are forbidden, and repeated submissions update the existing feedback row rather than creating duplicates. No separate Admin feedback-moderation module exists.

### Service Report PDF Download

| Method/path | Role | Input / data |
|---|---|---|
| GET /service-requests/{id}/report.pdf | owner CUSTOMER, assigned/historical TECHNICIAN, ADMIN, SUPER_ADMIN | PDF download |

The PDF endpoint renders the persisted structured `ServiceReport` for the authorized Service Request. It does not expose filesystem paths, storage keys, credentials or stack traces. The PDF is generated on request from current canonical report/request data and is not stored as a permanent generated artifact. The implemented PDF contains the service request reference, status, diagnosis, work performed and testing result. Branding, signatures, checklists and completion OTP are not part of the current report contract.

## Payments and Invoices

The canonical payment domain remains the application's business system of record. Phase 1B extends it with Razorpay Test Mode gateway fields and endpoints for server-created checkout orders, signed webhook synchronization, refunds, and lightweight reconciliation. Clients must not submit arbitrary payable amounts for invoice checkout; the backend derives the amount from trusted invoice/payment data. The backend must still be configured with Razorpay Test Mode credentials and a public HTTPS webhook endpoint before real external gateway verification can be completed.

| Method/path | Role | Input / data |
|---|---|---|
| GET /payments | CUSTOMER owner, ADMIN, SUPER_ADMIN | PageView<PaymentView> |
| POST /payments | CUSTOMER owner, ADMIN, SUPER_ADMIN | PaymentCreate -> PaymentView |
| GET /payments/{id} | CUSTOMER owner, ADMIN, SUPER_ADMIN | PaymentView |
| PUT /payments/{id}/status | ADMIN, SUPER_ADMIN | PaymentStatusUpdate -> PaymentView |
| GET /invoices | CUSTOMER owner, ADMIN, SUPER_ADMIN | PageView<InvoiceView> |
| POST /invoices | ADMIN, SUPER_ADMIN | InvoiceCreate -> InvoiceView |
| GET /invoices/{id} | CUSTOMER owner, ADMIN, SUPER_ADMIN | InvoiceView |
| PUT /invoices/{id}/status | ADMIN, SUPER_ADMIN | InvoiceStatusUpdate -> InvoiceView |

`PaymentCreate` requires `amount` and accepts `currency`, `purpose`, `customerProfileId`, `serviceRequestId`, `amcContractId`, `invoiceId`, and `providerReference`. Customers never submit or choose `customerProfileId`; ownership derives from JWT. Admin/SUPER_ADMIN may create a payment for a specified customer. If service request, AMC contract, or invoice IDs are supplied, they must belong to the same customer. `PaymentView` contains id, customerProfileId, serviceRequestId, amcContractId, invoiceId, amount, currency, purpose, status, providerReference, failureReason, createdAt and updatedAt. Payment statuses are `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, and `REFUNDED`. Purposes are `SERVICE_REQUEST`, `AMC_RENEWAL`, `INVOICE`, and `OTHER`.

`InvoiceCreate` requires `customerProfileId`, `description`, and `subtotal`; it accepts `serviceRequestId`, `amcContractId`, `taxAmount`, `currency`, and `dueDate`. Invoice numbers are generated by the backend as `INV-000001` style references after persistence. `InvoiceView` contains id, invoiceNumber, customerProfileId, serviceRequestId, amcContractId, description, subtotal, taxAmount, totalAmount, currency, status, issuedDate, dueDate, createdAt and updatedAt. Invoice statuses are `DRAFT`, `ISSUED`, `PAID`, `VOID`, and `CANCELLED`.

## Admin Transactions, Reports, and Exports

Admin finance reporting is read from the application's existing normalized database records. It does not create a second payment ledger and does not query Razorpay directly for report generation. Transaction rows are derived from `PaymentRecord` and `PaymentRefund`; invoice, customer, service request and AMC context are returned only where already linked by those records.

| Method/path | Role | Input / data |
|---|---|---|
| GET /admin/transactions | ADMIN, SUPER_ADMIN | `page?`, `size?`, `sort?`, `status?`, `customerId?`, `type?`, `dateFrom?`, `dateTo?`, `q?` -> `PageView<TransactionView>` |
| GET /admin/transactions/{id} | ADMIN, SUPER_ADMIN | `TransactionView` |
| GET /admin/transactions.csv | ADMIN, SUPER_ADMIN | same filters as list -> CSV download |
| GET /admin/reports/{type} | ADMIN, SUPER_ADMIN | `dateFrom?`, `dateTo?`, `status?`, `customerId?`, `technicianId?` -> `ReportView` |
| GET /admin/reports/{type}.csv | ADMIN, SUPER_ADMIN | same filters as report -> CSV download |

Transaction IDs are stable display identifiers in the form `PAYMENT-{id}` or `REFUND-{id}`. `TransactionView` contains id, type, status, amount, currency, customerProfileId, invoiceId, invoiceNumber, paymentId, refundId, serviceRequestId, amcContractId, purpose, providerReference, Razorpay order/payment/refund IDs where present, gatewayStatus, gatewaySyncedAt, createdAt and updatedAt. It never exposes card data, CVV, UPI PIN, webhook secrets, API keys, raw signatures or authentication material.

Transaction list supports status filtering using existing payment/refund statuses, `type=PAYMENT|REFUND`, customer filtering by customer profile ID, inclusive `dateFrom` and `dateTo`, search across invoice and safe gateway reference identifiers, pagination with size 1-100, and sorting by `createdAt`, `amount`, or `status` ascending/descending. Invalid filters return 400. Empty results return a normal page with an empty `items` array.

Report types are `revenue`, `payments`, `invoices`, `services`, `customers`, and `technicians`. `ReportView` contains `type`, a summary map and optional tabular rows. Revenue uses successful/refunded/partially-refunded payment gross amount minus successful refunds as net amount. Payments and invoices group by existing backend statuses. Services summarize service-request statuses and visit counts. Customer and technician reports use existing profile, AMC, availability and assignment data only; no SLA, ranking or performance-score calculation is defined in this phase.

CSV exports respect the same Admin authorization and filters as their JSON endpoints, include headers, handle empty datasets, escape cells safely and prefix formula-like values to prevent spreadsheet formula execution. PDF and Excel exports are not implemented for Phase 13A.

## Paid AMC Renewal Requests

Paid renewal is represented as a customer renewal request that Admin/SUPER_ADMIN can quote and later mark renewed only after a linked payment has `SUCCEEDED`. Automated pricing rules are not defined; the current implemented pricing rule is an admin-supplied quote amount/currency.

| Method/path | Role | Input / data |
|---|---|---|
| GET /amc-renewal-requests | CUSTOMER owner, ADMIN, SUPER_ADMIN | PageView<RenewalView> |
| POST /customers/me/amc-contracts/{id}/renewal-requests | CUSTOMER owner | RenewalCreate -> RenewalView |
| GET /amc-renewal-requests/{id} | CUSTOMER owner, ADMIN, SUPER_ADMIN | RenewalView |
| PUT /amc-renewal-requests/{id}/quote | ADMIN, SUPER_ADMIN | RenewalQuote -> RenewalView |
| PUT /amc-renewal-requests/{id}/status | ADMIN, SUPER_ADMIN | RenewalStatusUpdate -> RenewalView |

`RenewalCreate` requires requestedStartDate and requestedEndDate and accepts customerNotes. The requested period must start after the current AMC end date, and only one active request may exist per contract while status is `REQUESTED`, `QUOTED`, or `PAYMENT_PENDING`. `RenewalQuote` requires quotedAmount and currency and may link invoiceId/paymentId. `RenewalStatusUpdate` changes status and may link invoice/payment. Setting status to `RENEWED` requires a linked successful payment; it updates the existing AMC contract dates and increments renewalCount. Renewal statuses are `REQUESTED`, `QUOTED`, `PAYMENT_PENDING`, `RENEWED`, `REJECTED`, and `CANCELLED`.

## Support Tickets

Support tickets are separate from Service Requests. They represent product/support issues from customers or technicians and optional contextual links to an authorized Service Request.

| Method/path | Role | Input / data |
|---|---|---|
| GET /support-tickets | CUSTOMER own, TECHNICIAN own, ADMIN, SUPER_ADMIN | PageView<SupportTicketView> |
| POST /support-tickets | CUSTOMER, TECHNICIAN, ADMIN, SUPER_ADMIN | SupportTicketCreate -> SupportTicketView |
| GET /support-tickets/{id} | creator, ADMIN, SUPER_ADMIN | SupportTicketView |
| PUT /support-tickets/{id}/status | ADMIN, SUPER_ADMIN | SupportTicketStatusUpdate -> SupportTicketView |

`SupportTicketCreate` requires subject and description; accepts serviceRequestId, category, priority and preferredContact. If serviceRequestId is supplied, the caller must already be authorized for that request. Ticket references are generated by the backend as `SUP-000001` style references. Statuses are `SUBMITTED`, `UNDER_REVIEW`, `IN_PROGRESS`, `RESOLVED`, and `CLOSED`. Priorities are `LOW`, `MEDIUM`, `HIGH`, and `URGENT`. Categories are `ACCOUNT`, `SERVICE_REQUEST`, `TECHNICAL`, `BILLING`, and `OTHER`. Non-admin callers never receive adminNotes.

## Building and Lift Documents

Building/Lift document management reuses the local storage validation approach used by service-request attachments but stores separate asset document metadata. It does not expose storage keys or filesystem paths.

| Method/path | Role | Input / data |
|---|---|---|
| GET /buildings/{id}/documents | ADMIN, SUPER_ADMIN | AssetDocumentView[] |
| POST /buildings/{id}/documents | ADMIN, SUPER_ADMIN | multipart `file` -> AssetDocumentView |
| GET /buildings/{id}/documents/{documentId} | ADMIN, SUPER_ADMIN | file download |
| DELETE /buildings/{id}/documents/{documentId} | ADMIN, SUPER_ADMIN | void |
| GET /lifts/{id}/documents | ADMIN, SUPER_ADMIN | AssetDocumentView[] |
| POST /lifts/{id}/documents | ADMIN, SUPER_ADMIN | multipart `file` -> AssetDocumentView |
| GET /lifts/{id}/documents/{documentId} | ADMIN, SUPER_ADMIN | file download |
| DELETE /lifts/{id}/documents/{documentId} | ADMIN, SUPER_ADMIN | void |
| GET /customers/me/buildings/{id}/documents | CUSTOMER owner | AssetDocumentView[] |
| POST /customers/me/buildings/{id}/documents | CUSTOMER owner | multipart `file` -> AssetDocumentView |
| GET /customers/me/buildings/{id}/documents/{documentId} | CUSTOMER owner | file download |
| DELETE /customers/me/buildings/{id}/documents/{documentId} | CUSTOMER owner | void |
| GET /customers/me/lifts/{id}/documents | CUSTOMER owner | AssetDocumentView[] |
| POST /customers/me/lifts/{id}/documents | CUSTOMER owner | multipart `file` -> AssetDocumentView |
| GET /customers/me/lifts/{id}/documents/{documentId} | CUSTOMER owner | file download |
| DELETE /customers/me/lifts/{id}/documents/{documentId} | CUSTOMER owner | void |

`AssetDocumentView` contains id, ownerType (`BUILDING` or `LIFT`), ownerId, originalFilename, contentType, fileSize, uploadedByUserId and createdAt. Upload supports JPEG, PNG, WebP and PDF, validates size and basic signature server-side, and uses the existing 10 MB file limit. Customer authorization always resolves from JWT and the owned building/lift relationship; customers cannot manage another customer's asset documents. Admin/SUPER_ADMIN can manage documents for all assets.

## Scheduling / Service Visits

A ServiceRequest represents the customer's service need. A ServiceVisit represents one planned field-service appointment for that request. Every visit belongs to an existing service request. A request may have multiple historical visits, but only one active/upcoming visit may exist at a time. Completed and cancelled visits remain associated with the request. A visit is not a replacement for the service-request state machine.

| Method/path | Role | Input / data |
|---|---|---|
| GET /admin/service-visits | ADMIN, SUPER_ADMIN | `fromDate?`, `toDate?`, `technicianProfileId?`, `serviceRequestId?`, `status?`, `page=0`, `size=20` -> `PageView<VisitView>` |
| POST /admin/service-visits | ADMIN, SUPER_ADMIN | `VisitCreateRequest` -> `VisitView` |
| GET /admin/service-visits/{id} | ADMIN, SUPER_ADMIN | `VisitView` |
| PUT /admin/service-visits/{id} | ADMIN, SUPER_ADMIN | `VisitUpdateRequest` for date/time, technician and notes -> `VisitView` |
| POST /admin/service-visits/{id}/cancel | ADMIN, SUPER_ADMIN | required reason -> `VisitView` |
| GET /technician/me/visits | TECHNICIAN | `fromDate?`, `toDate?`, `status?`, `page=0`, `size=20` -> `PageView<VisitView>` |
| GET /technician/me/visits/{id} | assigned TECHNICIAN | `VisitView` |
| PUT /technician/me/visits/{id}/status | assigned TECHNICIAN | `IN_PROGRESS` or `COMPLETED`, optional notes -> `VisitView` |
| POST /technician/me/visits/{id}/reschedule-requests | assigned TECHNICIAN | required reason, requested date/start/end -> `VisitChangeRequestView` |
| POST /technician/me/visits/{id}/additional-visit-requests | assigned TECHNICIAN | required reason, requested date/start/end -> `VisitChangeRequestView` |
| POST /technician/me/visits/{id}/cancel | assigned TECHNICIAN | required reason -> `VisitView` |
| GET /admin/visit-change-requests | ADMIN, SUPER_ADMIN | `type?`, `status?`, `page=0`, `size=20` -> `PageView<VisitChangeRequestView>` |
| POST /admin/visit-change-requests/{id}/approve | ADMIN, SUPER_ADMIN | optional replacement date/start/end/technician -> `VisitView` |
| POST /admin/visit-change-requests/{id}/reject | ADMIN, SUPER_ADMIN | required reason -> `VisitChangeRequestView` |
| GET /customers/me/visits | CUSTOMER | optional `serviceRequestId`, `page=0`, `size=20` -> customer-safe `PageView<VisitView>` |

VisitCreateRequest requires an existing service request, an eligible assigned technician, `scheduledDate`, `startTime`, and `endTime`; it may include notes. Customers never create visits. Admin scheduling may be request-driven or calendar-driven, but both use this same Visit resource. A service request with no active/upcoming visit is an unscheduled service request; no separate task entity exists.

Visit statuses are exactly `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`. `RESCHEDULED` is not a status. Admin updates to date/time or technician are reschedule events while status remains `SCHEDULED`. Rescheduling and technician changes require the same conflict checks as creation. A technician may request a reschedule or additional visit, but cannot create or approve an appointment directly. Admin/SUPER_ADMIN approve or reject those requests and may change the proposed date/time or technician before approval.

The actual visit time range is authoritative. `startTime` must be before `endTime`. The database and service layer prevent overlapping active scheduled visits for the same technician; adjacent ranges are allowed. Completed and cancelled visits do not conflict. Only one active/upcoming visit is allowed for a service request. Technician availability labels are not a substitute for time-range conflict detection.

ADMIN and SUPER_ADMIN may create, list, view, reschedule, reassign, cancel and review visit-change requests. Assigned TECHNICIAN users may view their own visits, update authorized progress, request rescheduling, request an additional visit, and cancel their visit with a reason; they do not have unrestricted scheduling authority. CUSTOMER users may view only their own customer-safe visits and may cancel their own service request through `PENDING`, `ASSIGNED`, `ACCEPTED`, or `ON_THE_WAY`; cancellation is rejected from `REACHED_SITE` and later work states. Customer cancellation uses the existing request status endpoint, requires a reason, releases the active assignment and active visit, and records normal request/visit history. Customer visit views omit technician contact, employee, security and workload data. Visit cancellation does not cancel the service request; the request remains available for another visit. Visit and change-request events retain actor, reason/notes and timestamps in visit history.

## Notifications and dashboard

| Method/path | Role | Input / data |
|---|---|---|
| GET /notifications | authenticated recipient | status?, page=0, size=20 -> NotificationPageResponse |
| POST /notifications | ADMIN, SUPER_ADMIN | NotificationCreateRequest -> NotificationResponse |
| PUT /notifications/{id}/read | recipient only | NotificationResponse |
| GET /admin/dashboard/summary | ADMIN, SUPER_ADMIN | Summary |

NotificationCreateRequest requires recipientUserId, title, message; channel defaults to IN_APP and only IN_APP is accepted. EMAIL/SMS/PUSH remain reserved storage values, rejected as requests and not advertised as usable input channels. scheduledAt is optional UTC local date/time. Responses contain id, recipientUserId, title, message, channel, status, scheduledAt, sentAt, readAt and timestamps. Status values are PENDING, SENT, FAILED, READ. New in-app records are PENDING with sentAt null. Inbox is recipient-scoped and only includes unscheduled/due records, ordered createdAt/id descending. Read checks ownership and due time and is idempotent, preserving readAt. No deletion, external delivery or worker exists.

Dashboard Summary fields are totalCustomers, totalLifts, totalRequests, pendingJobs, completedJobs, emergencyJobs, totalTechnicians, totalAmcs. Counts include retained rows; pending/completed count exact lifecycle status and emergencyJobs counts priority EMERGENCY. Dashboard is the specific ADMIN/SUPER_ADMIN exception to the otherwise SUPER_ADMIN-only /admin/** security rule.

Dashboard backend connectivity uses the public `GET /api/v1/health` endpoint only. It returns a safe envelope with `data.status=UP`, requires no authentication, and must not expose database state, credentials, stack traces, host internals, JWT configuration or provider secrets. Admin clients may use it to show Backend API reachable/unreachable/checking states, but an HTTP health response is not a full business-data or infrastructure health guarantee.

## Admin settings

Admin settings are global non-secret business/runtime preferences managed through `/api/v1/admin/settings`. They are not deployment secrets. Database credentials, JWT secrets, provider API keys, password policy secrets, refresh-token/session lifetimes and environment-specific hostnames remain process/environment configuration and are never stored in the settings row.

| Method/path | Role | Input / data |
|---|---|---|
| GET /admin/settings | ADMIN, SUPER_ADMIN | SettingsView |
| PUT /admin/settings | ADMIN, SUPER_ADMIN | SettingsRequest -> SettingsView |

There is one global settings record. `SettingsView` contains `companyName`, nullable `supportEmail`, nullable `supportPhone`, `timezone`, `currency`, `dateFormat`, `defaultVisitDurationMinutes`, `maintenanceReminderDays`, `emergencyResponseTargetMinutes`, `emailNotificationsEnabled`, `smsNotificationsEnabled`, `autoAssignRequestsEnabled`, `createdAt`, and `updatedAt`.

`SettingsRequest` accepts the same editable fields except timestamps. `companyName`, `timezone`, `currency`, `dateFormat`, `defaultVisitDurationMinutes`, `maintenanceReminderDays`, and `emergencyResponseTargetMinutes` are required. Supported currencies are `INR`, `USD`, and `AED`. Supported date formats are `DD MMM YYYY`, `MM/DD/YYYY`, and `YYYY-MM-DD`. `timezone` must be a valid Java `ZoneId`. `defaultVisitDurationMinutes` must be 15-480, `maintenanceReminderDays` 0-365, and `emergencyResponseTargetMinutes` 5-1440. Email, phone and text lengths follow the DTO validation. Unknown fields are rejected, including secret-looking fields such as JWT, database, token, password or provider key values.

ADMIN and SUPER_ADMIN can read and update settings. CUSTOMER and TECHNICIAN cannot access these routes. Settings changes do not mutate existing service requests, visits, assignments, customers, assets, AMCs, notifications or staff accounts. The settings record is initialized by Flyway defaults and is recreated with defaults by service code only if missing in a nonstandard local/test database.

## Technician application contract

The Technician application is built on canonical Service Requests, technician assignments, Service Visits, reports, notifications and request attachments. There is no separate TechnicianJob, TechnicianVisit, TechnicianNotification, TechnicianReport or TechnicianAssignment table. A "job" is the authorized technician projection of a Service Request plus its current assignment/visit context.

### Implemented

| Method/path | Role | Input / data |
|---|---|---|
| GET /technician/me/profile | TECHNICIAN | TechnicianProfileView |
| PUT /technician/me/profile | TECHNICIAN | TechnicianProfileUpdate -> TechnicianProfileView |
| GET /technician/me/dashboard | TECHNICIAN | TechnicianDashboardSummary |
| GET /technician/me/jobs | TECHNICIAN | status?, page=0, size=20 -> PageView<RequestView> |
| GET /technician/me/jobs/{id} | current TECHNICIAN; historically assigned TECHNICIAN for terminal requests | Detail |
| POST /service-requests/{id}/assignments/{assignmentId}/accept | assigned TECHNICIAN | Detail |
| POST /service-requests/{id}/status | assigned TECHNICIAN | toStatus, notes? -> Detail |
| POST /technician/me/jobs/{id}/report | assigned TECHNICIAN | diagnosis, workPerformed, testingResult; completionNotes? -> ReportView |
| GET /technician/me/jobs/{id}/location | assigned TECHNICIAN | LocationView with latest, route/ETA/geofence availability |
| POST /technician/me/jobs/{id}/location | assigned TECHNICIAN | latitude, longitude, timestamp?, accuracy?, speed?, heading?, battery? -> LocationView |
| GET /technician/me/jobs/{id}/payment | assigned TECHNICIAN | invoice/payment/cash OTP state for the service request |
| GET /technician/me/visits | TECHNICIAN | fromDate?, toDate?, status?, page=0, size=20 -> PageView<VisitView> |
| GET /technician/me/visits/{id} | assigned TECHNICIAN | VisitView |
| PUT /technician/me/visits/{id}/status | assigned TECHNICIAN | IN_PROGRESS or COMPLETED, optional notes -> VisitView |
| POST /technician/me/visits/{id}/reschedule-requests | assigned TECHNICIAN | required reason, requested date/start/end -> VisitChangeRequestView |
| POST /technician/me/visits/{id}/additional-visit-requests | assigned TECHNICIAN | required reason, requested date/start/end -> VisitChangeRequestView |
| POST /technician/me/visits/{id}/cancel | assigned TECHNICIAN | required reason -> VisitView |
| GET /notifications | authenticated TECHNICIAN recipient | own notification inbox |
| PUT /notifications/{id}/read | authenticated TECHNICIAN recipient | marks own due notification read |
| GET /service-requests/{id}/attachments | assigned/historical TECHNICIAN | AttachmentView[] |
| POST /service-requests/{id}/attachments | assigned TECHNICIAN on active nonterminal request | multipart file -> AttachmentView |
| GET /service-requests/{id}/attachments/{attachmentId} | assigned/historical TECHNICIAN | file download |
| DELETE /service-requests/{id}/attachments/{attachmentId} | assigned TECHNICIAN uploader on active nonterminal request | void |
| POST /technician/me/jobs/{id}/arrival-otp/request | assigned TECHNICIAN | creates arrival OTP state; does not return OTP code to technician |
| POST /technician/me/jobs/{id}/arrival-otp/verify | assigned TECHNICIAN | otpId, otp -> state |
| GET /service-requests/{id}/arrival-otp | owner CUSTOMER, assigned TECHNICIAN, ADMIN/SUPER_ADMIN | safe OTP state; code only for owner customer while pending/valid |
| POST /technician/me/jobs/{id}/completion-otp/request | assigned TECHNICIAN | creates completion OTP state; does not return OTP code to technician |
| POST /technician/me/jobs/{id}/completion-otp/verify | assigned TECHNICIAN | otpId, otp -> state |
| GET /service-requests/{id}/completion-otp | owner CUSTOMER, assigned TECHNICIAN, ADMIN/SUPER_ADMIN | safe OTP state; code only for owner customer while pending/valid |
| GET /technician/me/jobs/{id}/checklist | assigned TECHNICIAN | JobChecklistView or null data when no active applicable template exists |
| PUT /technician/me/jobs/{id}/checklist/responses | assigned TECHNICIAN | item response batch -> JobChecklistView |
| GET /technician/me/private-attachments | TECHNICIAN | own private attachment metadata |
| POST /technician/me/private-attachments | TECHNICIAN | multipart file -> metadata |
| GET /technician/me/private-attachments/{id} | owner TECHNICIAN | file download |
| DELETE /technician/me/private-attachments/{id} | owner TECHNICIAN | no content |
| POST /payments/cash/otp/verify | assigned TECHNICIAN or ADMIN/SUPER_ADMIN | paymentId, otpId, otp -> CashPaymentOtpView |

Technician authentication uses `POST /auth/login/technician`, the shared refresh/logout routes, and role-scoped bearer JWT authorization. TECHNICIAN cannot access Admin customer management, staff provisioning, Admin settings, unrelated jobs, unrelated visits, unrelated attachments, or another technician's private workload.

TechnicianProfileView contains userId, technicianProfileId, email, phone, employeeId, assignedArea, specialization, availabilityStatus, active, lastActiveAt, profilePhotoUrl, dateOfBirth, gender, address, emergencyContactName, and emergencyContactPhone. Employee ID, assigned area, specialization, email, phone and active state are read-only in the technician app. TechnicianProfileUpdate accepts `availabilityStatus`, `profilePhotoUrl`, `dateOfBirth`, `gender`, `address`, `emergencyContactName`, and `emergencyContactPhone`; unknown fields are rejected. Availability is operational metadata and never bypasses Visit conflict detection, assignment authorization, or Service Request state validation.

TechnicianDashboardSummary contains assignedJobs, pendingJobs, inProgressJobs, completedJobs, completedThisQuarter, todaysScheduledVisits and emergencyJobs. Assigned jobs and emergency jobs count active assigned/accepted assignments. Pending jobs count assigned requests in `ASSIGNED`. In-progress jobs count `ACCEPTED`, `ON_THE_WAY`, `REACHED_SITE`, `DIAGNOSIS`, `REPAIR_IN_PROGRESS`, `WAITING_FOR_PARTS`, and `TESTING`. Completed jobs count all completed requests historically assigned to the technician. Completed this quarter uses the current calendar quarter in the backend clock. Today's scheduled visits count the technician's own visits on the backend current date.

Technician job lists are limited to the signed-in technician. Active work requires a current assignment. Terminal completed/cancelled history may be read by a technician with any historical assignment row for that request. Filters use the canonical Service Request status values. The job detail provides the existing Detail projection, including customer/building/lift/service context already present in `RequestView`, activeAssignment, history and report. Customer contact information is limited to the safe fields already exposed by the authorized request projection; credentials, authentication state, password/token hashes and unrelated customer records are never returned.

Technician progress uses the canonical Service Request lifecycle. There is no technician-only state machine. "Start travel" maps to `ON_THE_WAY`, "mark arrived" maps to `REACHED_SITE`, work execution maps through `DIAGNOSIS`, `REPAIR_IN_PROGRESS`, `WAITING_FOR_PARTS`, and `TESTING`, and completion uses `COMPLETED` after a valid report, required checklist responses, and completion OTP verification exist for the active assignment. Arrival OTP verification is available after `REACHED_SITE` and before work begins. Cancellation uses the existing `CANCELLED` transition rules and requires notes/reason. Visit progress remains on the Visit resource and must stay consistent with the Service Request/assignment authorization rules.

Technician photos/evidence reuse Service Request attachments. A technician may upload JPEG, PNG, WebP or PDF files only to their active assigned nonterminal request. A technician may list/download attachments for an active assigned request and for completed/cancelled requests where they have historical assignment. A technician may delete only files they uploaded, and only while the request is nonterminal and assigned to them. Customer owners and Admin/SUPER_ADMIN can see request-scoped attachments through their existing authorization.

Technician-private attachments are separate from request-scoped attachments. They are visible only to the technician owner and authorized Admin/SUPER_ADMIN users. Customers can never list, download, upload, or delete technician-private attachments. The API returns metadata and file downloads only; it never exposes filesystem paths or storage keys.

Technician notifications reuse the canonical notification inbox. Assignment, reassignment, Visit creation/reschedule/cancellation, change-request approval/rejection, emergency assignment and admin update messages should be represented as normal in-app notifications to the technician user where emitting services create them. No technician-specific notification table exists.

Technician payment reads are service-request scoped. `GET /technician/me/jobs/{id}/payment` exposes the invoice/payment/cash OTP state needed to render the Technician payment screen. Cash OTP verification is allowed for the assigned technician or Admin/SUPER_ADMIN and never returns OTP hashes. UPI/Razorpay payment success remains gateway/webhook-authoritative and is not technician-controlled.

### Deferred or product decision required

Routing provider activation, real ETA quality, websocket/SSE delivery, long-term customer-visible location history, and additional tracking intelligence remain deferred product decisions. The implemented location endpoints return explicit unavailable states when provider data, building coordinates, or active tracking state are missing.

### Live tracking scope

Live technician tracking is implemented as latest-location state only. It does not define routing, ETA, geofencing, background history, websocket/SSE delivery, or long-term location retention; those remain future product decisions.

## Phase 15 technician advanced operations

Checklist templates are managed by Admin/SUPER_ADMIN users and consumed by assigned technicians. Templates have active state, version, timestamps, optional description, and service-type applicability. Items belong to a template and include label, optional description, required flag, sort order, and input type (`CHECKBOX`, `TEXT`, `NUMBER`, or `PHOTO_NOTE`). Checklist questions are not hardcoded in the Technician app.

| Method/path | Role | Input / data |
|---|---|---|
| GET /admin/checklist-templates | ADMIN, SUPER_ADMIN | TemplateView[] |
| POST /admin/checklist-templates | ADMIN, SUPER_ADMIN | name, description?, active?, serviceTypes? -> TemplateView |
| PUT /admin/checklist-templates/{id} | ADMIN, SUPER_ADMIN | editable template fields -> TemplateView |
| POST /admin/checklist-templates/{id}/items | ADMIN, SUPER_ADMIN | label, description?, required?, sortOrder, inputType -> TemplateView |
| PUT /admin/checklist-templates/{id}/items/{itemId} | ADMIN, SUPER_ADMIN | editable item fields -> TemplateView |
| DELETE /admin/checklist-templates/{id}/items/{itemId} | ADMIN, SUPER_ADMIN | no content |
| GET /technician/me/jobs/{id}/checklist | assigned TECHNICIAN | JobChecklistView or null data when no active applicable template exists |
| PUT /technician/me/jobs/{id}/checklist/responses | assigned TECHNICIAN | item response batch -> JobChecklistView |

The backend creates a job checklist from the active applicable template when an assigned technician opens the checklist. Required items must be completed before the service request can transition to `COMPLETED`. If no active applicable template exists, checklist gating does not block completion.

Completion OTP is backend-owned and gates service completion after the structured report and required checklist validations pass. OTP values are generated server-side, hashed with the configured password encoder, expire, enforce attempt limits and lockout, and are never logged or returned to technicians/admins. The authorized customer owner may see the current pending code from the OTP state endpoint only while it is valid; the code is cleared after verification or expiry and the hash remains the verification source of truth. Delivery uses the communication-provider abstraction; the current application-level implementation uses a no-op sender so real SMS/WhatsApp/email credentials are not required for tests.

| Method/path | Role | Input / data |
|---|---|---|
| POST /technician/me/jobs/{id}/completion-otp/request | assigned TECHNICIAN | creates OTP state; does not return OTP code to technician |
| POST /technician/me/jobs/{id}/completion-otp/verify | assigned TECHNICIAN | otpId, otp -> state |
| GET /service-requests/{id}/completion-otp | owner CUSTOMER, assigned TECHNICIAN, ADMIN/SUPER_ADMIN | safe OTP state; `code` is populated only for the owner customer while the OTP is pending and unexpired |

Technician-private attachments use the existing storage abstraction with MIME and size validation. The technician routes operate only on the signed-in technician's private files; Admin/SUPER_ADMIN routes require a target technician id. Customers have no route to these files.

| Method/path | Role | Input / data |
|---|---|---|
| GET /technician/me/private-attachments | TECHNICIAN | own private attachment metadata |
| POST /technician/me/private-attachments | TECHNICIAN | multipart file -> metadata |
| GET /technician/me/private-attachments/{id} | owner TECHNICIAN | file download |
| DELETE /technician/me/private-attachments/{id} | owner TECHNICIAN | no content |
| GET /admin/technicians/{id} | ADMIN, SUPER_ADMIN | expanded technician profile |
| PUT /admin/technicians/{id} | ADMIN, SUPER_ADMIN | expanded editable technician fields -> profile |
| GET /admin/technicians/{id}/private-attachments | ADMIN, SUPER_ADMIN | technician private attachment metadata |
| POST /admin/technicians/{id}/private-attachments | ADMIN, SUPER_ADMIN | multipart file -> metadata |
| GET /admin/technicians/{id}/private-attachments/{attachmentId} | ADMIN, SUPER_ADMIN | file download |
| DELETE /admin/technicians/{id}/private-attachments/{attachmentId} | ADMIN, SUPER_ADMIN | no content |

## Phase 1B Razorpay payments and live technician tracking

The Phase 1 payment/invoice domain remains the application business system of record. Razorpay is an external gateway only behind the backend `PaymentGateway` abstraction, currently implemented by `RazorpayGateway`. Backend secrets are configured by environment variables `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET`; clients receive only the public key id and checkout order details.

| Method/path | Role | Input / data |
|---|---|---|
| POST /payments/razorpay/checkout | CUSTOMER owner, ADMIN/SUPER_ADMIN | invoiceId -> paymentId, invoiceId, razorpayKeyId, razorpayOrderId, amount, currency, status |
| POST /webhooks/razorpay | Razorpay signed webhook | raw body plus `X-Razorpay-Signature`; verifies signature, stores event id, processes idempotently |
| POST /payments/{id}/refunds | ADMIN/SUPER_ADMIN | amount, reason? -> current PaymentView |
| GET /payments/{id}/refunds | owner CUSTOMER, ADMIN/SUPER_ADMIN | RefundView[] |
| GET /admin/payments/reconciliation | ADMIN/SUPER_ADMIN | lightweight mismatch issue list |

Checkout creation never trusts a frontend amount. It validates invoice ownership/eligibility, rejects paid/void/cancelled invoices, rejects an existing processing/succeeded payment attempt for the invoice, creates a local `payment_records` row, and creates a Razorpay Order for the trusted invoice total/currency. Browser/app checkout return is not authoritative; clients should poll `GET /payments/{id}` and show pending until webhook/API synchronization updates the local payment. Application-level integration can be built without provider credentials; real gateway transactions require later external activation.

Razorpay event mapping currently handles documented event names `payment.authorized`, `payment.captured`, `payment.failed`, `refund.processed`, and `refund.failed`. Internal statuses remain compatible: `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `REFUNDED`, and `PARTIALLY_REFUNDED`. Unknown Razorpay events with valid signatures are stored as processed and ignored safely.

Refund requests require an existing successful or partially refunded Razorpay payment, reject an active refund in progress, and reject over-refunding. Admin clicking refund creates a gateway refund request but does not mark completion; webhook synchronization updates refund/payment state.

Live tracking is latest-location only. Tracking is active for canonical Service Request statuses `ON_THE_WAY`, `REACHED_SITE`, `DIAGNOSIS`, `REPAIR_IN_PROGRESS`, `WAITING_FOR_PARTS`, and `TESTING`; it stops for `COMPLETED`, `CANCELLED`, and other non-trackable states.

| Method/path | Role | Input / data |
|---|---|---|
| POST /technician/me/jobs/{id}/location | assigned TECHNICIAN | latitude, longitude, timestamp? -> latitude, longitude, timestamp, stale, trackingState |
| GET /technician/me/jobs/{id}/location | assigned TECHNICIAN | latest location plus route/ETA/geofence availability |
| GET /customers/me/service-requests/{id}/technician-location | owner CUSTOMER | latest latitude, longitude, timestamp, stale, trackingState |

Technician identity is always resolved from JWT; technician_id is never accepted from the client. Customers can only view their own request's assigned technician location while tracking is active. Coordinates are validated server-side and stored in `technician_latest_locations` as one latest row per service request.

## Phase 16 advanced live tracking

Phase 16 extends, but does not replace, the latest-location model. The existing
`POST /technician/me/jobs/{id}/location` endpoint still updates the optimized
latest row and now also writes append-only history, evaluates geofence state, and
returns advanced tracking metadata. The existing customer endpoint remains
compatible: older clients can keep reading `latitude`, `longitude`, `timestamp`,
`stale`, and `trackingState`; newer clients may additionally read `geofence`,
`route`, and `eta`.

Location history is stored in `technician_location_history` with service request,
technician, latitude, longitude, and recordedAt indexes. It is backend/internal
tracking data in this phase; no customer history endpoint is exposed.

Geofencing uses the service request's lift -> building relationship. Buildings
now support nullable `latitude` and `longitude`. If a building has no coordinates,
geofence state is `UNAVAILABLE`; no coordinates are invented. The geofence radius
is configurable through `valor.tracking.geofence-radius-meters` and defaults to
150 meters.

Routing is behind `RoutingProvider`. The default provider is intentionally
unconfigured and returns `PROVIDER_NOT_CONFIGURED`, so the backend never fabricates
production route or ETA values. ETA is available only when the route provider
returns a route and the latest technician location is not stale. Stale location,
missing site coordinates, inactive jobs, and unconfigured providers all produce
explicit unavailable states.

Background tracking is a Technician client capability using Expo background
location tasks. The backend still authorizes every submitted point by JWT,
assignment, and active trackable request status.

## Phase 17 communication platform foundation

Phase 17 adds a provider-independent communication foundation. It does not
activate real Email, SMS, MSG91, WhatsApp, or other external providers.

Tables: `communication_templates`, `communication_preferences`,
`communication_events`, and `communication_messages`.

Channels: `EMAIL`, `SMS`, `WHATSAPP`, `IN_APP`.

Message status lifecycle: `PENDING`, `PROCESSING`, `SENT`, `DELIVERED`,
`FAILED`, `CANCELLED`.

Idempotency rule: `communication_events.idempotency_key` is unique. Reprocessing
the same idempotency key reuses the existing communication event and does not
create duplicate per-channel messages.

Provider abstraction: backend business logic uses communication providers
(`EmailProvider`, `SmsProvider`, `WhatsAppProvider`) through
`CommunicationService`; provider-specific SDK/API behavior remains outside the
core communication domain. Current providers are mock/development providers.

Recipient logging rule: normal logs and Admin visibility use masked recipients
such as `g***@example.com` or `+91******1234`. Provider secrets, JWTs, passwords,
OTP plaintext, and authorization tokens must not be logged.

| Method/path | Role | Input / data |
|---|---|---|
| GET /admin/communications/messages | ADMIN/SUPER_ADMIN | status?, page?, size? -> paged communication messages |
| POST /admin/communications/events | ADMIN/SUPER_ADMIN | eventType, recipientUserId, channels, templateKey?, variables?, idempotencyKey? -> messages |
| POST /admin/communications/messages/{id}/process | ADMIN/SUPER_ADMIN | processes one pending/failed message through the configured mock/provider abstraction |
| POST /admin/communications/retries/process | ADMIN/SUPER_ADMIN | limit? -> processed retry count |
| GET /admin/communications/preferences/{userId} | ADMIN/SUPER_ADMIN | communication channel preferences |
| PUT /admin/communications/preferences/{userId} | ADMIN/SUPER_ADMIN | emailEnabled, smsEnabled, whatsappEnabled, inAppEnabled |

## Migration and client readiness

V1-V4 remain unchanged. V5 adds typed service visits and visit-change requests with restrictive foreign keys, date/technician/request indexes, active-visit uniqueness, and time-range validation. V6 adds the single global admin_settings record for non-secret Admin runtime preferences. V7 adds request-scoped attachments and one feedback row per service request. V8 adds payment_records, invoices, support_tickets, amc_renewal_requests and asset_documents with restrictive foreign keys, owner indexes, status checks and no credential storage. V9 adds Razorpay identifiers to payment_records, payment_refunds, razorpay_webhook_events and technician_latest_locations. V10 adds roles, permissions, role_permissions and audit_logs with seeded role capability mappings and audit indexes. V11 adds technician advanced operations tables for checklist, completion OTP, expanded profiles, and technician-private attachments. V12 adds nullable building coordinates plus location history and geofence state/event tables. V13 adds provider-independent communication templates, preferences, events, and messages with channel/status/retry/idempotency indexes. The H2 test adapter still only removes V3 STORED syntax for H2; it is not deployed and cannot prove MySQL storage semantics. Live Razorpay Test Mode transaction verification, public HTTPS webhook delivery, routing provider activation, communication provider activation, and real background/device tracking validation remain environment-dependent.
## Phase 18 Email System Contract

Email is a backend-driven communication channel under `/api/v1` and reuses the Phase 17 communication APIs and data model. Clients do not receive provider secrets or provider configuration.

Architecture:

```text
CommunicationService
  -> EmailProvider
    -> MockEmailProvider
    -> future provider adapter
```

`EmailProvider.send(...)` receives provider-neutral data only: recipient, sender, sender display name, reply-to, subject, HTML body, plain-text body, communication message id, event/template metadata, and idempotency key. No SMTP, SES, SendGrid, Mailgun, Resend, or other provider model is part of the core contract.

Configuration is generic and optional: `EMAIL_PROVIDER`, `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_USERNAME`, `EMAIL_PASSWORD`, `EMAIL_API_KEY`, `EMAIL_FROM`, `EMAIL_FROM_NAME`, `EMAIL_REPLY_TO`, `EMAIL_ENABLED`, and `APP_SET_PASSWORD_URL`. Real provider activation is deferred.

`communication_messages` remains the delivery source of truth. Email delivery uses the existing statuses `PENDING`, `PROCESSING`, `SENT`, `DELIVERED`, `FAILED`, and `CANCELLED`, stores provider/provider message references when available, retries transient failures with `retry_count`, `max_retry_count`, `next_retry_at`, and preserves sanitized `failure_reason`.

Safe onboarding:

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/set-password` | Public one-time token | Set a password using an expiring onboarding token |

`POST /auth/set-password` accepts `{ token, password }`. Tokens are hashed at rest, expire, are single-use, and are not returned by read APIs. Account-created emails must use the configured set-password URL and must never include raw passwords.

Backend-triggered email events currently include customer account/set-password, service request created/status/completed/feedback, appointment scheduled/changed/cancelled, technician account/assignment/job status/visit/change-request decision, AMC renewal request, invoice created, payment result, and admin alert template support where existing backend events dispatch it.

## Phases 19-21 SMS, WhatsApp, And Preference Contract

All paths remain under `/api/v1`. External MSG91 SMS/OTP and WhatsApp providers are not activated; tests/dev use mock providers only.

OTP endpoints:

| Method | Path | Auth | Request | Response |
| --- | --- | --- | --- | --- |
| POST | `/auth/otp/send` | Public dev/test only | `phone` | `OtpSent` |
| POST | `/auth/otp/resend` | Public dev/test only | `phone`, `requestId` | `OtpSent` |
| POST | `/auth/otp/verify` | Public | `phone`, `otp`, `requestId` | `Authentication` |

OTP behavior: phone numbers use canonical normalization, OTPs are hashed at rest, expire by configured seconds, enforce resend cooldown, hourly request limits, attempt limits, temporary lockout, and masked/sanitized provider error handling. Responses must not expose OTP hashes, provider secrets, MSG91 auth keys, or internal provider payloads.

Provider abstractions:

```text
OtpProvider -> MockOtpProvider -> future Msg91OtpProvider activation
WhatsAppProvider -> MockWhatsAppProvider -> future Msg91WhatsAppProvider activation
SmsProvider -> MockSmsProvider -> future SMS adapter activation
```

Preference contract expands `/admin/communications/preferences/{userId}` without creating a second preference system. `PreferenceView` includes:

```text
emailEnabled
smsEnabled
whatsappEnabled
inAppEnabled
otpSmsEnabled
otpWhatsappEnabled
serviceNotificationsEnabled
billingNotificationsEnabled
appointmentNotificationsEnabled
jobNotificationsEnabled
visitNotificationsEnabled
systemNotificationsEnabled
criticalAlertsEnabled
reportNotificationsEnabled
```

`PUT /admin/communications/preferences/{userId}` accepts the same fields. The first four channel booleans remain required for compatibility; category booleans are optional and preserve previous values when omitted. Non-mandatory communication events are filtered by channel and category preferences before messages are created. Security/mandatory flows are handled by product/auth rules and are not silently blocked by generic marketing preferences.

WhatsApp support uses existing communication events, templates, status, retry, failure, provider reference, and idempotency behavior. Supported template/event coverage includes OTP where selected, service-request updates, visit changes, technician assignment, AMC renewal, invoice/payment, service completion, and feedback requests.

## Phase 22 Communication Event Automation Contract

Communication automation is backend-owned and remains under `/api/v1`. Controllers and clients do not call Email, SMS, OTP, or WhatsApp providers directly. Existing business services publish application communication events, and the communication layer creates idempotent delivery records after the business transaction commits.

Automated event coverage is limited to existing backend flows: customer created/onboarding, service request created with customer confirmation and admin critical alert, technician assigned/reassigned, visit scheduled/rescheduled/cancelled, service-request status changed, AMC created or renewal requested, invoice created, payment result, service completed plus feedback request, and report-ready admin broadcasts where report generation already exists.

Delivery records continue to use `communication_events` and `communication_messages`; there is no second status table. Preference filtering, template rendering, retry/failure handling, provider reference storage, masked logging, and duplicate prevention reuse the Phase 17-21 contracts. External providers are not activated by Phase 22, and API responses never expose provider secrets.

No new client-visible provider configuration API is added. Admin visibility remains the existing `/api/v1/admin/communications/**` surface.
