# Frozen Phase-1 backend API contract

Scope: canonical Stage 1-4 API on main, reconciled with ../TARGET_VALOR_SCHEMA_SPEC.md. Flyway V1-V4 and MySQL schema are unchanged. This reconciliation is verified using isolated H2/MockMvc; real-MySQL endpoint verification and client migration have not been performed. Android, Admin Portal, Website and Valor-technician remain unchanged.

## Common contract

All paths below have prefix `/api/v1`. Success is HTTP 200 with `ApiResponse<T>`: success, message, data, status, timestamp. Staff and domain views are flat DTOs; no identity entities or passwordHash/otpHash/tokenHash fields are serialized. Input passwords are writeOnly with password format in OpenAPI. Every operation has an explicit stable operationId. Shared ApiErrorResponse components describe safe 400 validation/authentication/OTP/refresh failures, 401 unauthenticated, 403 forbidden, 404 missing resources and 409 conflicts. Error data is null or an empty object; errors never return submitted secrets or internal exception details. Logout and asset deactivation retain their existing message envelopes with data=null, not a fabricated result object.

Default port: 8081. Swagger: /swagger-ui.html. OpenAPI: /v3/api-docs. Public health: GET /api/v1/health with data.status=UP. Other routes require authentication except register, login, OTP send/verify and refresh. Runtime configuration is unchanged: Flyway enabled, Hibernate validate, SQL initialization disabled; credentials come from the process environment, not automatic .env loading. Production requires external credentials and never enables development bootstrap.

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

## Customer profile and owned assets

| Method/path | Role | Input / data |
|---|---|---|
| GET /customers/me | CUSTOMER | CustomerSummary |
| PUT /customers/me | CUSTOMER | fullName, alternatePhone?, companyName?, address? -> CustomerSummary |
| GET /customers/me/buildings | CUSTOMER | owned BuildingView[] |
| POST /customers/me/buildings | CUSTOMER | buildingName; buildingType/address/city/state/pincode/emergencyContactName/emergencyContactPhone optional -> BuildingView |
| GET /customers/me/lifts | CUSTOMER | owned LiftView[] |
| GET /customers/me/service-requests | CUSTOMER | status?, page=0, size=20 -> PageView<RequestView> |

Ownership always resolves from JWT; customer building creation accepts no customer/profile ID, status, or active flag. Profile updates cannot change identity, role or account state. Inactive profiles cannot initiate operations. There is exactly one customer request-creation route: POST /service-requests.

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

BuildingWrite requires customerProfileId and buildingName; accepts buildingType, address, city, state, pincode, emergencyContactName, emergencyContactPhone, status. BuildingView includes those fields, id, isActive, activeLiftCount, createdAt, updatedAt. Building ownership cannot be transferred by update.

LiftWrite requires buildingId and name; accepts liftNumber, model, manufacturer, capacity, floorCount, serialNumber, installationDate, location, currentStatus, warrantyStatus, warrantyStartDate, warrantyEndDate, lastMaintenanceDate, nextMaintenanceDate, healthScore, machineRoom, qrCode, specifications. customerId is rejected. LiftView includes these fields, id, isActive, amcCoverage, asOfDate, createdAt, updatedAt. healthScore is a nullable JSON integer 0-100 in both directions (numeric Byte mapping to existing TINYINT); no String conversion or migration. Lift statuses: ACTIVE, DOWN, MAINTENANCE, OUT_OF_SERVICE. Derived amcCoverage is ACTIVE or NON_AMC. Ownership cannot be transferred by update.

AmcWrite requires liftId, amcNumber, plan, startDate, endDate; accepts coverageDetails, renewalDate. AmcRenew requires plan/startDate/endDate and accepts coverageDetails/renewalDate. AmcView adds id, status, lastReminderSentAt, renewalCount, covered, asOfDate and timestamps. AMC statuses: ACTIVE, EXPIRED, NON_AMC, CANCELLED, RENEWED. Renewal must begin after the existing end date; endDate cannot precede startDate. Coverage uses one service-supplied business date and contract queries. Lift counts remain repository projections; no derived columns are introduced. Deactivation never deletes rows. New asset operations require active account/profile/building/lift parents.

## Service workflow

| Method/path | Role | Input / data |
|---|---|---|
| POST /service-requests | CUSTOMER, ADMIN, SUPER_ADMIN | CreateRequest -> Detail |
| GET /service-requests/{id} | owner CUSTOMER, assigned TECHNICIAN, ADMIN, SUPER_ADMIN | Detail |
| GET /service-requests | ADMIN, SUPER_ADMIN | status?, priority?, page=0, size=20 -> PageView<RequestView> |
| POST /service-requests/{id}/assignments | ADMIN, SUPER_ADMIN | technicianProfileId, notes? -> Detail |
| POST /service-requests/{id}/assignments/{assignmentId}/accept | assigned TECHNICIAN | Detail |
| POST /service-requests/{id}/status | assigned TECHNICIAN, ADMIN, SUPER_ADMIN | toStatus, notes? -> Detail |
| GET /technician/me/jobs | TECHNICIAN | status?, page=0, size=20 -> PageView<RequestView> |
| GET /technician/me/jobs/{id} | assigned TECHNICIAN | Detail |
| POST /technician/me/jobs/{id}/report | assigned TECHNICIAN | diagnosis, workPerformed, testingResult; completionNotes? -> ReportView |

CreateRequest requires liftId, title, description, serviceType. Optional issueCategory, priority (MEDIUM default), customerRemarks, preferredVisitDate, preferredTimeSlot; customerProfileId is required for admin creation and forbidden for CUSTOMER. internalAdminNotes and estimatedCompletionMinutes are also admin-only and rejected from CUSTOMER. Customer ownership derives exclusively from JWT and must match the lift's building owner. Submitted customerId and unknown fields are rejected.

Detail contains request (RequestView), nullable activeAssignment (AssignmentView), immutable history (HistoryView[]) and nullable report (ReportView). Existing response shapes are preserved. RequestView contains id/serviceId, customerProfileId/liftId, title/description/category, priority/status/serviceType, remarks, scheduling/completion/estimate and timestamps. Internal notes are hidden from customers. AssignmentView contains id, serviceRequestId, technicianProfileId, status, assignedByUserId, assignedAt, acceptedAt, releasedAt, notes. HistoryView contains id, nullable fromStatus, toStatus, changedByUserId, notes, changedAt. ReportView contains id, serviceRequestId, assignmentId, diagnosis, workPerformed, testingResult, completionNotes, reportedByUserId and timestamps. PageView contains items, page, size, totalElements, totalPages; page must be nonnegative and size is 1-100.

Request statuses: PENDING, ASSIGNED, ACCEPTED, ON_THE_WAY, REACHED_SITE, DIAGNOSIS, REPAIR_IN_PROGRESS, WAITING_FOR_PARTS, TESTING, COMPLETED, CANCELLED. Assignment statuses: ASSIGNED, ACCEPTED, REJECTED, RELEASED, COMPLETED. Priorities: LOW, MEDIUM, HIGH, EMERGENCY. Service types: ROUTINE_MAINTENANCE, BREAKDOWN, EMERGENCY, INSPECTION, INSTALLATION, MODERNIZATION. Java enums and V3 checks remain aligned.

The approved transition graph is unchanged. Request row locking coordinates assignment, reassignment, report, status and completion; the existing generated unique active-assignment index remains authoritative. Creation writes one initial PENDING event; every accepted transition writes one event. Reassignment retains released rows and allows the same technician later. Cancellation requires a reason and releases the active assignment. WAITING_FOR_PARTS requires notes. TESTING -> COMPLETED requires a nonblank report for the current active assignment, even for admins; completion atomically records completedAt, completes the assignment and writes history. Report POST does not complete a job; terminal reports are immutable.

## Notifications and dashboard

| Method/path | Role | Input / data |
|---|---|---|
| GET /notifications | authenticated recipient | status?, page=0, size=20 -> NotificationPageResponse |
| POST /notifications | ADMIN, SUPER_ADMIN | NotificationCreateRequest -> NotificationResponse |
| PUT /notifications/{id}/read | recipient only | NotificationResponse |
| GET /admin/dashboard/summary | ADMIN, SUPER_ADMIN | Summary |

NotificationCreateRequest requires recipientUserId, title, message; channel defaults to IN_APP and only IN_APP is accepted. EMAIL/SMS/PUSH remain reserved storage values, rejected as requests and not advertised as usable input channels. scheduledAt is optional UTC local date/time. Responses contain id, recipientUserId, title, message, channel, status, scheduledAt, sentAt, readAt and timestamps. Status values are PENDING, SENT, FAILED, READ. New in-app records are PENDING with sentAt null. Inbox is recipient-scoped and only includes unscheduled/due records, ordered createdAt/id descending. Read checks ownership and due time and is idempotent, preserving readAt. No deletion, external delivery or worker exists.

Dashboard Summary fields are totalCustomers, totalLifts, totalRequests, pendingJobs, completedJobs, emergencyJobs, totalTechnicians, totalAmcs. Counts include retained rows; pending/completed count exact lifecycle status and emergencyJobs counts priority EMERGENCY. Dashboard is the specific ADMIN/SUPER_ADMIN exception to the otherwise SUPER_ADMIN-only /admin/** security rule.

## Migration and client readiness

No V1-V4 edits, schema additions, manual SQL or MySQL access occurred during reconciliation. The H2 test adapter still only removes V3 STORED syntax for H2; it is not deployed and cannot prove MySQL storage semantics. The user previously reported V1-V4 verified against real MySQL; the new code still needs real-MySQL endpoint verification before client migration. All client repositories and unrelated README changes remain untouched. Payments, inventory, providers, visits/checklists/parts/attachments, invoices, exports and other excluded features remain unimplemented.
