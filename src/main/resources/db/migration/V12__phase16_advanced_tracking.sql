alter table buildings add column latitude decimal(9,6);
alter table buildings add column longitude decimal(9,6);
alter table buildings add constraint ck_buildings_latitude check (latitude is null or (latitude >= -90 and latitude <= 90));
alter table buildings add constraint ck_buildings_longitude check (longitude is null or (longitude >= -180 and longitude <= 180));

create table technician_location_history (
  id bigint primary key auto_increment,
  service_request_id bigint not null,
  technician_id bigint not null,
  latitude decimal(9,6) not null,
  longitude decimal(9,6) not null,
  recorded_at datetime not null,
  created_at datetime not null default current_timestamp,
  constraint fk_location_history_request foreign key (service_request_id) references service_requests(id) on delete restrict,
  constraint fk_location_history_technician foreign key (technician_id) references technician_profiles(id) on delete restrict,
  constraint ck_location_history_lat check (latitude >= -90 and latitude <= 90),
  constraint ck_location_history_lng check (longitude >= -180 and longitude <= 180)
);
create index idx_location_history_request_time on technician_location_history(service_request_id, recorded_at, id);
create index idx_location_history_technician_time on technician_location_history(technician_id, recorded_at, id);

create table service_request_geofence_states (
  id bigint primary key auto_increment,
  service_request_id bigint not null,
  technician_id bigint not null,
  state varchar(20) not null,
  distance_meters decimal(10,2),
  radius_meters int not null,
  evaluated_at datetime not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  constraint uk_geofence_state_request unique (service_request_id),
  constraint fk_geofence_state_request foreign key (service_request_id) references service_requests(id) on delete restrict,
  constraint fk_geofence_state_technician foreign key (technician_id) references technician_profiles(id) on delete restrict,
  constraint ck_geofence_state_value check (state in ('UNAVAILABLE','OUTSIDE','INSIDE')),
  constraint ck_geofence_state_radius check (radius_meters > 0)
);
create index idx_geofence_state_technician on service_request_geofence_states(technician_id, evaluated_at);

create table service_request_geofence_events (
  id bigint primary key auto_increment,
  service_request_id bigint not null,
  technician_id bigint not null,
  event_type varchar(20) not null,
  distance_meters decimal(10,2),
  radius_meters int not null,
  occurred_at datetime not null,
  constraint fk_geofence_event_request foreign key (service_request_id) references service_requests(id) on delete restrict,
  constraint fk_geofence_event_technician foreign key (technician_id) references technician_profiles(id) on delete restrict,
  constraint ck_geofence_event_type check (event_type in ('ENTERED','EXITED')),
  constraint ck_geofence_event_radius check (radius_meters > 0)
);
create index idx_geofence_event_request_time on service_request_geofence_events(service_request_id, occurred_at, id);
