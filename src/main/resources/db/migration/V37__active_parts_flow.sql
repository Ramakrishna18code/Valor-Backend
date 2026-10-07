create table part_categories (
 id bigint auto_increment primary key,
 name varchar(120) not null,
 description varchar(500),
 is_active boolean not null default true,
 created_at datetime(6) not null,
 updated_at datetime(6) not null,
 constraint uk_part_category_name unique(name)
);
create table part_items (
 id bigint auto_increment primary key,
 category_id bigint not null,
 name varchar(180) not null,
 sku varchar(80) not null,
 description varchar(1000),
 unit varchar(40) not null,
 quantity_on_hand int not null default 0,
 reorder_threshold int not null default 0,
 is_active boolean not null default true,
 compatibility_metadata varchar(500),
 version bigint not null default 0,
 created_at datetime(6) not null,
 updated_at datetime(6) not null,
 constraint uk_part_item_sku unique(sku),
 constraint ck_part_item_stock_nonnegative check(quantity_on_hand >= 0),
 constraint ck_part_item_threshold_nonnegative check(reorder_threshold >= 0),
 constraint fk_part_item_category foreign key(category_id) references part_categories(id)
);
create index idx_part_items_category_active on part_items(category_id,is_active);
create table service_request_part_requests (
 id bigint auto_increment primary key,
 service_request_id bigint not null,
 technician_profile_id bigint not null,
 status varchar(20) not null,
 notes varchar(1000),
 created_at datetime(6) not null,
 submitted_at datetime(6), approved_at datetime(6), rejected_at datetime(6), reserved_at datetime(6), issued_at datetime(6), consumed_at datetime(6), cancelled_at datetime(6),
 constraint fk_part_request_service foreign key(service_request_id) references service_requests(id),
 constraint fk_part_request_technician foreign key(technician_profile_id) references technician_profiles(id),
 constraint ck_part_request_status check(status in ('DRAFT','SUBMITTED','UNDER_REVIEW','REJECTED','APPROVED','RESERVED','READY','ISSUED','CONSUMED','CANCELLED'))
);
create index idx_part_requests_technician_created on service_request_part_requests(technician_profile_id,created_at);
create index idx_part_requests_status_created on service_request_part_requests(status,created_at);
create table service_request_part_lines (
 id bigint auto_increment primary key,
 request_id bigint not null,
 part_item_id bigint not null,
 requested_quantity int not null,
 approved_quantity int not null default 0,
 issued_quantity int not null default 0,
 constraint uk_part_request_item unique(request_id,part_item_id),
 constraint fk_part_line_request foreign key(request_id) references service_request_part_requests(id) on delete cascade,
 constraint fk_part_line_item foreign key(part_item_id) references part_items(id),
 constraint ck_part_line_requested_positive check(requested_quantity > 0),
 constraint ck_part_line_approved_nonnegative check(approved_quantity >= 0),
 constraint ck_part_line_issued_nonnegative check(issued_quantity >= 0)
);
create table part_stock_movements (
 id bigint auto_increment primary key,
 part_item_id bigint not null,
 movement_type varchar(20) not null,
 quantity int not null,
 reason varchar(500),
 service_request_id bigint,
 actor_user_id bigint,
 created_at datetime(6) not null,
 constraint fk_part_movement_item foreign key(part_item_id) references part_items(id),
 constraint fk_part_movement_service foreign key(service_request_id) references service_requests(id),
 constraint ck_part_movement_type check(movement_type in ('RESTOCK','ADJUSTMENT','RESERVE','RELEASE','ISSUE','RETURN','CONSUME')),
 constraint ck_part_movement_quantity_positive check(quantity > 0)
);
create index idx_part_movements_item_created on part_stock_movements(part_item_id,created_at);
create table part_request_events (
 id bigint auto_increment primary key,
 request_id bigint not null,
 previous_status varchar(20),
 new_status varchar(20) not null,
 actor_user_id bigint,
 reason varchar(1000),
 created_at datetime(6) not null,
 constraint fk_part_event_request foreign key(request_id) references service_request_part_requests(id) on delete cascade
);
create index idx_part_events_request_created on part_request_events(request_id,created_at);
