create table technician_applications (
  id bigint primary key auto_increment,
  application_token_hash varchar(64) not null unique,
  full_name varchar(160) not null,
  email varchar(254) not null,
  phone varchar(20) not null,
  password_hash varchar(255) not null,
  experience varchar(80),
  specialization varchar(160),
  lift_brands varchar(500),
  certifications varchar(500),
  highest_qualification varchar(160),
  hands_on_experience varchar(160),
  preferred_locations varchar(500),
  willing_to_work_at_heights boolean,
  travel_availability varchar(80),
  additional_notes varchar(1000),
  status varchar(30) not null default 'DRAFT',
  otp_hash varchar(255),
  otp_expires_at datetime(6),
  otp_attempts_remaining int unsigned not null default 3,
  otp_verified_at datetime(6),
  reviewed_at datetime(6),
  reviewed_by_user_id bigint,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint ck_technician_application_status check (status in ('DRAFT','OTP_VERIFIED','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED')),
  constraint fk_technician_application_reviewer foreign key (reviewed_by_user_id) references users(id) on delete set null
);
create index idx_technician_applications_status on technician_applications(status, created_at);
create index idx_technician_applications_email on technician_applications(email);

create table technician_application_documents (
  id bigint primary key auto_increment,
  application_id bigint not null,
  document_type varchar(50) not null,
  original_filename varchar(255) not null,
  content_type varchar(120) not null,
  file_size bigint not null,
  storage_key varchar(500) not null unique,
  created_at datetime(6) not null,
  constraint uk_technician_application_document_type unique(application_id, document_type),
  constraint fk_technician_application_document foreign key (application_id) references technician_applications(id) on delete cascade
);
create index idx_technician_application_documents_application on technician_application_documents(application_id, created_at);
