CREATE TABLE service_quotations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quotation_number VARCHAR(40) NOT NULL,
    customer_id BIGINT NOT NULL,
    service_request_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    invoice_id BIGINT NULL,
    pricing_code VARCHAR(80) NULL,
    description VARCHAR(500) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    valid_until DATE NULL,
    responded_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_service_quotations_number UNIQUE (quotation_number),
    CONSTRAINT uk_service_quotations_invoice UNIQUE (invoice_id),
    CONSTRAINT fk_service_quotations_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_quotations_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_quotations_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_quotations_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT ck_service_quotations_amounts CHECK (subtotal >= 0 AND tax_amount >= 0 AND total_amount = subtotal + tax_amount AND total_amount > 0),
    CONSTRAINT ck_service_quotations_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_service_quotations_status CHECK (status IN ('ISSUED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX idx_service_quotations_customer_created ON service_quotations(customer_id, created_at, id);
CREATE INDEX idx_service_quotations_request_status ON service_quotations(service_request_id, status, created_at);
