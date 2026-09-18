create table communication_templates (
  id bigint primary key auto_increment,
  event_type varchar(80) not null,
  channel varchar(20) not null,
  template_key varchar(120) not null,
  subject varchar(200),
  body text not null,
  variables varchar(1000),
  active boolean not null default true,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  constraint uk_comm_template unique (event_type, channel, template_key),
  constraint ck_comm_template_channel check (channel in ('EMAIL','SMS','WHATSAPP','IN_APP'))
);
create index idx_comm_template_event_channel on communication_templates(event_type, channel, active);

create table communication_preferences (
  id bigint primary key auto_increment,
  user_id bigint not null,
  email_enabled boolean not null default true,
  sms_enabled boolean not null default true,
  whatsapp_enabled boolean not null default true,
  in_app_enabled boolean not null default true,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  constraint uk_comm_preferences_user unique (user_id),
  constraint fk_comm_preferences_user foreign key (user_id) references users(id) on delete restrict
);

create table communication_events (
  id bigint primary key auto_increment,
  event_type varchar(80) not null,
  recipient_user_id bigint not null,
  idempotency_key varchar(160) not null,
  payload text,
  status varchar(20) not null default 'PENDING',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  constraint uk_comm_event_idempotency unique (idempotency_key),
  constraint fk_comm_event_recipient foreign key (recipient_user_id) references users(id) on delete restrict,
  constraint ck_comm_event_status check (status in ('PENDING','PROCESSING','SENT','DELIVERED','FAILED','CANCELLED'))
);
create index idx_comm_event_recipient_created on communication_events(recipient_user_id, created_at, id);
create index idx_comm_event_status_retry on communication_events(status, updated_at);

create table communication_messages (
  id bigint primary key auto_increment,
  event_id bigint not null,
  recipient_user_id bigint not null,
  channel varchar(20) not null,
  template_id bigint,
  template_key varchar(120),
  provider varchar(80),
  provider_message_id varchar(160),
  recipient_masked varchar(160),
  subject varchar(200),
  body text,
  status varchar(20) not null default 'PENDING',
  retry_count int not null default 0,
  max_retry_count int not null default 3,
  next_retry_at datetime,
  failure_reason varchar(1000),
  sent_at datetime,
  delivered_at datetime,
  failed_at datetime,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  constraint fk_comm_message_event foreign key (event_id) references communication_events(id) on delete restrict,
  constraint fk_comm_message_recipient foreign key (recipient_user_id) references users(id) on delete restrict,
  constraint fk_comm_message_template foreign key (template_id) references communication_templates(id) on delete restrict,
  constraint uk_comm_message_channel unique (event_id, channel),
  constraint ck_comm_message_channel check (channel in ('EMAIL','SMS','WHATSAPP','IN_APP')),
  constraint ck_comm_message_status check (status in ('PENDING','PROCESSING','SENT','DELIVERED','FAILED','CANCELLED'))
);
create index idx_comm_message_status_retry on communication_messages(status, next_retry_at, id);
create index idx_comm_message_recipient_created on communication_messages(recipient_user_id, created_at, id);
