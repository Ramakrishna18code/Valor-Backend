# Valor Customer Backend Reference

Source of truth: this document is derived only from the `Valor-Backend` Java code and Flyway migrations. The active schema is the Flyway schema under `src/main/resources/db/migration`. `schema.sql` contains older/fresh-reference table names and should not be treated as the current runtime contract.

## Customer Domain Scope

The customer backend is centered on a `users` row with role `CUSTOMER` and a one-to-one `customer_profiles` row. All customer-owned data hangs from `customer_profiles`, especially buildings, lifts, AMC contracts, service requests, payments, invoices, feedback, notifications, and communication events.

Customer ownership is server-authoritative. Customer APIs use the JWT actor and active `customer_profiles` row; clients must not send or change ownership fields for self-service flows.

## Core Backend Files

- Identity/auth: `src/main/java/com/valor/auth/AuthController.java`, `AuthComponents.java`, `CustomerProfile.java`, `CustomerProfileController.java`, `AdminCustomerController.java`
- Directory/admin lookup: `DirectoryController.java`
- Assets: `src/main/java/com/valor/assets/AssetController.java`, `AssetService.java`, `AssetDtos.java`, `Building.java`, `Lift.java`, `AmcContract.java`
- Workflow: `src/main/java/com/valor/workflow/WorkflowController.java`, `WorkflowService.java`, `ServiceVisitController.java`, `ServiceVisitService.java`
- Commerce/support/docs: `src/main/java/com/valor/commerce/CommerceController.java`, `CommerceService.java`
- Tracking: `src/main/java/com/valor/tracking/TrackingController.java`, `TrackingService.java`
- Notifications/communication: `NotificationController.java`, `CommunicationService.java`, `EmailEventService.java`

## Identity And Profile Tables

- `users`: shared identity row. Important fields: `email`, `phone`, `password_hash`, `role`, `is_active`, lockout fields, login timestamps. Customer rows use `role = CUSTOMER`.
- `customer_profiles`: one row per customer user. Fields: `user_id`, `full_name`, `alternate_phone`, `company_name`, `address`, nullable onboarding preference `has_lift`, unique `referral_code`, `rating`, `status`, `is_active`, timestamps.
- `refresh_tokens`: refresh-token rotation state for all roles.
- `otp_verifications`: customer phone OTP login/register verification state.
- `onboarding_tokens`: password setup links for admin-created customers.

Customer status values are `ACTIVE`, `INACTIVE`, `SUSPENDED`. A customer is usable only when both `users.is_active` and `customer_profiles.is_active` are true.

## Customer Auth APIs

- `POST /api/v1/auth/register`: customer self-registration. Creates `users.role = CUSTOMER` and `customer_profiles`.
- `POST /api/v1/auth/login/customer`: customer password login by identity and password.
- `POST /api/v1/auth/otp/send`, `/resend`, `/verify`: phone OTP flow for customer auth.
- `POST /api/v1/auth/refresh`: refresh-token rotation.
- `POST /api/v1/auth/logout`: revoke refresh token.
- `POST /api/v1/auth/set-password`: set password from onboarding token.
- `GET /api/v1/me`: current identity plus customer profile when actor is a customer.

Validation notes: email is normalized lowercase; phone is normalized to E.164 style; registration/admin creation require email or phone. Unknown JSON fields in write DTOs are rejected.

## Customer Profile APIs

- `GET /api/v1/customers/me`: returns the active customer profile for the JWT actor.
- `PUT /api/v1/customers/me`: updates `fullName`, `alternatePhone`, `companyName`, and `address`.

Admin customer management:

- `GET /api/v1/admin/customers`: paged customer directory with `page`, `size`, `q`, and `active`.
- `POST /api/v1/admin/customers`: create customer. Required: `fullName` and at least one of `email` or `phone`. Optional: `password`, `hasLift`, `referralCode`, `alternatePhone`, `companyName`, `address`.
- `GET /api/v1/admin/customers/{customerProfileId}`: customer detail with building/lift/request summaries.
- `PUT /api/v1/admin/customers/{customerProfileId}`: update profile fields.
- `POST /api/v1/admin/customers/{customerProfileId}/deactivate`: sets profile inactive/status inactive and disables the user.
- `POST /api/v1/admin/customers/{customerProfileId}/reactivate`: restores active customer state.

Admin customer create/update records audit events and sends account-created email when an email exists. Admin customer creation can capture the customer's "Do you have a lift?" Yes/No answer as `hasLift`; this is an onboarding preference only and does not create buildings, lifts, or service requests. Referral codes are backend-owned, persisted on the customer profile, unique, and generated when not supplied.

## Customer Asset Tables

- `buildings`: belongs to `customer_profiles` through `customer_id`. Important fields include `building_name`, type/address/city/state/pincode, latitude/longitude, emergency contact, `building_preference`, `status`, `is_active`.
- `lifts`: belongs to `buildings`. Important fields include `name`, globally unique nullable `lift_number`, `model`, `manufacturer`, `capacity`, `floor_count`, `door_type`, `serial_number`, installation/location/status/warranty/maintenance fields, `health_score`, `machine_room`, `qr_code`, `specifications`, `is_active`.
- `amc_contracts`: belongs to `lifts`, with contract plan, dates, status, reminders, and renewal count.

Lift `door_type` values are `MANUAL` or `AUTO`. Lift status values are `ACTIVE`, `DOWN`, `MAINTENANCE`, `OUT_OF_SERVICE`.

## Customer Asset APIs

Admin asset APIs:

- `GET/POST/PUT/DELETE /api/v1/buildings`
- `GET/POST/PUT/DELETE /api/v1/lifts`
- `GET/POST /api/v1/amc-contracts`
- `PUT /api/v1/amc-contracts/{id}/renew`

Customer self-service asset APIs:

- `GET /api/v1/customers/me/buildings`
- `GET /api/v1/customers/me/buildings/{id}`
- `POST /api/v1/customers/me/buildings`
- `PUT /api/v1/customers/me/buildings/{id}`
- `DELETE /api/v1/customers/me/buildings/{id}`
- `GET /api/v1/customers/me/lifts`
- `GET /api/v1/customers/me/lifts/{id}`
- `POST /api/v1/customers/me/lifts`
- `PUT /api/v1/customers/me/lifts/{id}`
- `DELETE /api/v1/customers/me/lifts/{id}`

For customer self-service building writes, the backend derives the owner from JWT and does not accept `customerProfileId`. For admin building writes, `customerProfileId` is required. For lift writes, `buildingId` and `name` are required, and the backend verifies the building is active and belongs to the correct customer when called through customer-owned routes.

## Customer Workflow Tables

- `service_requests`: belongs to `customer_profiles`; normal service requests belong to `lifts`, while `INSTALLATION` requests may be created before a lift exists.
- `technician_assignments`: assigns technicians to service requests.
- `service_reports`: technician report for a request assignment.
- `service_status_history`: status transitions with `changed_by_user_id`.
- `service_request_attachments`: request files uploaded by users.
- `service_request_feedback`: customer rating/comment after completion.
- `service_visits`: scheduled visits for a service request and technician.
- `visit_change_requests`: technician visit reschedule/additional/cancel requests reviewed by admin.
- `service_visit_history`: visit audit trail.

## Customer Workflow APIs

- `POST /api/v1/service-requests`: customer creates a request for one of their lifts. `INSTALLATION` requests may omit `liftId` when the customer is requesting a new lift. Customer submissions cannot include `customerProfileId`, internal admin notes, or estimated completion minutes.
- `GET /api/v1/customers/me/service-requests`: customer list of their own requests.
- `GET /api/v1/service-requests/{id}`: customer may read only owned requests.
- `POST /api/v1/service-requests/{id}/status`: customers have limited transitions, for example cancellation of eligible own requests.
- `GET /api/v1/customers/me/visits`: customer visits connected to their requests.
- `GET /api/v1/service-requests/{id}/attachments`
- `POST /api/v1/service-requests/{id}/attachments`
- `GET /api/v1/service-requests/{id}/attachments/{attachmentId}`
- `DELETE /api/v1/service-requests/{id}/attachments/{attachmentId}`
- `GET /api/v1/service-requests/{id}/feedback`
- `PUT /api/v1/service-requests/{id}/feedback`: rating is required from 1 to 5 and feedback is available after completion.

Request creation validates that the selected lift belongs to the chosen customer. Non-installation requests require an active owned lift. `INSTALLATION` requests may omit `liftId`, remain owned by the customer profile, and follow the same assignment/status/report workflow. Admin creation requires an explicit active `customerProfileId`; customer creation gets the customer from the JWT actor.

There is no implemented distinct "booking for friend" service-request model. The confirmed no-lift customer flow is an owned `INSTALLATION` request without an existing lift.

## Customer Tracking APIs

- `GET /api/v1/customers/me/service-requests/{id}/technician-location`: returns latest technician coordinates, staleness, route/ETA data, and geofence state for an owned request.

Related tables are `technician_latest_locations`, `technician_location_history`, `service_request_geofence_states`, and `service_request_geofence_events`.

## Customer Commerce And Support APIs

- Payments: `POST /api/v1/payments`, `GET /api/v1/payments/{id}`, `POST /api/v1/payments/{id}/status`, `POST /api/v1/payments/razorpay/checkout`, `POST /api/v1/payments/{id}/refunds`
- Invoices: `POST /api/v1/invoices`, `GET /api/v1/invoices/{id}`, `POST /api/v1/invoices/{id}/status`
- Support tickets: `POST /api/v1/support-tickets`, `GET /api/v1/support-tickets/{id}`, `POST /api/v1/support-tickets/{id}/status`
- AMC renewal requests: `GET /api/v1/amc-renewal-requests`, `POST /api/v1/customers/me/amc-contracts/{id}/renewal-requests`, `POST /api/v1/amc-renewal-requests/{id}/quote`, `POST /api/v1/amc-renewal-requests/{id}/status`
- Asset documents: `GET/POST /api/v1/customers/me/buildings/{id}/documents`, `GET/POST /api/v1/customers/me/lifts/{id}/documents`
- Reports: `GET /api/v1/service-requests/{id}/report.pdf`

Commerce tables connected to customers include `invoices`, `payment_records`, `payment_refunds`, `support_tickets`, `amc_renewal_requests`, and `asset_documents`.

## Notifications And Communication

- `GET /api/v1/notifications`
- `POST /api/v1/notifications`
- `PUT /api/v1/notifications/{id}/read`

Notifications and communications target `users`, not profile IDs. Related tables are `notifications`, `communication_preferences`, `communication_events`, `communication_messages`, and `communication_templates`.

Customer-related email/event flows include account creation, service request creation/status changes, technician assignment, payment/invoice events, AMC renewal, feedback, and support events. Communication preferences control optional channels, while critical/mandatory events may bypass preferences.

## Cross-Domain Rules

- Customer identity is `users.id`; customer domain ownership is `customer_profiles.id`.
- Buildings point to customer profiles; lifts point to buildings; AMC contracts point to lifts.
- Service requests point to customer profiles. Non-installation requests also point to lifts, and the backend enforces that the lift belongs to the customer. Installation requests may be created without an existing lift.
- Payments, invoices, support tickets, and AMC renewal requests carry customer profile links and may also link to service requests, AMC contracts, invoices, or payment records.
- Audit logs use actor user IDs and entity metadata; admin customer changes are audited.
- Never expose or depend on raw passwords. Admin-created customers should use onboarding/set-password flow where possible.
