CREATE TABLE invoices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invoice_number VARCHAR(40) NULL,
    customer_id BIGINT NOT NULL,
    service_request_id BIGINT NULL,
    amc_contract_id BIGINT NULL,
    created_by_user_id BIGINT NOT NULL,
    description VARCHAR(500) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    issued_date DATE NOT NULL,
    due_date DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_amc FOREIGN KEY (amc_contract_id) REFERENCES amc_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'VOID', 'CANCELLED')),
    CONSTRAINT ck_invoices_amounts CHECK (subtotal >= 0 AND tax_amount >= 0 AND total_amount >= 0)
);
CREATE INDEX idx_invoices_customer_created ON invoices(customer_id, created_at);
CREATE INDEX idx_invoices_request ON invoices(service_request_id);
CREATE INDEX idx_invoices_amc ON invoices(amc_contract_id);

CREATE TABLE payment_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    service_request_id BIGINT NULL,
    amc_contract_id BIGINT NULL,
    invoice_id BIGINT NULL,
    created_by_user_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    purpose VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    provider_reference VARCHAR(120) NULL,
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_amc FOREIGN KEY (amc_contract_id) REFERENCES amc_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT ck_payments_purpose CHECK (purpose IN ('SERVICE_REQUEST', 'AMC_RENEWAL', 'INVOICE', 'OTHER'))
);
CREATE INDEX idx_payments_customer_created ON payment_records(customer_id, created_at);
CREATE INDEX idx_payments_request ON payment_records(service_request_id);
CREATE INDEX idx_payments_invoice ON payment_records(invoice_id);

CREATE TABLE support_tickets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_reference VARCHAR(40) NULL,
    created_by_user_id BIGINT NOT NULL,
    service_request_id BIGINT NULL,
    category VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    preferred_contact VARCHAR(80) NULL,
    admin_notes VARCHAR(2000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_support_tickets_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_support_tickets_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT uk_support_tickets_reference UNIQUE (ticket_reference),
    CONSTRAINT ck_support_tickets_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_support_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_support_tickets_category CHECK (category IN ('ACCOUNT', 'SERVICE_REQUEST', 'TECHNICAL', 'BILLING', 'OTHER'))
);
CREATE INDEX idx_support_tickets_user_created ON support_tickets(created_by_user_id, created_at);
CREATE INDEX idx_support_tickets_status_created ON support_tickets(status, created_at);

CREATE TABLE amc_renewal_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    amc_contract_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    invoice_id BIGINT NULL,
    payment_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    requested_start_date DATE NOT NULL,
    requested_end_date DATE NOT NULL,
    quoted_amount DECIMAL(12,2) NULL,
    currency VARCHAR(3) NULL,
    customer_notes VARCHAR(1000) NULL,
    admin_notes VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_amc_renewals_contract FOREIGN KEY (amc_contract_id) REFERENCES amc_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_renewals_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_renewals_user FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_renewals_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_renewals_payment FOREIGN KEY (payment_id) REFERENCES payment_records(id) ON DELETE RESTRICT,
    CONSTRAINT ck_amc_renewals_status CHECK (status IN ('REQUESTED', 'QUOTED', 'PAYMENT_PENDING', 'RENEWED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_amc_renewals_dates CHECK (requested_end_date >= requested_start_date),
    CONSTRAINT ck_amc_renewals_quote CHECK (quoted_amount IS NULL OR quoted_amount > 0)
);
CREATE INDEX idx_amc_renewals_customer_created ON amc_renewal_requests(customer_id, created_at);
CREATE INDEX idx_amc_renewals_contract_status ON amc_renewal_requests(amc_contract_id, status);

CREATE TABLE asset_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_type VARCHAR(20) NOT NULL,
    owner_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(160) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asset_documents_user FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_asset_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_asset_documents_owner CHECK (owner_type IN ('BUILDING', 'LIFT')),
    CONSTRAINT ck_asset_documents_size CHECK (file_size > 0)
);
CREATE INDEX idx_asset_documents_owner ON asset_documents(owner_type, owner_id, created_at);
