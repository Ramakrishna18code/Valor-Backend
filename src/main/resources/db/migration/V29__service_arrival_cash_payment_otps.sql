CREATE TABLE IF NOT EXISTS arrival_otps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_request_id BIGINT NOT NULL,
    customer_profile_id BIGINT NOT NULL,
    requested_by_technician_id BIGINT NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    attempts_remaining SMALLINT NOT NULL DEFAULT 3,
    max_attempts SMALLINT NOT NULL DEFAULT 3,
    expires_at DATETIME(6) NOT NULL,
    locked_until DATETIME(6) NULL,
    verified_at DATETIME(6) NULL,
    customer_visible_code VARCHAR(16) NULL,
    customer_visible_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_arrival_otps_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id),
    CONSTRAINT fk_arrival_otps_customer FOREIGN KEY (customer_profile_id) REFERENCES customer_profiles(id),
    CONSTRAINT fk_arrival_otps_technician FOREIGN KEY (requested_by_technician_id) REFERENCES technician_profiles(id)
);

CREATE INDEX idx_arrival_otps_request_created ON arrival_otps(service_request_id, created_at);

CREATE TABLE IF NOT EXISTS cash_payment_otps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    service_request_id BIGINT NULL,
    customer_profile_id BIGINT NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    attempts_remaining SMALLINT NOT NULL DEFAULT 3,
    max_attempts SMALLINT NOT NULL DEFAULT 3,
    expires_at DATETIME(6) NOT NULL,
    locked_until DATETIME(6) NULL,
    verified_at DATETIME(6) NULL,
    verified_by_user_id BIGINT NULL,
    customer_visible_code VARCHAR(16) NULL,
    customer_visible_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_cash_payment_otps_payment FOREIGN KEY (payment_id) REFERENCES payment_records(id),
    CONSTRAINT fk_cash_payment_otps_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_cash_payment_otps_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id),
    CONSTRAINT fk_cash_payment_otps_customer FOREIGN KEY (customer_profile_id) REFERENCES customer_profiles(id),
    CONSTRAINT fk_cash_payment_otps_verified_by FOREIGN KEY (verified_by_user_id) REFERENCES users(id)
);

CREATE INDEX idx_cash_payment_otps_payment_created ON cash_payment_otps(payment_id, created_at);
