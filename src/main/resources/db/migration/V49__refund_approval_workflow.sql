alter table payment_refunds add column approved_by_user_id bigint null;
alter table payment_refunds add column approved_at timestamp null;
alter table payment_refunds add column second_approved_by_user_id bigint null;
alter table payment_refunds add column second_approved_at timestamp null;
alter table payment_refunds add column approval_reason varchar(500) null;
alter table payment_refunds add column second_approval_reason varchar(500) null;
alter table payment_refunds add constraint fk_payment_refunds_approved_by foreign key (approved_by_user_id) references users(id) on delete restrict;
alter table payment_refunds add constraint fk_payment_refunds_second_approved_by foreign key (second_approved_by_user_id) references users(id) on delete restrict;
create index idx_payment_refunds_approval on payment_refunds(status, approved_by_user_id, second_approved_by_user_id);
