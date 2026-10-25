# Canonical backend API contract

Scope: the current `/api/v1` contract, with historical feature milestones retained where useful. The recorded DB-01–DB-33 baseline is 48 migration files through V57 on isolated MySQL 8.0.46 schemas; versions are not contiguous. Migration milestones below are historical additions, not a complete current-chain description. No `TARGET_VALOR_SCHEMA_SPEC.md` source is present in this checkout.

## Common contract

JSON success responses normally use `ApiResponse<T>` with `success`, `message`, `data`, `status`, and `timestamp`; status may be 200 or an explicitly documented creation status such as 201. PDF, CSV, multipart, and other downloads return their documented `ResponseEntity` content type and are not wrapped in `ApiResponse<T>`. Staff and domain views are flat DTOs; no identity entities or passwordHash/otpHash/tokenHash fields are serialized. Input passwords are writeOnly with password format in OpenAPI. Shared error responses cover validation/authentication/OTP/refresh failures, 401 unauthenticated, 403 forbidden, 404 missing resources, and 409 conflicts. Errors do not return submitted secrets or internal exception details.

Default port: 8081. Swagger: `/swagger-ui.html`. OpenAPI: `/v3/api-docs`. Public health: `GET /api/v1/health` with `data.status=UP`. The `/api/v1` auth registration/login, auth OTP, refresh, set-password, technician-application token routes, and health route have explicit public/token rules; the Razorpay webhook is public at the filter layer but requires a valid signature in the service. Other routes require JWT authentication. Runtime configuration is unchanged: Flyway enabled, Hibernate validate, SQL initialization disabled; production must not enable development bootstrap.

## Persistent Lift Service Code

`GET /customers/me/lifts/{liftId}/service-code` and `POST /customers/me/lifts/{liftId}/service-code/regenerate` are scoped to the authenticated owner. Service-request arrival/completion URLs remain compatible shims, but verification uses the same lift-owned code with separate START and COMPLETE audit records. Codes do not expire or regenerate per assignment. Verification is limited to the assigned technician, matching request lift and workflow state, with three failed attempts per action. Installation requests without a lift bypass this requirement. Attempt counters, hashes, expiry metadata, and raw codes are not exposed to customers or Admin users.

Technicians verify START or COMPLETE codes for a job with `POST /technician/me/jobs/{requestId}/service-code/verify` using `{ "action": "START|COMPLETE", "code": "..." }`. The request ID is canonical; the lift and active assignment are loaded server-side from the request. Only the assigned authenticated technician may call this route. START is accepted only at `REACHED_SITE` and gates `DIAGNOSIS`; COMPLETE is accepted only at `TESTING` and gates `COMPLETED`. The response contains `liftId`, `status`, `lockedUntil`, `attempts`, and `verifiedAt`; it never returns the submitted or stored code. Verification does not advance the request state; the existing workflow transition checks the recorded action-specific verification. Regenerating a customer code continues to replace the stored code without invalidating existing verification rows; whether regeneration should invalidate prior verification is a pending product/security decision.

Building create/update payloads require non-null `latitude` and `longitude` within `[-90,90]` and `[-180,180]`. Database NOT NULL enforcement is deferred until existing NULL rows are safely audited.

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

Authentication data contains accessToken, refreshToken, role, userId, nullable customerProfile and technicianProfile. CustomerSummary contains id, fullName, read-only account email and phone, alternatePhone, companyName, address, status, active, and referralCode. TechnicianSummary contains id, employeeId, assignedArea, specialization, availabilityStatus, active. Administrators have no profile. No tokens appear in /me.

Registration does not advertise role and rejects any submitted role, including null, through strict unknown-field validation. Email is trimmed/lowercased; phone is trimmed with spaces/hyphens/parentheses removed and must be canonical +international digits. A phone identity or email/password identity is required. Password storage is BCrypt; refresh-token storage is SHA-256. Duplicate normalized identities return 409. Login failures for an active matching-role account increment a persisted counter under a user-row lock; five wrong passwords temporarily lock it for fifteen minutes. Successful login resets the counter and temporary lock. Staff login now requires email rather than the old identity field; customer login preserves identity.

Customer authentication OTP send is available only under dev/test, never with prod active; other profiles reject it before persistence. The technician application uses a separate opaque `X-Application-Token` and its OTP endpoints are profile-gated to `dev` or `test` and rejected when `prod` is active. Its response marks `developmentOnly=true` and returns the generated four-digit code, including the documented `1111` only when fixtures or callers explicitly use that value; it is not a production acceptance path. A fixed-code or provider-backed production path is not implemented and requires a separately authorized security change. The generated customer OTP is never logged and only its BCrypt hash is persisted. Customer OTP codes expire after five minutes; three wrong attempts lock for fifteen minutes. Technician application OTPs expire after ten minutes and have three remaining attempts. Refresh rotation locks the token row and revokes the previous token atomically. Deactivated accounts cannot use login, JWT, or refresh.

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

### Verified Indian PIN-code lookup

`GET /api/v1/locations/pincodes/{pincode}` is available to authenticated users. The path must contain exactly six digits with a first digit from 1-9. It returns `ApiResponse<Lookup>` with `data.pincode` and `data.locations[]`; each location contains `pincode`, `state`, `district`, `postOffice`, `locality`, `sourceName`, `sourceVersion`, and nullable `sourceUpdatedAt`. Only active rows imported from the maintained Government of India OGD All India Pincode Directory are returned. Multiple post offices are returned explicitly so clients must not guess a locality. Invalid format returns 400; a syntactically valid but unavailable PIN returns 404. The endpoint does not geocode or provide an exact building coordinate.

The persistent directory is intentionally empty until an approved OGD snapshot is imported through a controlled operational process. The OGD catalog is licensed under the Government Open Data License–India; deployments must retain source/version metadata and refresh from the published directory rather than treating client-entered six-digit values as verified.

| Method/path | Role | Input / data |
|---|---|---|
| GET /customers/me | CUSTOMER | CustomerSummary including backend-owned `referralCode` and read-only account `email`/`phone` |
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
| POST /payments/razorpay/amc-purchase | CUSTOMER owner | AmcPurchaseCheckoutCreate (liftId, planId) -> AmcPurchaseCheckoutView |
| GET /invoices | CUSTOMER owner, ADMIN, SUPER_ADMIN | PageView<InvoiceView> |
| POST /invoices | ADMIN, SUPER_ADMIN | InvoiceCreate -> InvoiceView |
| GET /invoices/{id} | CUSTOMER owner, ADMIN, SUPER_ADMIN | InvoiceView |
| PUT /invoices/{id}/status | ADMIN, SUPER_ADMIN | InvoiceStatusUpdate -> InvoiceView |

`PaymentCreate` requires `amount` and accepts `currency`, `purpose`, `customerProfileId`, `serviceRequestId`, `amcContractId`, `invoiceId`, and `providerReference`. Customers never submit or choose `customerProfileId`; ownership derives from JWT. Admin/SUPER_ADMIN may create a payment for a specified customer. If service request, AMC contract, or invoice IDs are supplied, they must belong to the same customer. New AMC purchases must use the server-priced Razorpay endpoint and cannot be marked successful through manual status updates. `PaymentView` contains id, customerProfileId, serviceRequestId, amcContractId, invoiceId, amount, currency, purpose, status, providerReference, failureReason, createdAt and updatedAt. Payment statuses are `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, and `REFUNDED`. Purposes are `SERVICE_REQUEST`, `AMC_RENEWAL`, `AMC_PURCHASE`, `INVOICE`, and `OTHER`.

`AmcPurchaseCheckoutCreate` accepts the customer-owned `liftId` and one server catalog `planId`: `essential` (12+1 months, INR 12,000 before GST), `extended` (24+2 months, INR 22,000 before GST), or `long-term` (36+3 months, INR 32,000 before GST). The response contains the payment ID, Razorpay key/order, server-calculated amount and currency, and plan subtotal/tax/total. A paid invoice and AMC contract are generated on verified capture and linked to the resulting `PaymentView`.

`InvoiceCreate` requires `customerProfileId`, `description`, and `subtotal`; it accepts `serviceRequestId`, `amcContractId`, `taxAmount`, `currency`, and `dueDate`. Invoice numbers are generated by the backend as `INV-000001` style references after persistence. `InvoiceView` contains id, invoiceNumber, customerProfileId, serviceRequestId, amcContractId, description, subtotal, taxAmount, totalAmount, currency, status, issuedDate, dueDate, createdAt and updatedAt. Invoice statuses are `DRAFT`, `ISSUED`, `PAID`, `VOID`, and `CANCELLED`.

## Admin Transactions, Reports, and Exports

Admin finance reporting is read from the application's existing normalized database records. It does not create a second payment ledger and does not query Razorpay directly for report generation. Transaction rows are derived from `PaymentRecord` and `PaymentRefund`; invoice, customer, service request and AMC context are returned only where already linked by those records.

Financial document scope: the current contract exposes authoritative invoice, payment, and refund data through the JSON APIs above, Admin transaction/report CSV exports, a customer/Admin payment receipt PDF, and a consolidated refund PDF. The service-report PDF is a separate operational document and is not a payment receipt. Clients must use the persisted status, amount, currency, refund status, and gateway fields; pending, failed, or uncertain payments must not be presented as successful receipts. Refund-document history and inclusion rules are defined in the consolidated refund document section below; no resend-notification feature is claimed by this contract.

Customer payment receipts: `GET /payments/{id}/receipt.pdf` is available to the owning customer or authorized Admin/SUPER_ADMIN only, and returns `application/pdf` with filename `receipt-{paymentId}.pdf`. It is generated from the persisted successful `PaymentRecord` and its matching `Invoice`. The document includes a stable derived receipt reference (`RECEIPT-PAYMENT-{paymentId}`), invoice/payment references, authoritative payment date, amount, currency, and successful status. Pending, failed, processing, uncertain, missing-invoice, or mismatched payment records are rejected. The receipt is not a tax invoice and does not invent merchant, tax, or refund data.

Consolidated refund documents: `GET /payments/{id}/refunds/document?format=pdf&includeUnsettled=false` is available to the owning customer or authorized Admin/SUPER_ADMIN. PDF is currently the only supported format. Customer results are limited to refund records created in the most recent 12 months; Admin/SUPER_ADMIN results include the full persisted history. By default, only `SUCCEEDED` refunds are listed. `includeUnsettled=true` includes `REQUESTED`, `PROCESSING`, and `FAILED` records with their status, but those records never contribute to the successful refunded total or net amount. The document is not a credit note or tax document and never changes payment, invoice, refund, or milestone state.

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

### Realtime transport

The authenticated realtime endpoint is `GET /ws` over WebSocket. The HTTP upgrade is origin-checked and the STOMP `CONNECT` frame must contain the bearer JWT in its `Authorization` native header; the server authenticates that frame before allowing subscriptions. Clients may subscribe to `/user/queue/valor-events`; only `ADMIN` and `SUPER_ADMIN` may subscribe to `/topic/admin`. Client `SEND` frames are not supported. The current simple broker configuration does not define an application heartbeat scheduler or promise a server idle-timeout/connection-liveness guarantee. Clients must treat WebSocket delivery as an update signal and refetch authoritative REST state; HTTP 101 confirms only the transport upgrade, not STOMP authentication or message delivery.

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
| GET /admin/checklists | ADMIN, SUPER_ADMIN | paginated read-only checklist summaries; optional `serviceRequestId`, `status=IN_PROGRESS|COMPLETED`, `page`, and `size` filters |
| GET /admin/checklists/{serviceRequestId} | ADMIN, SUPER_ADMIN | read-only `JobChecklistView` for the canonical service request, including template version, item definitions, saved responses, responder profile IDs, response timestamps, and current status |
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

The Phase 1 payment/invoice domain remains the application business system of record. Razorpay is an external gateway only behind the backend `PaymentGateway` abstraction, currently implemented by `RazorpayGateway`. Backend secrets are configured by environment variables `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET`; real gateway calls also require `RAZORPAY_MODE` to be explicitly `test` or `live`, matching the recognized `rzp_test_` or `rzp_live_` key prefix. Clients receive only the public key id and checkout order details. The application's `razorpay.offline-test-mode=true` is a separate local mock and bypasses this external-mode check; it must not be enabled for real Razorpay Test Mode verification.

| Method/path | Role | Input / data |
|---|---|---|
| POST /payments/razorpay/checkout | CUSTOMER owner, ADMIN/SUPER_ADMIN | invoiceId -> paymentId, invoiceId, razorpayKeyId, razorpayOrderId, amount, currency, status |
| POST /webhooks/razorpay | Razorpay signed webhook | raw body plus `X-Razorpay-Signature`; verifies signature, stores event id, processes idempotently |
| POST /payments/{id}/refund-requests | owner CUSTOMER | amount, reason? -> RefundView; creates a REQUESTED record only |
| POST /payments/{id}/refunds | ADMIN/SUPER_ADMIN | amount, reason? -> current PaymentView; creates/approves through the refund policy |
| POST /admin/payments/refunds/{id}/approve | ADMIN/SUPER_ADMIN | refundId, reason? -> current PaymentView; records the required approval and initiates only when policy permits |
| GET /payments/{id}/refunds | owner CUSTOMER, ADMIN/SUPER_ADMIN | RefundView[] |
| POST /admin/payments/refunds/{id}/reconcile | ADMIN/SUPER_ADMIN | Read-only lookup of the stored Razorpay refund reference; applies only verified `processed` or `failed` status |
| GET /admin/payments/reconciliation | ADMIN/SUPER_ADMIN | lightweight mismatch issue list |

Checkout creation never trusts a frontend amount. It validates invoice ownership/eligibility, rejects paid/void/cancelled invoices, rejects an existing processing/succeeded payment attempt for the invoice, creates a local `payment_records` row, and creates a Razorpay Order for the trusted invoice total/currency. Browser/app checkout return is not authoritative; clients should poll `GET /payments/{id}` and show pending until webhook/API synchronization updates the local payment. Application-level integration can be built without provider credentials; real gateway transactions require later external activation.

Razorpay event mapping currently handles documented event names `payment.authorized`, `payment.captured`, `payment.failed`, `refund.processed`, and `refund.failed`. Internal statuses remain compatible: `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `REFUNDED`, and `PARTIALLY_REFUNDED`. Unknown Razorpay events with valid signatures are stored as processed and ignored safely.

Refunds are INR-only: the canonical payment currency must be `INR`; unsupported currencies are rejected before a refund record is created or gateway initiation. Refund requests require an existing successful or partially refunded Razorpay payment, reject an active refund in progress, and reject over-refunding. Customer requests create a persisted REQUESTED record without gateway side effects. Approval requires `VALOR_REFUNDS_SAME_ADMIN_THRESHOLD`; an absent, blank, malformed, negative, or more-than-two-decimal value fails closed with the existing safe configuration error. The proposed project threshold is `5000.00` INR and must be explicitly configured in the deployment environment; it is not an application default. Amounts less than or equal to the configured threshold use one authorized Admin approval; amounts strictly above it require a second distinct Admin approval. Gateway initiation does not mark completion; webhook synchronization updates refund/payment state.

Refund operational visibility is provided by the existing Admin transaction listing/detail and persisted refund fields, including approval actors/reasons, gateway status, provider refund ID, timestamps, payment references, and safe amount/currency data. `POST /admin/payments/refunds/{id}/reconcile` is restricted to Admin/SuperAdmin and performs only a read-only lookup of the stored provider refund ID. The provider response must match the stored refund ID, original Razorpay payment ID, amount, and INR currency. Only documented terminal statuses `processed` and `failed` update local state; `pending`, unknown, missing, unavailable, or conflicting evidence remains unresolved and requires investigation. Repeated reconciliation of a terminal refund is a no-op, and reconciliation never calls refund creation or retries an uncertain refund. Confirmed `refund.processed`/`refund.failed` webhook events remain supported settlement evidence.

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

## Email-verified customer registration

New self-registered customers use `POST /auth/register/email/start`, `POST /auth/register/email/resend`, and `POST /auth/register/email/verify`. Start accepts the existing customer registration fields with a required normalized email and returns only an opaque `challengeId`, expiry, and `EMAIL_ACCEPTED` status. Resend accepts the challenge ID and applies a 60-second cooldown. Verify accepts the challenge ID and six-digit code and returns the normal authentication response only after the customer and profile are created transactionally. SMTP must be explicitly enabled and configured; disabled or failed delivery creates no canonical account. The legacy `/auth/register` route rejects new unverified registration with a safe migration message. Existing accounts and Admin-provisioned accounts retain their prior login behavior. SMS is not enabled.

Email is a backend-driven communication channel under `/api/v1` and reuses the Phase 17 communication APIs and data model. Clients do not receive provider secrets or provider configuration. The default provider remains the safe `mock` provider; real SMTP is opt-in with `EMAIL_PROVIDER=smtp` and `SMTP_ENABLED=true` (the legacy `EMAIL_*` aliases remain supported for compatibility).

Architecture:

```text
CommunicationService
  -> EmailProvider
    -> MockEmailProvider
    -> future provider adapter
```

`EmailProvider.send(...)` receives provider-neutral data only: recipient, sender, sender display name, reply-to, subject, HTML body, plain-text body, communication message id, event/template metadata, and idempotency key. No SMTP, SES, SendGrid, Mailgun, Resend, or other provider model is part of the core contract.

When SMTP is explicitly enabled, the provider reads `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `SMTP_FROM` through the server configuration. It requires authenticated STARTTLS and bounded connection, read, and write timeouts. Missing or invalid SMTP configuration fails the individual delivery safely; it never rolls back the originating business transaction and never exposes credentials. The application does not send passwords, password hashes, OTPs, tokens, or provider secrets in email content.

Configuration is generic and optional: `EMAIL_PROVIDER`, `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_USERNAME`, `EMAIL_PASSWORD`, `EMAIL_API_KEY`, `EMAIL_FROM`, `EMAIL_FROM_NAME`, `EMAIL_REPLY_TO`, `EMAIL_ENABLED`, and `APP_SET_PASSWORD_URL`. Real provider activation is deferred.

`communication_messages` remains the delivery source of truth. Email delivery uses the existing statuses `PENDING`, `PROCESSING`, `SENT`, `DELIVERED`, `FAILED`, and `CANCELLED`, stores provider/provider message references when available, retries transient failures with `retry_count`, `max_retry_count`, `next_retry_at`, and preserves sanitized `failure_reason`. The current implementation persists messages after the originating transaction and exposes authorized Admin processing/retry operations; it does not run an automatic email worker. A future worker requires an approved claim/lease design before concurrent dispatch can be enabled. SMTP acceptance is recorded as `SENT`, not proof of inbox delivery; ambiguous network failures can still produce provider-side duplicate delivery on a later retry.

Safe onboarding:

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/set-password` | Public one-time token | Set a password using an expiring onboarding token |

`POST /auth/set-password` accepts `{ token, password }`. Tokens are hashed at rest, expire, are single-use, and are not returned by read APIs. Account-created emails must use the configured set-password URL and must never include raw passwords.

Backend-triggered email events currently include customer account/set-password, service request created/status/completed/feedback, appointment scheduled/changed/cancelled, technician account/assignment/job status/visit/change-request decision, AMC renewal request, invoice created, payment result, and admin alert template support where existing backend events dispatch it. They are deduplicated by persisted event keys and honor recipient communication preferences. Weekly digests and earnings/payroll notifications are not enabled because this backend has no verified digest scheduler/timezone delivery contract or authoritative payroll/payout record suitable for those messages.

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
## Simple location concept

Building views and customer building create/update requests use the canonical nullable `latitude` and `longitude` fields. They are saved only during building create/edit and are reused by lifts and service requests.

Technician profile views and profile update requests expose nullable `latitude` and `longitude` for a saved default/home location. This location is private to operational/admin views and is never returned to customers.

The existing `POST /service-requests/{id}/status` contract accepts optional `startLatitude` and `startLongitude` when moving a technician job to `ON_THE_WAY`. The backend writes `startedAt` authoritatively once, ignores repeat same-state calls, and calculates a static `arrivalEstimateMinutes` from the start point and building point when both are available. No routing provider is called.

The former job live-location endpoints and route/geofence/ETA response are retained only for compatibility during migration and are not used by active clients. Customers receive status, building coordinates, and a static estimate only; technician home coordinates remain private.

## Phase 1 Dedicated Installation Request Foundation

Dedicated installation requests are separate from `/service-requests` and do not use the normal service-request status machine. Migration V39 adds installation requests, structured third-party property authorizations, request history, and document references. Phase 1 statuses are `DRAFT`, `SUBMITTED`, and `CANCELLED`; quotation, inspection, order, task, commissioning, handover, and lift activation states are not implemented yet.

Installation milestone billing storage is additive in V50. No milestone schedule, percentage split, tax rule, due-date policy, or billing trigger is approved or configured by this contract. Milestone schedule activation and invoice generation therefore remain blocked until an authorized schedule and trigger policy are introduced; no default advance or synthetic milestone is created.

### Phase 3 schedule configuration foundation (V51)

Installation schedules are immutable version records. A schedule revision uses the same stable `scheduleKey` and an increasing `versionNo`; a previously approved version is never edited in place. Lifecycle values are `DRAFT`, `AWAITING_APPROVAL`, `APPROVED`, `SUPERSEDED`, and `CANCELLED`. The server stores the supplied currency and ordered milestone-definition document, but does not supply defaults or infer percentages, fixed amounts, tax, due dates, or billing triggers. Terms must be non-empty and currency must be a three-letter code before submission.

| Method/path | Authorization | Input / data |
|---|---|---|
| GET /admin/installations/{requestId}/schedules | ADMIN, SUPER_ADMIN | versioned `InstallationScheduleView[]` |
| POST /admin/installations/{requestId}/schedules | ADMIN, SUPER_ADMIN | non-empty currency and milestone definitions -> new `DRAFT` version |
| POST /admin/installations/schedules/{id}/submit | ADMIN, SUPER_ADMIN | draft -> `AWAITING_APPROVAL` |
| POST /admin/installations/schedules/{id}/company-approve | ADMIN, SUPER_ADMIN | authenticated company actor; no actor ID in body |
| POST /customers/me/installations/schedules/{id}/approve | owning CUSTOMER | authenticated customer actor; ownership is checked against the schedule installation |

Company and customer approvals are persisted against the exact schedule version. Repeated approval of the same type is idempotent. The schedule becomes `APPROVED` only after both approvals; approval of another customer or a changed version is rejected. There is no management exception, client-supplied approval status, or billing activation flag. V50 milestone invoice generation remains disabled: an approved schedule is stored for future review only and does not create, activate, or settle milestones or invoices. Missing business terms or required approval leaves the schedule non-approvable and non-billable.

Milestone invoice generation is exposed only through `POST /admin/installations/milestones/{id}/invoice` for authenticated `ADMIN` or `SUPER_ADMIN` users. The server locks and loads the milestone, installation, linked schedule, and customer; request bodies cannot supply amount, currency, tax, trigger, status, ownership, or due date. Generation requires the exact linked schedule version to be `APPROVED`, both approval records, a matching milestone definition, positive authoritative amount, explicit tax amount, matching currency, and the supported authoritative trigger `INSTALLATION_COMPLETED` with the installation already `COMPLETED`. Missing or unverifiable conditions fail closed. Existing non-void/non-cancelled milestone invoices are reused, and the milestone is marked `INVOICED` only after invoice insertion succeeds. Generation never marks a milestone paid; existing verified payment, webhook, and reconciliation flows remain authoritative.

| Method/path | Authorization | Input / data |
|---|---|---|
| GET /customers/me/installations | CUSTOMER | `InstallationView[]` |
| POST /customers/me/installations | CUSTOMER | `InstallationWriteRequest` -> `InstallationView`; optional clientRequestKey makes draft creation idempotent |
| GET /customers/me/installations/{id} | owning CUSTOMER | `InstallationView` |
| PUT /customers/me/installations/{id} | owning CUSTOMER, draft only | `InstallationWriteRequest` with current version -> `InstallationView` |
| POST /customers/me/installations/{id}/submit | owning CUSTOMER, draft only | submissionKey and current version -> `InstallationView` |
| POST /customers/me/installations/{id}/cancel | owning CUSTOMER, draft/submitted | reason and current version -> `InstallationView` |
| POST /customers/me/installations/{id}/documents | owning CUSTOMER, draft only | opaque existing storage reference -> `InstallationView` |
| POST /admin/installations/authorizations/{id}/review | ADMIN, SUPER_ADMIN | `AuthorizationReviewRequest` -> `AuthorizationView` |

Customer identity is derived from the authenticated principal. An optional building must be active and owned by the customer. Third-party authorization is stored as `PENDING_REVIEW` and blocks submission until an Admin or SUPER_ADMIN approves it. Customer-owned drafts without a pre-existing lift are supported; no lift record is created by these endpoints. All successful responses use `ApiResponse<T>` with `/api/v1` routing. Optimistic version checks and customer-scoped client/submission keys prevent stale writes and repeated submission duplication.

## Phase 3 installation site details

Migration V40 adds nullable structured site fields to `installation_requests`: `siteAddressLine1`, `siteAddressLine2`, `siteLocality`, `siteCity`, `siteState`, `sitePostalCode`, `siteCountry`, and `sitePropertyDescription`. They round-trip through `InstallationDtos.WriteRequest` and `InstallationDtos.View` on the existing create, retrieve, list, and draft-update endpoints. Existing requests and requests linked to an owned `buildingId` remain valid; no Building or Lift is created by these operations.

When `buildingId` is supplied, the authenticated customer must own an active building. When it is absent, structured site details may identify the proposed property and are persisted on the installation request itself. Third-party owner/organization, relationship, and authorization reference remain separate structured fields. The server derives customer identity and authorization status; client input cannot approve or reject authorization. Submission continues to require an eligible building or structured authorization and blocks pending/rejected authorization. V40 fields are nullable for backward compatibility and retain the existing version, idempotency, history, audit, and ownership rules.

## Phase 4 inspection lifecycle

V41 adds a separate one-to-one inspection domain for submitted installations. Statuses are `REQUESTED`, `SCHEDULED`, `ASSIGNED`, `IN_PROGRESS`, `SUBMITTED`, `REVIEW_REQUIRED`, `APPROVED`, `REWORK_REQUIRED`, `CANCELLED`, and `RESCHEDULE_REQUIRED`. Submission does not approve feasibility or create a quotation, order, Building, or Lift. Admins request, schedule, assign, reschedule, cancel, and review. The assigned technician may read, start, record structured assessment data, add existing evidence references, and submit; technicians cannot review their own assessment. Customers receive only their own authorized appointment/outcome view, without internal reviewer notes, reviewer identity, assigned technician identity, or evidence references.

| Method/path | Authorization | Purpose |
|---|---|---|
| GET /customers/me/installations/{requestId}/inspection | owning CUSTOMER | Read appointment/outcome |
| POST /admin/installations/{requestId}/inspection | ADMIN, SUPER_ADMIN | Request one inspection |
| GET /admin/installations/inspections/{id} | ADMIN, SUPER_ADMIN | Read complete inspection |
| PUT /admin/installations/inspections/{id}/schedule | ADMIN, SUPER_ADMIN | Schedule |
| PUT /admin/installations/inspections/{id}/assign | ADMIN, SUPER_ADMIN | Assign active technician |
| PUT /admin/installations/inspections/{id}/reschedule | ADMIN, SUPER_ADMIN | Reschedule with reason |
| POST /admin/installations/inspections/{id}/cancel | ADMIN, SUPER_ADMIN | Cancel with reason |
| POST /admin/installations/inspections/{id}/review | ADMIN, SUPER_ADMIN | Approve or return assessment |
| GET /technician/me/inspections/{id} | assigned TECHNICIAN | Read assigned inspection |
| POST /technician/me/inspections/{id}/start | assigned TECHNICIAN | Start visit |
| PUT /technician/me/inspections/{id}/assessment | assigned TECHNICIAN | Submit assessment |
| POST /technician/me/inspections/{id}/evidence | assigned TECHNICIAN | Add existing evidence reference |

Discovery endpoints use the standard `PageView<T>` fields `items`, `page`, `size`, `totalElements`, and `totalPages`, inside the normal `ApiResponse<T>` envelope. `GET /admin/installations/{requestId}/inspection` returns the single inspection linked by V41 or the standard not-found response. `GET /admin/installations/inspections` accepts optional `status`, `technicianProfileId`, `from`, `to` ISO local date-times, `page` (default 0), and `size` (default 20, maximum 100); results are ordered by newest creation then ID descending. `GET /technician/me/inspections` accepts optional `status`, `page`, and `size` and derives the technician from the bearer principal; it never accepts a technician ID. Both queue responses use `InspectionSummary`, containing inspection ID, installation request ID, status, schedule/timezone, assigned technician ID where authorized, visit/submission timestamps, persisted property/address context, and inspection version. Admins receive complete inspection detail; assigned technicians receive only their assigned inspection; customer detail continues to redact reviewer identity/notes and evidence references.

## Lifecycle notifications

## Complete implemented route inventory

### Protected Admin profile photos

The existing profile-photo storage also supports read-only Admin access: `GET /api/v1/profile/photo/admin/customers/{customerProfileId}` and `GET /api/v1/profile/photo/admin/technicians/{technicianProfileId}`. Only `ADMIN` and `SUPER_ADMIN` may call these routes; the path is resolved through the canonical customer or technician profile and returns `{ dataUri }`, with `dataUri: null` when no photo exists. Upload and delete remain owner-scoped through `GET/POST/DELETE /api/v1/profile/photo`.

This inventory supplements the detailed contracts above. Paths are grouped by controller and reflect the current backend mappings. Request validation, ownership checks, and role authorization remain enforced by the implementation; undocumented request fields must not be inferred from a path name.

### Public/authentication and common authenticated routes

- `GET /api/v1/health` — health check.
- `POST /api/v1/auth/register`, `/api/v1/auth/login/customer`, `/api/v1/auth/login/admin`, `/api/v1/auth/login/technician` — registration and role-specific login.
- `POST /api/v1/auth/otp/send`, `/otp/resend`, `/otp/verify`, `/auth/refresh`, `/auth/set-password`, `/auth/logout`; `GET /api/v1/me` — OTP, token, password, logout, and current identity operations.
- `GET/PUT /api/v1/customers/me`, `GET /api/v1/customers/me/technician-counts`; `GET/POST/PUT/DELETE /api/v1/profile/photo` — authenticated profile operations.
- `GET /api/v1/locations/search`, `/locations/reverse`, `/locations/pincodes/{pincode}` — authenticated location lookup operations.

### Customer assets, service, visits, and documents

- Customer buildings/lifts: `GET/POST/PUT/DELETE /api/v1/customers/me/buildings`, `/customers/me/buildings/{id}`, `/customers/me/lifts`, `/customers/me/lifts/{id}`; compatible asset routes also exist under `/api/v1/buildings`, `/lifts`, and `/amc-contracts` with server-side ownership checks.
- Service requests: `POST/GET /api/v1/service-requests`, `GET /api/v1/service-requests/{id}`, `POST /api/v1/service-requests/{id}/assignments`, `POST /api/v1/service-requests/{id}/assignments/{assignmentId}/accept`, `POST /api/v1/service-requests/{id}/status`, `GET /api/v1/customers/me/service-requests`, and technician job/report routes under `/api/v1/technician/me/jobs`.
- Request attachments/feedback: `GET/POST/DELETE /api/v1/service-requests/{id}/attachments[/{attachmentId}]`, `GET/PUT /api/v1/service-requests/{id}/feedback`, and `GET /api/v1/service-requests/{id}/report.pdf`.
- Visits: Admin `/api/v1/admin/service-visits` (list/create/read/update/cancel), technician `/api/v1/technician/me/visits` (list/read/status/reschedule/additional-visit/cancel), Admin visit-change request list/approve/reject, and `GET /api/v1/customers/me/visits`.
- Service codes: `GET /api/v1/customers/me/lifts/{liftId}/service-code`, `POST /api/v1/customers/me/lifts/{liftId}/service-code/regenerate`; `POST /api/v1/technician/me/jobs/{requestId}/service-code/verify` accepts explicit `START` or `COMPLETE` action and returns safe verification state only.
- Tracking and workflow support: technician location post/read, customer technician-location read, checklists, arrival/completion OTPs, private attachments, and job status routes are under `/api/v1` with role/assignment checks.

### Installation and schedule routes

- Customer installation lifecycle: `GET/POST /api/v1/customers/me/installations`, `GET/PUT /{id}`, `POST /{id}/submit`, `/cancel`, and `/documents`.
- Installation proposals: Admin history/save/present/start/requirements/complete under `/api/v1/admin/installations`; customer proposal read/history/decision under `/api/v1/customers/me/installations`.
- Assignments: Admin list/create/cancel under `/api/v1/admin/installations`; technician list/accept/decline under `/api/v1/technician/me/installations/assignments`.
- Inspection: customer read; Admin request/read/queue/update/cancel; technician inspection list/read/start/assess/evidence routes under `/api/v1`.
- Schedule approval: Admin list/create/submit/company-approve under `/api/v1/admin/installations`; customer exact-version approve under `/api/v1/customers/me/installations/schedules/{id}/approve`.
- Milestone billing: `POST /api/v1/admin/installations/milestones/{id}/invoice` is the narrowly scoped gated generation operation. It requires the exact approved schedule, approvals, terms, trigger, ownership, and milestone eligibility; it does not invent financial terms.
- Installation authorization review: `/api/v1/admin/installations/authorizations` list/read/review.

### Payments, invoices, refunds, and finance

- Payments: `GET/POST /api/v1/payments`, `GET /api/v1/payments/{id}`, `PUT /api/v1/payments/{id}/status` (Admin/SuperAdmin only), Razorpay checkout, cash OTP request/verify, cash OTP state, and `GET /api/v1/payments/{id}/receipt.pdf`.
- Refunds: `POST /api/v1/payments/{id}/refund-requests` (customer request), `POST /api/v1/payments/{id}/refunds` (authorized provider initiation path), `GET /api/v1/payments/{id}/refunds`, `GET /api/v1/payments/{id}/refunds/document`, Admin approval, Admin payment reconciliation, and Admin refund reconciliation.
- Webhook: `POST /api/v1/webhooks/razorpay`; a valid configured Razorpay signature and recognized event are required. Duplicate/invalid/mismatched events fail safely and do not authorize settlement.
- Invoices: `GET/POST /api/v1/invoices`, `GET /api/v1/invoices/{id}`, status update, and technician payment view. Invoice generation is server-priced and association/ownership validated.
- Admin finance: `GET /api/v1/admin/transactions`, `/transactions/{id}`, `/transactions.csv`, `/reports/{type}`, and `/reports/{type}.csv`.

### Administration, staff, communication, and configuration

- Admin customer/directory/staff/settings/security: `/api/v1/admin/customers`, `/admin/technicians`, `/admin/customers`, `/admin/users`, `/admin/settings`, `/admin/permissions`, `/admin/roles`, `/admin/audit-logs`, and the Admin dashboard summary.
- Technician applications: public application routes under `/api/v1/technician-applications`; Admin review/status/document/meeting routes under `/api/v1/admin/technician-applications`.
- Communication: Admin message/event listing/creation, manual message processing, retry processing, and preference GET/PUT under `/api/v1/admin/communications`; in-app notifications under `/api/v1/notifications`.
- Asset configuration and parts: `/api/v1/config/asset-options`, Admin asset-option management, customer/technician/Admin parts and part-request routes as mapped by `AssetConfigurationController` and `PartsController`.

### Compatibility routes

The repository also contains older routes under `/api`: authentication aliases, `/api/customers`, `/api/buildings`, `/api/lifts`, `/api/service-requests`, `/api/amcs`, `/api/payments`, `/api/notifications`, `/api/inventory`, `/api/admin`, `/api/technicians`, and `/api/reports/summary`. They remain compatibility mappings and must still enforce the service-layer ownership and authorization checks. New clients should use the `/api/v1` contracts above.

For database relationships and payment authority, see [`SCHEMA.md`](../database/SCHEMA.md), [`DB_BACKEND_STRUCTURE.md`](../database/DB_BACKEND_STRUCTURE.md), and [`PAYMENTS.md`](PAYMENTS.md). The privacy reference is [`PRIVACY_POLICY.md`](../policies/PRIVACY_POLICY.md).

The existing `/api/v1/notifications` inbox stores in-app notifications only; email, SMS, push, and external delivery are not enabled by this contract. Successful building creation, lift registration, installation submission, inspection scheduling/rescheduling, and inspection review create customer-scoped notifications. Each system-generated lifecycle notification carries an internal `eventKey` with a unique database constraint so retries of the same persisted event do not create duplicates. Notification responses do not expose event keys or reviewer/private evidence data. Customer notification reads remain scoped to the authenticated recipient.
Installation proposal revisions are immutable once presented or decided. Admin proposal saves create a new draft revision after a declined proposal; the existing declined row is never overwritten. `GET /admin/installations/{id}/proposals` returns the full revision history ordered newest first. `GET /customers/me/installations/{id}/proposal` returns the newest non-draft proposal, and `GET /customers/me/installations/{id}/proposals` returns the customer-safe non-draft history. Customer decisions must include the current proposal `id` and `version`; stale, unpresented, or already-decided revisions are rejected. Construction start remains gated by the installation request status `PROPOSAL_ACCEPTED`.
