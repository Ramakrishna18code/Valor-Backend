insert into communication_templates(event_type, channel, template_key, subject, body, variables)
values
('AMC_CREATED','EMAIL','amc-created-email','AMC {{amcNumber}} created','Hello {{name}},\n\nAMC contract {{amcNumber}} has been created.','name,amcNumber,contractId'),
('AMC_CREATED','SMS','amc-created-sms','AMC created','Valor AMC {{amcNumber}} has been created.','amcNumber,contractId'),
('AMC_CREATED','WHATSAPP','amc-created-whatsapp','AMC created','Valor AMC {{amcNumber}} has been created.','amcNumber,contractId'),
('ADMIN_CRITICAL_SERVICE_EVENT','EMAIL','admin-critical-service-created-email','Critical service event {{serviceId}}','Service {{serviceId}} requires attention. Status: {{status}}.','serviceId,status'),
('ADMIN_CRITICAL_SERVICE_EVENT','SMS','admin-critical-service-created-sms','Critical service event','Service {{serviceId}} status {{status}}.','serviceId,status'),
('ADMIN_CRITICAL_SERVICE_EVENT','WHATSAPP','admin-critical-service-created-whatsapp','Critical service event','Service {{serviceId}} status {{status}}.','serviceId,status'),
('ADMIN_REPORT_READY','SMS','admin-report-ready-sms','Report ready','Valor report {{reportType}} is ready.','reportType'),
('ADMIN_REPORT_READY','WHATSAPP','admin-report-ready-whatsapp','Report ready','Valor report {{reportType}} is ready.','reportType')
on duplicate key update subject = values(subject), body = values(body), variables = values(variables), active = true;
