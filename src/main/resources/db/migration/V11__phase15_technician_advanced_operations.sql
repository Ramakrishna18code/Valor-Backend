alter table technician_profiles add column profile_photo_url varchar(500);
alter table technician_profiles add column date_of_birth date;
alter table technician_profiles add column gender varchar(40);
alter table technician_profiles add column address varchar(500);
alter table technician_profiles add column emergency_contact_name varchar(160);
alter table technician_profiles add column emergency_contact_phone varchar(20);

create table checklist_templates (
  id bigint primary key auto_increment,
  name varchar(160) not null,
  description varchar(1000),
  active boolean not null default true,
  version int not null default 1,
  created_at datetime(6) not null,
  updated_at datetime(6) not null
);

create table checklist_template_service_types (
  template_id bigint not null,
  service_type varchar(40) not null,
  primary key (template_id, service_type),
  constraint fk_checklist_template_type_template foreign key (template_id) references checklist_templates(id)
);

create table checklist_items (
  id bigint primary key auto_increment,
  template_id bigint not null,
  label varchar(255) not null,
  description varchar(1000),
  required boolean not null default true,
  sort_order int not null,
  input_type varchar(30) not null default 'CHECKBOX',
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint fk_checklist_item_template foreign key (template_id) references checklist_templates(id),
  constraint chk_checklist_item_input_type check (input_type in ('CHECKBOX','TEXT','NUMBER','PHOTO_NOTE'))
);

create index idx_checklist_items_template_sort on checklist_items(template_id, sort_order, id);

create table job_checklists (
  id bigint primary key auto_increment,
  service_request_id bigint not null,
  service_visit_id bigint,
  template_id bigint not null,
  template_version int not null,
  status varchar(30) not null default 'IN_PROGRESS',
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_job_checklist_request unique (service_request_id),
  constraint fk_job_checklist_request foreign key (service_request_id) references service_requests(id),
  constraint fk_job_checklist_visit foreign key (service_visit_id) references service_visits(id),
  constraint fk_job_checklist_template foreign key (template_id) references checklist_templates(id),
  constraint chk_job_checklist_status check (status in ('IN_PROGRESS','COMPLETED'))
);

create table checklist_responses (
  id bigint primary key auto_increment,
  job_checklist_id bigint not null,
  item_id bigint not null,
  technician_profile_id bigint not null,
  checked boolean,
  value_text varchar(2000),
  responded_at datetime(6) not null,
  constraint uk_checklist_response_item unique (job_checklist_id, item_id),
  constraint fk_checklist_response_job foreign key (job_checklist_id) references job_checklists(id),
  constraint fk_checklist_response_item foreign key (item_id) references checklist_items(id),
  constraint fk_checklist_response_tech foreign key (technician_profile_id) references technician_profiles(id)
);

create table completion_otps (
  id bigint primary key auto_increment,
  service_request_id bigint not null,
  customer_profile_id bigint not null,
  requested_by_technician_id bigint not null,
  otp_hash varchar(255) not null,
  attempts_remaining smallint not null default 3,
  max_attempts smallint not null default 3,
  expires_at datetime(6) not null,
  locked_until datetime(6),
  verified_at datetime(6),
  created_at datetime(6) not null,
  constraint fk_completion_otp_request foreign key (service_request_id) references service_requests(id),
  constraint fk_completion_otp_customer foreign key (customer_profile_id) references customer_profiles(id),
  constraint fk_completion_otp_tech foreign key (requested_by_technician_id) references technician_profiles(id)
);

create index idx_completion_otp_request_created on completion_otps(service_request_id, created_at);

create table technician_private_attachments (
  id bigint primary key auto_increment,
  technician_profile_id bigint not null,
  uploaded_by_user_id bigint not null,
  original_filename varchar(255) not null,
  content_type varchar(100) not null,
  file_size bigint not null,
  storage_key varchar(500) not null unique,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint fk_private_attachment_technician foreign key (technician_profile_id) references technician_profiles(id),
  constraint fk_private_attachment_uploader foreign key (uploaded_by_user_id) references users(id)
);

create index idx_private_attachment_technician on technician_private_attachments(technician_profile_id, created_at, id);

insert into permissions(code, description, category) values
('CHECKLIST_READ', 'Read checklist templates and job checklists', 'Service'),
('CHECKLIST_WRITE', 'Manage checklist templates and items', 'Service'),
('TECHNICIAN_PROFILE_WRITE', 'Update expanded technician profile fields', 'Staff');

insert into role_permissions(role_id, permission_id)
select r.id, p.id from roles r join permissions p
where r.name = 'SUPER_ADMIN' and p.code in ('CHECKLIST_READ','CHECKLIST_WRITE','TECHNICIAN_PROFILE_WRITE');

insert into role_permissions(role_id, permission_id)
select r.id, p.id from roles r join permissions p
where r.name = 'ADMIN' and p.code in ('CHECKLIST_READ','CHECKLIST_WRITE','TECHNICIAN_PROFILE_WRITE');
