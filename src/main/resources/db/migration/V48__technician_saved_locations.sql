create table technician_saved_locations (
    id bigint auto_increment primary key,
    technician_profile_id bigint not null,
    title varchar(120) not null,
    pincode varchar(6) not null,
    state varchar(120),
    district varchar(120),
    address varchar(500),
    latitude decimal(9,6) not null,
    longitude decimal(9,6) not null,
    is_default boolean not null default false,
    created_at datetime(6) not null default current_timestamp(6),
    updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint fk_technician_saved_locations_profile foreign key (technician_profile_id) references technician_profiles(id) on delete restrict,
    constraint ck_technician_saved_locations_pincode check (pincode regexp '^[0-9]{6}$'),
    constraint ck_technician_saved_locations_latitude check (latitude between -90 and 90),
    constraint ck_technician_saved_locations_longitude check (longitude between -180 and 180)
);
create index idx_technician_saved_locations_profile on technician_saved_locations(technician_profile_id, is_default, updated_at);
