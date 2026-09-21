alter table service_requests drop foreign key fk_service_requests_lift;
alter table service_requests modify column lift_id bigint null;
alter table service_requests add constraint fk_service_requests_lift foreign key (lift_id) references lifts(id) on delete restrict;
alter table service_requests add constraint ck_service_requests_lift_required check (service_type = 'INSTALLATION' or lift_id is not null);

alter table completion_otps add column customer_visible_code varchar(16);
alter table completion_otps add column customer_visible_until datetime(6);
