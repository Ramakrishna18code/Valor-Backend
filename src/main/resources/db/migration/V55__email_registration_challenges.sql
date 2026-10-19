create table email_registration_challenges (
 id varchar(64) primary key,
 email varchar(254) not null,
 phone varchar(20),
 password_hash varchar(255) not null,
 full_name varchar(160) not null,
 alternate_phone varchar(20),
 company_name varchar(200),
 address varchar(500),
 has_lift boolean,
 referral_code varchar(32),
 otp_hash varchar(255) not null,
 attempts_remaining smallint not null default 3,
 expires_at timestamp not null,
 locked_until timestamp,
 last_sent_at timestamp not null,
 delivery_status varchar(30) not null,
 verified_at timestamp,
 created_at timestamp not null,
 updated_at timestamp not null,
 constraint uq_email_registration_email unique(email)
);
create index ix_email_registration_expiry on email_registration_challenges(expires_at);
