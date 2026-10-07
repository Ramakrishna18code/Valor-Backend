create table if not exists lift_service_codes (
    id bigint auto_increment primary key,
    lift_id bigint not null unique references lifts(id),
    customer_profile_id bigint not null references customer_profiles(id),
    code_hash varchar(255) not null,
    code_ciphertext varchar(512) not null,
    updated_at datetime(6) not null
);

create table if not exists service_code_verifications (
    id bigint auto_increment primary key,
    service_request_id bigint not null references service_requests(id),
    lift_service_code_id bigint not null references lift_service_codes(id),
    technician_id bigint not null references technician_profiles(id),
    action varchar(16) not null check (action in ('START', 'COMPLETE')),
    attempts smallint not null default 0,
    locked_until datetime(6),
    verified_at datetime(6),
    created_at datetime(6) not null,
    unique(service_request_id, technician_id, action)
);
create index idx_service_code_verifications_request on service_code_verifications(service_request_id, action);
