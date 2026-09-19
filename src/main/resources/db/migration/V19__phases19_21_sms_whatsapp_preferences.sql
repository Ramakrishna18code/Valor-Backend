alter table otp_verifications add column provider varchar(80);
alter table otp_verifications add column provider_reference varchar(160);
alter table otp_verifications add column last_sent_at datetime;
alter table otp_verifications add column resend_count int not null default 0;

alter table communication_preferences add column otp_sms_enabled boolean not null default true;
alter table communication_preferences add column otp_whatsapp_enabled boolean not null default false;
alter table communication_preferences add column service_notifications_enabled boolean not null default true;
alter table communication_preferences add column billing_notifications_enabled boolean not null default true;
alter table communication_preferences add column appointment_notifications_enabled boolean not null default true;
alter table communication_preferences add column job_notifications_enabled boolean not null default true;
alter table communication_preferences add column visit_notifications_enabled boolean not null default true;
alter table communication_preferences add column system_notifications_enabled boolean not null default true;
alter table communication_preferences add column critical_alerts_enabled boolean not null default true;
alter table communication_preferences add column report_notifications_enabled boolean not null default true;

insert into communication_templates(event_type, channel, template_key, subject, body, variables)
values
('OTP_REQUESTED','SMS','otp-requested-msg91-sms','OTP requested','Your Valor OTP is {{otp}}. It expires in {{expirySeconds}} seconds.','otp,expirySeconds'),
('OTP_REQUESTED','WHATSAPP','otp-requested-whatsapp','OTP requested','Your Valor OTP is {{otp}}. It expires in {{expirySeconds}} seconds.','otp,expirySeconds'),
('SERVICE_REQUEST_STATUS_CHANGED','SMS','service-request-status-sms','Service request update','Valor request {{serviceId}} is now {{status}}.','serviceId,status'),
('SERVICE_REQUEST_STATUS_CHANGED','WHATSAPP','service-request-status-whatsapp','Service request update','Valor request {{serviceId}} is now {{status}}.','serviceId,status'),
('APPOINTMENT_CHANGED','SMS','appointment-changed-sms','Appointment updated','Valor appointment for {{serviceId}} is {{date}} {{startTime}}-{{endTime}}.','serviceId,date,startTime,endTime'),
('APPOINTMENT_CHANGED','WHATSAPP','appointment-changed-whatsapp','Appointment updated','Valor appointment for {{serviceId}} is {{date}} {{startTime}}-{{endTime}}.','serviceId,date,startTime,endTime'),
('TECHNICIAN_ASSIGNMENT','SMS','technician-assignment-sms','New assignment','Valor assignment {{serviceId}}: {{title}}.','serviceId,title'),
('TECHNICIAN_ASSIGNMENT','WHATSAPP','technician-assignment-whatsapp','New assignment','Valor assignment {{serviceId}}: {{title}}.','serviceId,title'),
('AMC_RENEWAL_REQUEST','SMS','amc-renewal-sms','AMC renewal','Valor AMC renewal request received for contract {{contractId}}.','contractId'),
('AMC_RENEWAL_REQUEST','WHATSAPP','amc-renewal-whatsapp','AMC renewal','Valor AMC renewal request received for contract {{contractId}}.','contractId'),
('INVOICE_CREATED','SMS','invoice-created-sms','Invoice created','Valor invoice {{invoiceNumber}} for {{amount}} {{currency}} has been created.','invoiceNumber,amount,currency'),
('INVOICE_CREATED','WHATSAPP','invoice-created-whatsapp','Invoice created','Valor invoice {{invoiceNumber}} for {{amount}} {{currency}} has been created.','invoiceNumber,amount,currency'),
('PAYMENT_RESULT','SMS','payment-result-sms','Payment result','Valor payment {{paymentId}} status: {{status}}.','paymentId,status'),
('PAYMENT_RESULT','WHATSAPP','payment-result-whatsapp','Payment result','Valor payment {{paymentId}} status: {{status}}.','paymentId,status'),
('SERVICE_COMPLETED','SMS','service-completed-sms','Service completed','Valor service {{serviceId}} has been completed.','serviceId'),
('SERVICE_COMPLETED','WHATSAPP','service-completed-whatsapp','Service completed','Valor service {{serviceId}} has been completed.','serviceId'),
('FEEDBACK_REQUEST','SMS','feedback-request-sms','Feedback requested','Please share feedback for Valor service {{serviceId}}.','serviceId'),
('FEEDBACK_REQUEST','WHATSAPP','feedback-request-whatsapp','Feedback requested','Please share feedback for Valor service {{serviceId}}.','serviceId')
on duplicate key update subject = values(subject), body = values(body), variables = values(variables), active = true;
