# Valor customer and technician system

This document summarizes behavior visible in the backend repository. It is prepared for business and legal review, not presented as legal advice. Missing owner decisions are listed at the end.

## Privacy Policy

### Information the system processes

Depending on the account and workflow, the backend stores or processes:

- account identity and contact details, role, password hash, login/lock state, refresh-token hashes and OTP verification state;
- customer profiles, buildings, lifts, addresses, coordinates, service requests, appointments/visits, technician assignments, service reports, checklists, attachments, feedback, and service verification state;
- technician profiles, saved/current work locations, job records, application documents, meeting/review records, and private attachments;
- customer and technician profile photos, when voluntarily uploaded through the protected profile-photo flow;
- payment, invoice, Razorpay order/payment/refund references, refund approvals, webhook event IDs, reconciliation/audit records, and AMC/renewal data;
- in-app notification, communication event/message, delivery-status, and notification-preference records.

Raw passwords, service codes, refresh tokens, and OTP values are not intended to be exposed; the backend stores hashes or protected state for these values.

### Why information is used

The system uses this information to authenticate users, authorize ownership and role access, provide lift/service/installation workflows, assign and track jobs, issue and settle invoices, process authorized refunds, maintain AMC records, send configured communications, prevent replay/duplicate actions, and preserve audit history.

### Access

Customers access their own profiles, assets, service requests, visits, payments, invoices, refunds, and supported documents. Technicians access their authenticated profiles, assigned jobs, authorized verification flows, visits, work records, and their own operational records. Admin and SuperAdmin access follows the backend's role and ownership checks, with additional approval requirements for protected financial actions. Authorized Admin profile-photo reads resolve the canonical profile before returning protected image data. No general public access to customer or technician records is documented.

Razorpay receives the provider request data required by the existing checkout/refund integration, such as amount, currency, order/payment/refund references and signed webhook interactions. SMTP receives configured recipient/from/subject/body data when enabled. The backend does not place passwords, hashes, OTPs, or gateway secrets in general notification content.

### Security controls

The repository implements password hashing, JWT/authenticated identity, role and ownership checks, hashed OTP/service-code/token storage, replay/idempotency records, database constraints, transaction validation, and audit records. These controls reduce risk but do not promise absolute security.

### Items not established by the repository

The backend does not establish a retention schedule, automatic deletion/purge policy, customer export workflow, cookie/analytics/advertising policy, international-transfer policy, or a complete statutory privacy-notice process. These require owner/legal confirmation.

## Terms & Conditions

### Accounts and access

Users must provide information required by the registration and onboarding flows and must use the account and role assigned by the system. Some staff/technician accounts require approval or onboarding steps. Credentials, OTPs, refresh tokens, and service codes must not be shared or misused.

### Services and jobs

Customers may create and manage supported service or installation requests for their own assets. Scheduling, technician assignment, visits, service-code verification, reports, checklists, completion, feedback, cancellation, and rescheduling are governed by the current workflow state and role checks. A technician may act only for an assigned/authorized request. Status changes are not accepted merely because a client supplies a desired status.

### Payments, refunds, and documents

Amounts, currency, invoices, AMC renewal quotes, and installation milestone billing are backend-authoritative. Online payment is not confirmed by checkout creation or a browser callback; verified gateway capture/webhook or authorized reconciliation is required. Refund requests require the configured Admin approval flow, and unresolved gateway outcomes remain unresolved. The original invoice and payment history are preserved. Installation milestone billing remains blocked when approved schedule terms or triggers are missing.

Cash payment, where supported, uses the existing authorized OTP verification flow. Razorpay is a third-party provider and its availability or response may affect payment processing; the backend must not treat an uncertain response as success.

### Communications and availability

The system may persist in-app notifications and communication messages. SMTP delivery is configurable and may be disabled or unavailable; a persisted message is not a guarantee of inbox delivery. No uninterrupted availability promise is made by this document.

### Effective status

This document is a technical summary pending business/legal approval. It does not create a new refund, tax, pricing, retention, warranty, salary, or service-level policy.

## Owner confirmation required

The business/legal owner must confirm the registered legal entity name, address, support/privacy contact, effective date, governing law and jurisdiction, dispute process, statutory rights language, retention/deletion/export rules, cookie/analytics/advertising practices, third-party sharing and international-transfer wording, and any customer/technician-specific contractual terms. No unverified values are supplied here.
