# Canonical backend API contract

Scope: canonical Stage 1-4 API on main, reconciled with ../TARGET_VALOR_SCHEMA_SPEC.md, plus the approved Scheduling / Service Visit contract below. Flyway V1-V4 remain unchanged; Scheduling / Service Visits are added in V5. This reconciliation is verified using isolated H2/MockMvc; real-MySQL endpoint verification and client migration have not been performed. Android, Admin Portal, Website and Valor-technician remain unchanged.

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

`CustomerCreateRequest` accepts `email`, `phone`, `password`, `fullName`, `alternatePhone`, `companyName`, and `address`. `fullName` and `password` are required. At least one canonical identity (`email` or `phone`) is required, and email/phone use the same normalization and duplicate checks as customer self-registration. Password input is writeOnly, limited by the existing BCrypt byte-length rule, and is never returned. The response never includes passwordHash, OTP data, refresh-token hashes, lock counters, deletedAt, or submitted credentials. Creation creates exactly one canonical `users` row with role CUSTOMER and one canonical `customer_profiles` row; it does not create buildings, lifts, AMCs, service requests or visits.

`CustomerUpdateRequest` accepts only profile fields already supported by customer self-service: `fullName`, `alternatePhone`, `companyName`, and `address`. Admin update cannot change email, phone, role, password, authentication state, profile ID, user ID, rating, or ownership identifiers. Unknown fields are rejected.

`AdminCustomerDetail` contains `userId`, `customerProfileId`, `email`, `phone`, `fullName`, `alternatePhone`, `companyName`, `address`, `active`, `status`, `createdAt`, `updatedAt`, and an operational summary: `buildingCount`, `liftCount`, `serviceRequestCount`, plus lightweight `buildings`, `lifts`, and recent `serviceRequests` arrays where canonical relationships already exist. Building rows include id/name/type/city/status/isActive. Lift rows include id/buildingId/name/liftNumber/currentStatus/isActive. Recent service-request rows use the existing `RequestView` shape. These summary arrays are read-only convenience projections; use the canonical asset and service-request APIs for writes.

Deactivation sets the canonical user inactive, sets the customer profile inactive, and sets profile status to `INACTIVE`. Authentication and refresh already reject inactive users, so deactivated customers cannot log in or rotate sessions. Existing bearer tokens become unusable on the next authenticated request because account usability is rechecked. Existing refresh-token rows are retained for audit/rotation semantics and remain rejected by account-state checks. Deactivation is idempotent and non-destructive; historical service requests, buildings, lifts, AMCs, visits and history remain visible to admins.

Reactivation sets the canonical user active, sets the customer profile active, and restores profile status to `ACTIVE`. It does not change existing passwords, identities, assets, service requests, assignments, visits, or history. Reactivation is idempotent. CUSTOMER and TECHNICIAN users cannot call these routes. ADMIN and SUPER_ADMIN have the same customer-management authority; staff management remains separately restricted to SUPER_ADMIN.

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
| GET /service-requests/{id} | owner CUSTOMER, current TECHNICIAN (historically assigned for terminal requests), ADMIN, SUPER_ADMIN | Detail |
| GET /service-requests | ADMIN, SUPER_ADMIN | status?, priority?, page=0, size=20 -> PageView<RequestView> |
| POST /service-requests/{id}/assignments | ADMIN, SUPER_ADMIN | technicianProfileId, notes? -> Detail |
| POST /service-requests/{id}/assignments/{assignmentId}/accept | assigned TECHNICIAN | Detail |
| POST /service-requests/{id}/status | assigned TECHNICIAN, ADMIN, SUPER_ADMIN | toStatus, notes? -> Detail |
| GET /technician/me/jobs | TECHNICIAN | status?, page=0, size=20 -> PageView<RequestView> |
| GET /technician/me/jobs/{id} | current TECHNICIAN; historically assigned TECHNICIAN for terminal requests | Detail |
| POST /technician/me/jobs/{id}/report | assigned TECHNICIAN | diagnosis, workPerformed, testingResult; completionNotes? -> ReportView |

CreateRequest requires liftId, title, description, serviceType. Optional issueCategory, priority (MEDIUM default), customerRemarks, preferredVisitDate, preferredTimeSlot; customerProfileId is required for admin creation and forbidden for CUSTOMER. internalAdminNotes and estimatedCompletionMinutes are also admin-only and rejected from CUSTOMER. Customer ownership derives exclusively from JWT and must match the lift's building owner. Submitted customerId and unknown fields are rejected.

Detail contains request (RequestView), nullable activeAssignment (AssignmentView), immutable history (HistoryView[]) and nullable report (ReportView). Reports are loaded by unique serviceRequestId independently of active assignment. Authorized customer owners, admins and technicians with historical assignment rows can read reports on COMPLETED/CANCELLED requests, including technician job detail. Nonterminal technician access still requires the active assignment; unrelated users remain forbidden. History notes are returned to all authorized viewers; internalAdminNotes remain admin-only. RequestView contains id/serviceId, customerProfileId/liftId, title/description/category, priority/status/serviceType, remarks, scheduling/completion/estimate and timestamps. Internal notes are hidden from customers. AssignmentView contains id, serviceRequestId, technicianProfileId, status, assignedByUserId, assignedAt, acceptedAt, releasedAt, notes. HistoryView contains id, nullable fromStatus, toStatus, changedByUserId, notes, changedAt. ReportView contains id, serviceRequestId, assignmentId, diagnosis, workPerformed, testingResult, completionNotes, reportedByUserId and timestamps. PageView contains items, page, size, totalElements, totalPages; page must be nonnegative and size is 1-100.

Request statuses: PENDING, ASSIGNED, ACCEPTED, ON_THE_WAY, REACHED_SITE, DIAGNOSIS, REPAIR_IN_PROGRESS, WAITING_FOR_PARTS, TESTING, COMPLETED, CANCELLED. Assignment statuses: ASSIGNED, ACCEPTED, REJECTED, RELEASED, COMPLETED. Priorities: LOW, MEDIUM, HIGH, EMERGENCY. Service types: ROUTINE_MAINTENANCE, BREAKDOWN, EMERGENCY, INSPECTION, INSTALLATION, MODERNIZATION. Java enums and V3 checks remain aligned.

The approved transition graph is unchanged. Request row locking coordinates assignment, reassignment, report, status and completion; the existing generated unique active-assignment index remains authoritative. Creation writes one initial PENDING event; every accepted transition writes one event. Reassignment retains released rows and allows the same technician later without a status-history event when lifecycle state is unchanged. Acceptance after advanced reassignment likewise does not emit a same-status event. Event creation rejects equal from/to states; the initial NULL -> PENDING event remains valid. Supplied transition notes are persisted and returned, including waiting notes and cancellation reasons. Cancellation requires a reason and releases the active assignment. WAITING_FOR_PARTS requires notes. TESTING -> COMPLETED requires a nonblank report for the current active assignment, even for admins; completion atomically records completedAt, completes the assignment and writes history. Report POST does not complete a job; terminal reports are immutable.

## Scheduling / Service Visits

A ServiceRequest represents the customer's service need. A ServiceVisit represents one planned field-service appointment for that request. Every visit belongs to an existing service request. A request may have multiple historical visits, but only one active/upcoming visit may exist at a time. Completed and cancelled visits remain associated with the request. A visit is not a replacement for the service-request state machine.

| Method/path | Role | Input / data |
|---|---|---|
| GET /admin/service-visits | ADMIN, SUPER_ADMIN | `fromDate?`, `toDate?`, `technicianProfileId?`, `status?`, `page=0`, `size=20` -> `PageView<VisitView>` |
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

## Migration and client readiness

V1-V4 remain unchanged. V5 adds typed service visits and visit-change requests with restrictive foreign keys, date/technician/request indexes, active-visit uniqueness, and time-range validation. The H2 test adapter still only removes V3 STORED syntax for H2; it is not deployed and cannot prove MySQL storage semantics. The user previously reported V1-V4 verified against real MySQL; the new code still needs real-MySQL endpoint verification before client migration. All client repositories and unrelated README changes remain untouched. Payments, inventory, providers, checklists/parts/attachments, invoices, exports and other excluded features remain unimplemented.
