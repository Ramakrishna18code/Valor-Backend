alter table communication_messages add column html_body text;
alter table communication_messages add column from_address varchar(254);
alter table communication_messages add column from_name varchar(120);
alter table communication_messages add column reply_to varchar(254);

create table onboarding_tokens (
  id bigint primary key auto_increment,
  user_id bigint not null,
  token_hash varchar(64) not null,
  expires_at datetime not null,
  used_at datetime,
  created_at datetime not null default current_timestamp,
  constraint uk_onboarding_token_hash unique (token_hash),
  constraint fk_onboarding_token_user foreign key (user_id) references users(id) on delete restrict
);
create index idx_onboarding_token_user_created on onboarding_tokens(user_id, created_at, id);
create index idx_onboarding_token_expiry on onboarding_tokens(expires_at, used_at);

insert into communication_templates(event_type, channel, template_key, subject, body, variables)
values
('CUSTOMER_ACCOUNT_CREATED','EMAIL','customer-account-created-email','Welcome to Valor','Hello {{name}},\n\nYour Valor account has been created. Use the secure set-password link to finish onboarding:\n{{setPasswordUrl}}\n\nThis link expires and no password is included in this email.','name,setPasswordUrl'),
('CUSTOMER_SET_PASSWORD','EMAIL','customer-set-password-email','Set your Valor password','Hello {{name}},\n\nSet your Valor password securely here:\n{{setPasswordUrl}}\n\nIf you did not expect this message, contact Valor support.','name,setPasswordUrl'),
('SERVICE_REQUEST_CREATED','EMAIL','service-request-created-email','Service request {{serviceId}} created','Hello {{name}},\n\nWe received your service request {{serviceId}} for {{title}}. Current status: {{status}}.','name,serviceId,title,status'),
('SERVICE_REQUEST_STATUS_CHANGED','EMAIL','service-request-status-email','Service request {{serviceId}} is {{status}}','Hello {{name}},\n\nYour service request {{serviceId}} status changed from {{fromStatus}} to {{status}}.','name,serviceId,fromStatus,status'),
('APPOINTMENT_SCHEDULED','EMAIL','appointment-scheduled-email','Appointment scheduled for {{serviceId}}','Hello {{name}},\n\nYour appointment for {{serviceId}} is scheduled on {{date}} from {{startTime}} to {{endTime}}.','name,serviceId,date,startTime,endTime'),
('APPOINTMENT_CHANGED','EMAIL','appointment-changed-email','Appointment updated for {{serviceId}}','Hello {{name}},\n\nYour appointment for {{serviceId}} was updated to {{date}} from {{startTime}} to {{endTime}}.','name,serviceId,date,startTime,endTime'),
('APPOINTMENT_CANCELLED','EMAIL','appointment-cancelled-email','Appointment cancelled for {{serviceId}}','Hello {{name}},\n\nYour appointment for {{serviceId}} was cancelled. Reason: {{reason}}','name,serviceId,reason'),
('AMC_RENEWAL_REQUEST','EMAIL','amc-renewal-request-email','AMC renewal request received','Hello {{name}},\n\nWe received your AMC renewal request for contract {{contractId}}.','name,contractId'),
('INVOICE_CREATED','EMAIL','invoice-created-email','Invoice {{invoiceNumber}} created','Hello {{name}},\n\nInvoice {{invoiceNumber}} for {{amount}} {{currency}} has been created.','name,invoiceNumber,amount,currency'),
('PAYMENT_RESULT','EMAIL','payment-result-email','Payment {{status}}','Hello {{name}},\n\nYour payment {{paymentId}} status is {{status}}.','name,paymentId,status'),
('SERVICE_COMPLETED','EMAIL','service-completed-email','Service {{serviceId}} completed','Hello {{name}},\n\nService request {{serviceId}} has been completed.','name,serviceId'),
('FEEDBACK_REQUEST','EMAIL','feedback-request-email','Share feedback for {{serviceId}}','Hello {{name}},\n\nPlease share feedback for completed service {{serviceId}}.','name,serviceId'),
('TECHNICIAN_ACCOUNT_CREATED','EMAIL','technician-account-created-email','Valor technician account created','Hello {{name}},\n\nYour technician account has been created. Use this secure set-password link:\n{{setPasswordUrl}}','name,setPasswordUrl'),
('TECHNICIAN_ASSIGNMENT','EMAIL','technician-assignment-email','New assignment {{serviceId}}','Hello {{name}},\n\nYou have been assigned to service request {{serviceId}}: {{title}}.','name,serviceId,title'),
('TECHNICIAN_JOB_STATUS_CHANGED','EMAIL','technician-job-status-email','Job {{serviceId}} is {{status}}','Hello {{name}},\n\nJob {{serviceId}} status changed from {{fromStatus}} to {{status}}.','name,serviceId,fromStatus,status'),
('TECHNICIAN_VISIT_SCHEDULED','EMAIL','technician-visit-scheduled-email','Visit scheduled for {{serviceId}}','Hello {{name}},\n\nVisit {{visitId}} for {{serviceId}} is scheduled on {{date}} from {{startTime}} to {{endTime}}.','name,visitId,serviceId,date,startTime,endTime'),
('TECHNICIAN_VISIT_RESCHEDULED','EMAIL','technician-visit-rescheduled-email','Visit rescheduled for {{serviceId}}','Hello {{name}},\n\nVisit {{visitId}} for {{serviceId}} was rescheduled to {{date}} from {{startTime}} to {{endTime}}.','name,visitId,serviceId,date,startTime,endTime'),
('TECHNICIAN_CHANGE_REQUEST_DECISION','EMAIL','technician-change-request-decision-email','Visit change request {{status}}','Hello {{name}},\n\nYour {{type}} request {{changeRequestId}} was {{status}}.','name,type,changeRequestId,status'),
('ADMIN_SYSTEM_ALERT','EMAIL','admin-system-alert-email','Valor alert: {{title}}','Admin alert:\n\n{{message}}','title,message'),
('ADMIN_REPORT_READY','EMAIL','admin-report-ready-email','Report {{reportType}} ready','Report {{reportType}} is ready for review.','reportType'),
('ADMIN_CRITICAL_SERVICE_EVENT','EMAIL','admin-critical-service-event-email','Critical service event {{serviceId}}','Service {{serviceId}} requires attention. Status: {{status}}.','serviceId,status'),
('ADMIN_PAYMENT_ALERT','EMAIL','admin-payment-alert-email','Payment alert {{paymentId}}','Payment {{paymentId}} status is {{status}}.','paymentId,status')
on duplicate key update subject = values(subject), body = values(body), variables = values(variables), active = true;
