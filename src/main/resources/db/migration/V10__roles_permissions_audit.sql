create table roles (
  id bigint primary key auto_increment,
  name varchar(40) not null unique,
  description varchar(255),
  enabled boolean not null default true,
  system_role boolean not null default true,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp
);

create table permissions (
  id bigint primary key auto_increment,
  code varchar(80) not null unique,
  description varchar(255) not null,
  category varchar(60) not null,
  created_at timestamp not null default current_timestamp
);

create table role_permissions (
  role_id bigint not null,
  permission_id bigint not null,
  created_at timestamp not null default current_timestamp,
  primary key (role_id, permission_id),
  constraint fk_role_permissions_role foreign key (role_id) references roles(id) on delete cascade,
  constraint fk_role_permissions_permission foreign key (permission_id) references permissions(id) on delete cascade
);

create index idx_role_permissions_permission on role_permissions(permission_id);

create table audit_logs (
  id bigint primary key auto_increment,
  actor_user_id bigint,
  actor_role varchar(40),
  action varchar(100) not null,
  entity_type varchar(80) not null,
  entity_id varchar(80),
  result_status varchar(40) not null,
  summary varchar(1000),
  before_summary varchar(1000),
  after_summary varchar(1000),
  created_at timestamp not null default current_timestamp,
  constraint fk_audit_actor_user foreign key (actor_user_id) references users(id) on delete set null
);

create index idx_audit_created_at on audit_logs(created_at);
create index idx_audit_actor on audit_logs(actor_user_id, created_at);
create index idx_audit_entity on audit_logs(entity_type, entity_id);
create index idx_audit_action on audit_logs(action, created_at);

insert into roles(name, description, enabled, system_role) values
('SUPER_ADMIN', 'Full system administration role', true, true),
('ADMIN', 'Operations administration role controlled by permissions', true, true),
('CUSTOMER', 'Customer application role', true, true),
('TECHNICIAN', 'Technician application role', true, true);

insert into permissions(code, description, category) values
('CUSTOMER_READ', 'Read customer records', 'Customers'),
('CUSTOMER_CREATE', 'Create customer records', 'Customers'),
('CUSTOMER_UPDATE', 'Update customer records', 'Customers'),
('CUSTOMER_DEACTIVATE', 'Deactivate or reactivate customers', 'Customers'),
('BUILDING_READ', 'Read buildings', 'Assets'),
('BUILDING_WRITE', 'Create or update buildings', 'Assets'),
('LIFT_READ', 'Read lifts', 'Assets'),
('LIFT_WRITE', 'Create or update lifts', 'Assets'),
('AMC_READ', 'Read AMC contracts and renewals', 'AMC'),
('AMC_WRITE', 'Create or update AMC contracts and renewals', 'AMC'),
('SERVICE_REQUEST_READ', 'Read service requests', 'Service'),
('SERVICE_REQUEST_ASSIGN', 'Assign technicians to service requests', 'Service'),
('SERVICE_REQUEST_UPDATE', 'Update service request lifecycle', 'Service'),
('SERVICE_VISIT_READ', 'Read service visits and change requests', 'Scheduling'),
('SERVICE_VISIT_WRITE', 'Create, update, cancel, approve or reject visits/change requests', 'Scheduling'),
('PAYMENT_READ', 'Read payments and invoices', 'Finance'),
('PAYMENT_REFUND', 'Request payment refunds', 'Finance'),
('TRANSACTION_READ', 'Read transaction records', 'Finance'),
('REPORT_READ', 'Read admin reports', 'Reports'),
('REPORT_EXPORT', 'Export admin reports and transactions', 'Reports'),
('NOTIFICATION_READ', 'Read notifications', 'Notifications'),
('NOTIFICATION_WRITE', 'Create or update notifications', 'Notifications'),
('SETTINGS_READ', 'Read admin settings', 'Settings'),
('SETTINGS_WRITE', 'Update admin settings', 'Settings'),
('ROLE_READ', 'Read roles and permissions', 'Security'),
('ROLE_WRITE', 'Change role permissions', 'Security'),
('AUDIT_READ', 'Read audit logs', 'Security');

insert into role_permissions(role_id, permission_id)
select r.id, p.id from roles r join permissions p
where r.name = 'SUPER_ADMIN';

insert into role_permissions(role_id, permission_id)
select r.id, p.id from roles r join permissions p
where r.name = 'ADMIN'
  and p.code in (
    'CUSTOMER_READ','CUSTOMER_CREATE','CUSTOMER_UPDATE','CUSTOMER_DEACTIVATE',
    'BUILDING_READ','BUILDING_WRITE','LIFT_READ','LIFT_WRITE',
    'AMC_READ','AMC_WRITE',
    'SERVICE_REQUEST_READ','SERVICE_REQUEST_ASSIGN','SERVICE_REQUEST_UPDATE',
    'SERVICE_VISIT_READ','SERVICE_VISIT_WRITE',
    'PAYMENT_READ','PAYMENT_REFUND','TRANSACTION_READ',
    'REPORT_READ','REPORT_EXPORT',
    'NOTIFICATION_READ','NOTIFICATION_WRITE',
    'SETTINGS_READ','SETTINGS_WRITE',
    'ROLE_READ'
  );
