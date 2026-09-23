# Valor Technician Backend Reference

Source of truth: this document is derived only from the `Valor-Backend` Java code and Flyway migrations. The active schema is the Flyway schema under `src/main/resources/db/migration`. `schema.sql` contains older/fresh-reference table names and should not be treated as the current runtime contract.

## Technician Domain Scope

The technician backend is centered on a `users` row with role `TECHNICIAN` and a one-to-one `technician_profiles` row. Technician work then connects through assignments, jobs, service visits, reports, checklists, arrival OTPs, completion OTPs, cash payment OTP verification, private attachments, live tracking/maps, notifications, and communication events.

Technician job access is assignment-based. A technician can read and update only jobs or visits tied to their active `technician_profiles` row unless acting through admin endpoints.

## Core Backend Files

- Identity/staff: `src/main/java/com/valor/auth/AuthController.java`, `AuthComponents.java`, `TechnicianProfile.java`, `StaffController.java`, `DirectoryController.java`
- Technician profile/dashboard: `src/main/java/com/valor/workflow/TechnicianMeController.java`, `TechnicianDtos.java`
- Workflow/jobs/reports: `WorkflowController.java`, `WorkflowService.java`, `WorkflowDtos.java`
- Visits: `ServiceVisitController.java`, `ServiceVisitService.java`, `ServiceVisitDtos.java`
- Advanced technician operations: `Phase15Controller.java`, `ChecklistService.java`, `CompletionOtpService.java`, `TechnicianPrivateAttachmentService.java`
- Tracking: `src/main/java/com/valor/tracking/TrackingController.java`, `TrackingService.java`, tracking models/repositories
- Payments/cash OTP: `src/main/java/com/valor/commerce/CommerceController.java`, `CommerceService.java`, `CashPaymentOtpModels.java`
- Notifications/communication: `NotificationController.java`, `CommunicationService.java`, `EmailEventService.java`

## Identity And Profile Tables

- `users`: shared identity row. Technician rows use `role = TECHNICIAN`.
- `technician_profiles`: one row per technician user. Fields include `user_id`, `employee_id`, `assigned_area`, `specialization`, `availability_status`, `rating`, `last_working_day`, `last_active_at`, `is_active`, timestamps.
- Phase 15 profile fields: `profile_photo_url`, `date_of_birth`, `gender`, `address`, `emergency_contact_name`, `emergency_contact_phone`.
- `refresh_tokens`: refresh-token rotation state.
- `onboarding_tokens`: password setup links for admin-created technicians.

Technician availability values are `AVAILABLE`, `BUSY`, `OFF_DUTY`, and `ON_LEAVE`.

## Technician Auth And Provisioning APIs

- `POST /api/v1/auth/login/technician`: technician email/password login.
- `POST /api/v1/auth/refresh`: refresh-token rotation.
- `POST /api/v1/auth/logout`: revoke refresh token.
- `POST /api/v1/auth/set-password`: set password from onboarding token.
- `GET /api/v1/me`: current identity plus technician profile when actor is a technician.
- `POST /api/v1/admin/users`: create admin or technician staff user. For technicians, creates both `users` and `technician_profiles`.
- `DELETE /api/v1/admin/users/{userId}`: deactivate staff; technician deactivation also marks `technician_profiles.is_active = false`.
- `GET /api/v1/admin/technicians`: paged technician directory with `page`, `size`, `q`, and `active`.
- `GET /api/v1/admin/technicians/{id}`: technician profile by profile ID.
- `PUT /api/v1/admin/technicians/{id}`: admin update of technician availability, assigned area, specialization, and editable profile fields.

Staff creation is SUPER_ADMIN-only in `StaffService`. Technician fields are allowed only for role `TECHNICIAN`; admin staff cannot include technician profile fields. Unknown JSON fields are rejected by strict DTOs where implemented.

## Technician Self APIs

- `GET /api/v1/technician/me/profile`
- `PUT /api/v1/technician/me/profile`
- `GET /api/v1/technician/me/dashboard`

Editable self profile fields include availability status, profile photo URL, date of birth, gender, address, emergency contact name, and emergency contact phone. Updating the profile also refreshes `last_active_at`.

`TechnicianProfileView` includes `userId`, `technicianProfileId`, `email`, `phone`, `employeeId`, `assignedArea`, `specialization`, `availabilityStatus`, `active`, `lastActiveAt`, `profilePhotoUrl`, `dateOfBirth`, `gender`, `address`, `emergencyContactName`, and `emergencyContactPhone`. Email, phone, employee ID, area, specialization, and active state are controlled by Admin/staff flows and are read-only to the Technician app.

`TechnicianDashboard` returns `assignedJobs`, `pendingJobs`, `inProgressJobs`, `completedJobs`, `completedThisQuarter`, `todaysScheduledVisits`, and `emergencyJobs`. Counts are resolved from real assignments, visits, and service request statuses for the signed-in technician profile.

## Job And Assignment Tables

- `service_requests`: the underlying job/request raised by customer or admin.
- `technician_assignments`: links technician profiles to service requests. Tracks assignment status, assigned-by user, assigned/accepted/released timestamps, and notes.
- `service_reports`: technician-submitted work report for an assignment.
- `service_status_history`: request status transitions.
- `service_request_attachments`: shared files attached to service requests.
- `service_request_feedback`: customer feedback after completion.

## Technician Job APIs

- `GET /api/v1/technician/me/jobs`: assigned technician jobs with optional `status`, `page`, and `size`.
- `GET /api/v1/technician/me/jobs/{id}`: assigned job detail. Terminal completed/cancelled requests remain readable when the technician has historical assignment.
- `POST /api/v1/technician/me/jobs/{id}/report`: submit service report. Required fields are `diagnosis`, `workPerformed`, and `testingResult`; `completionNotes` is optional.
- `POST /api/v1/service-requests/{id}/assignments/{assignmentId}/accept`: accept an assignment.
- `POST /api/v1/service-requests/{id}/status`: technician status updates for assigned jobs with `{ "toStatus": "...", "notes": "..." }`.
- `GET/POST/GET/DELETE /api/v1/service-requests/{id}/attachments...`: shared request attachments, subject to request access.
- `GET /api/v1/service-requests/{id}/arrival-otp`: latest arrival OTP state for a request.
- `GET /api/v1/service-requests/{id}/completion-otp`: latest completion OTP state for a request.

Job detail returns canonical service request data, not a separate technician job table. The mobile app can show `serviceId`, title/description, priority, status, service type, schedule/date/time fields, customer name, building name/address, lift name/number, active assignment, history, and the structured report when available. Customer credentials, password state, token state, unrelated customer records, and raw OTPs are never returned to the technician.

Status values are `PENDING`, `ASSIGNED`, `ACCEPTED`, `ON_THE_WAY`, `REACHED_SITE`, `DIAGNOSIS`, `REPAIR_IN_PROGRESS`, `WAITING_FOR_PARTS`, `TESTING`, `COMPLETED`, and `CANCELLED`. Technician app labels should map these to the UX flow, for example Start Job -> `ACCEPTED`/`ON_THE_WAY`, Reached Location -> `REACHED_SITE`, Start Work -> `DIAGNOSIS` or `REPAIR_IN_PROGRESS`, Testing -> `TESTING`, and Complete Job -> `COMPLETED`.

Before a request can move to completion, the backend requires a service report, completed required checklist responses when an applicable template exists, and a verified completion OTP.

## Arrival OTP, Completion OTP, And Job Gates

Arrival OTP confirms the technician is at the customer site before work starts:

- `POST /api/v1/technician/me/jobs/{id}/arrival-otp/request`: assigned technician requests an arrival OTP state.
- `POST /api/v1/technician/me/jobs/{id}/arrival-otp/verify`: assigned technician verifies `{ otpId, otp }`.
- `GET /api/v1/service-requests/{id}/arrival-otp`: authorized request participants read safe state. Customer-visible codes are only exposed to the owning customer while pending and valid.

Completion OTP confirms customer approval before job completion:

- `POST /api/v1/technician/me/jobs/{id}/completion-otp/request`: assigned technician requests a completion OTP state.
- `POST /api/v1/technician/me/jobs/{id}/completion-otp/verify`: assigned technician verifies `{ otpId, otp }`.
- `GET /api/v1/service-requests/{id}/completion-otp`: authorized request participants read safe state. Customer-visible codes are only exposed to the owning customer while pending and valid.

OTP hashes are stored server-side, expire, enforce attempt limits, and must not be logged or returned to technician clients.

## Payment And Cash OTP APIs

- `GET /api/v1/technician/me/jobs/{id}/payment`: assigned technician reads the invoice/payment state for a service request.
- `POST /api/v1/payments/cash/otp/verify`: assigned technician or Admin verifies `{ paymentId, otpId, otp }` for an existing cash OTP.
- `GET /api/v1/payments/{id}/cash-otp`: authorized payment participants read safe cash OTP state.

Cash payment OTP starts from the customer/payment side for an existing invoice; the technician verifies the code shown by the customer. Successful cash OTP verification marks the payment succeeded and invoice paid. UPI/Razorpay payments remain gateway/webhook-authoritative and do not use the technician cash OTP screen.

## Service Visit Tables

- `service_visits`: scheduled job visits connected to service request and technician profile.
- `visit_change_requests`: technician requests for reschedule, additional visit, or cancellation.
- `service_visit_history`: status and assignment history for visits.

## Technician Visit APIs

- `GET /api/v1/technician/me/visits`
- `GET /api/v1/technician/me/visits/{id}`
- `PUT /api/v1/technician/me/visits/{id}/status`
- `POST /api/v1/technician/me/visits/{id}/reschedule-requests`
- `POST /api/v1/technician/me/visits/{id}/additional-visit-requests`
- `POST /api/v1/technician/me/visits/{id}/cancel`

Admin review APIs for technician visit change requests:

- `GET /api/v1/admin/visit-change-requests`
- `POST /api/v1/admin/visit-change-requests/{id}/approve`
- `POST /api/v1/admin/visit-change-requests/{id}/reject`

## Checklist And Completion Tables

- `checklist_templates`: admin-managed checklist definitions.
- `checklist_template_service_types`: maps templates to workflow service types.
- `checklist_items`: ordered template questions/items; input types are `CHECKBOX`, `TEXT`, `NUMBER`, `PHOTO_NOTE`.
- `job_checklists`: per-request checklist instance, unique by `service_request_id`.
- `checklist_responses`: technician responses to checklist items.
- `completion_otps`: customer/technician-linked OTPs used to confirm completion.

## Checklist And Completion APIs

Admin checklist template APIs:

- `GET /api/v1/admin/checklist-templates`
- `POST /api/v1/admin/checklist-templates`
- `PUT /api/v1/admin/checklist-templates/{id}`
- `POST /api/v1/admin/checklist-templates/{id}/items`
- `PUT /api/v1/admin/checklist-templates/{id}/items/{itemId}`
- `DELETE /api/v1/admin/checklist-templates/{id}/items/{itemId}`

Technician checklist APIs:

- `GET /api/v1/technician/me/jobs/{id}/checklist`
- `PUT /api/v1/technician/me/jobs/{id}/checklist/responses`

Completion OTP APIs:

- `POST /api/v1/technician/me/jobs/{id}/completion-otp/request`
- `POST /api/v1/technician/me/jobs/{id}/completion-otp/verify`
- `GET /api/v1/service-requests/{id}/completion-otp`

Checklist instances are created from an active template matching the request service type. Required checklist items must be answered before completion.

## Private Technician Attachment Tables And APIs

Table: `technician_private_attachments`, linked to `technician_profiles`.

Technician self APIs:

- `GET /api/v1/technician/me/private-attachments`
- `POST /api/v1/technician/me/private-attachments`
- `GET /api/v1/technician/me/private-attachments/{id}`
- `DELETE /api/v1/technician/me/private-attachments/{id}`

Admin technician file APIs:

- `GET /api/v1/admin/technicians/{id}/private-attachments`
- `POST /api/v1/admin/technicians/{id}/private-attachments`
- `GET /api/v1/admin/technicians/{id}/private-attachments/{attachmentId}`
- `DELETE /api/v1/admin/technicians/{id}/private-attachments/{attachmentId}`

Private attachments are profile-scoped and separate from service request attachments.

## Tracking And Geofence Tables

- `technician_latest_locations`: latest technician location for a service request; unique by `service_request_id`.
- `technician_location_history`: append-only historical location samples.
- `service_request_geofence_states`: current geofence state for a request.
- `service_request_geofence_events`: entered/exited events.
- `buildings.latitude` and `buildings.longitude`: target coordinates for geofence/routing.

## Tracking APIs

- `POST /api/v1/technician/me/jobs/{id}/location`: technician updates current coordinates for an assigned request. Payload requires valid `latitude` and `longitude`; optional fields include accuracy, speed, heading, and battery.
- `GET /api/v1/technician/me/jobs/{id}/location`: technician reads current job tracking state, including latest location, route, ETA, and geofence availability when the provider/data exists.
- `GET /api/v1/customers/me/service-requests/{id}/technician-location`: customer reads latest technician location for an owned request.

Tracking is active only for trackable in-progress request states: `ON_THE_WAY`, `REACHED_SITE`, `DIAGNOSIS`, `REPAIR_IN_PROGRESS`, `WAITING_FOR_PARTS`, and `TESTING`. It stops for `COMPLETED`, `CANCELLED`, and other non-trackable states. The default geofence radius is configured in backend properties and falls back to 150 meters when invalid or missing. The backend never fabricates route, ETA, or map coordinates when building coordinates or routing provider data are unavailable.

## Notifications And Communication

- `GET /api/v1/notifications`: current user's inbox with optional `status`, `page`, and `size`.
- `POST /api/v1/notifications`
- `PUT /api/v1/notifications/{id}/read`

Communication tables use `users.id`, not technician profile IDs:

- `communication_preferences`
- `communication_events`
- `communication_messages`
- `communication_templates`

Technician-related communication events include technician account creation, service assignment, service updates, visit updates, report notifications, and system/admin alerts.

## Technician Application Registration APIs

These routes are public and protected by the application token returned by the create step, not by a technician JWT:

- `POST /api/v1/technician-applications`
- `POST /api/v1/technician-applications/{id}/otp/send`
- `POST /api/v1/technician-applications/{id}/otp/verify`
- `PUT /api/v1/technician-applications/{id}`
- `POST /api/v1/technician-applications/{id}/documents`
- `GET /api/v1/technician-applications/{id}`
- `POST /api/v1/technician-applications/{id}/submit`

Admin review:

- `GET /api/v1/admin/technician-applications`
- `PUT /api/v1/admin/technician-applications/{id}/status`

Development technician application OTP is currently the documented temporary `1111` path until the external SMS/OTP provider is activated.

## Local Technician Demo Seed

When the `dev` profile has `DEV_SEED_SAMPLE_DATA_ENABLED=true`, startup seeds sample technician accounts with the password from `DEV_SEED_SAMPLE_PASSWORD`. The account `tech.arjun@valor.local` is seeded with a broad test set for the Technician app:

- Mixed request statuses: assigned, on the way, reached site, repair in progress, testing, completed, cancelled.
- Mixed service types and priorities: routine maintenance, inspection, breakdown, emergency.
- Multiple buildings/lifts, visits, assignments, service reports, attachments, latest locations, invoices/payments, cash OTP rows, and notifications.
- Cash OTP seed rows use the visible test code `1111` and are refreshed on startup for Arjun demo records.

This data is local development seed data only. Production and shared environments must not enable the dev seed.

## Cross-Domain Rules

- Technician identity is `users.id`; technician work ownership is `technician_profiles.id`.
- Assignments, visits, checklists, private attachments, latest location, location history, and geofence rows use technician profile IDs.
- Communication, notifications, refresh tokens, audit actors, and onboarding tokens use user IDs.
- Technician access to a service request depends on an active assignment to the technician profile.
- Admin-created technicians should use onboarding/set-password flow where possible; raw passwords must not be exposed.
- Customer completion flows cross technician and customer domains through `service_requests`, `completion_otps`, checklist completion, and customer-visible technician tracking.
