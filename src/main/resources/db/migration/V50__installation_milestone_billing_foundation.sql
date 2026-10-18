CREATE TABLE installation_milestones (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    installation_request_id BIGINT NOT NULL,
    milestone_code VARCHAR(80) NOT NULL,
    sequence_no INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NULL,
    currency VARCHAR(3) NULL,
    invoice_id BIGINT NULL,
    payment_id BIGINT NULL,
    billing_trigger VARCHAR(80) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_installation_milestone_code UNIQUE (installation_request_id, milestone_code),
    CONSTRAINT fk_installation_milestone_request FOREIGN KEY (installation_request_id) REFERENCES installation_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_installation_milestone_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_installation_milestone_payment FOREIGN KEY (payment_id) REFERENCES payment_records(id) ON DELETE RESTRICT,
    CONSTRAINT ck_installation_milestone_status CHECK (status IN ('PLANNED','BILLABLE','INVOICED','PAID','CANCELLED')),
    CONSTRAINT ck_installation_milestone_amount CHECK (amount IS NULL OR amount > 0)
);
CREATE INDEX idx_installation_milestone_request_status ON installation_milestones(installation_request_id, status, sequence_no);
