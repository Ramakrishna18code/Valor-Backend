ALTER TABLE payment_records ADD COLUMN razorpay_order_id VARCHAR(80) NULL;
ALTER TABLE payment_records ADD COLUMN razorpay_payment_id VARCHAR(80) NULL;
ALTER TABLE payment_records ADD COLUMN razorpay_payment_link_id VARCHAR(80) NULL;
ALTER TABLE payment_records ADD COLUMN gateway_status VARCHAR(60) NULL;
ALTER TABLE payment_records ADD COLUMN gateway_synced_at DATETIME NULL;

ALTER TABLE payment_records ADD CONSTRAINT uk_payment_records_razorpay_order UNIQUE (razorpay_order_id);
ALTER TABLE payment_records ADD CONSTRAINT uk_payment_records_razorpay_payment UNIQUE (razorpay_payment_id);

ALTER TABLE payment_records DROP CONSTRAINT ck_payments_status;
ALTER TABLE payment_records
    ADD CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED'));

CREATE TABLE payment_refunds (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    razorpay_refund_id VARCHAR(80) NULL,
    gateway_status VARCHAR(60) NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_refunds_payment FOREIGN KEY (payment_id) REFERENCES payment_records(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_refunds_user FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_payment_refunds_razorpay_refund UNIQUE (razorpay_refund_id),
    CONSTRAINT ck_payment_refunds_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_refunds_status CHECK (status IN ('REQUESTED', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX idx_payment_refunds_payment_created ON payment_refunds(payment_id, created_at);

CREATE TABLE razorpay_webhook_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    razorpay_order_id VARCHAR(80) NULL,
    razorpay_payment_id VARCHAR(80) NULL,
    razorpay_refund_id VARCHAR(80) NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_razorpay_webhook_events_event UNIQUE (event_id)
);
CREATE INDEX idx_razorpay_webhook_order ON razorpay_webhook_events(razorpay_order_id);
CREATE INDEX idx_razorpay_webhook_payment ON razorpay_webhook_events(razorpay_payment_id);

CREATE TABLE technician_latest_locations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    technician_id BIGINT NOT NULL,
    service_request_id BIGINT NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    recorded_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_technician_locations_technician FOREIGN KEY (technician_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_technician_locations_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT uk_technician_locations_request UNIQUE (service_request_id),
    CONSTRAINT ck_technician_locations_lat CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT ck_technician_locations_lng CHECK (longitude >= -180 AND longitude <= 180)
);
CREATE INDEX idx_technician_locations_technician ON technician_latest_locations(technician_id, updated_at);
