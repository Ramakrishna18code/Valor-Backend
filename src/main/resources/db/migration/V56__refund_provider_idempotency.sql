alter table payment_refunds add column provider_idempotency_key varchar(80) null;
create unique index uk_payment_refunds_provider_idempotency on payment_refunds(provider_idempotency_key);
