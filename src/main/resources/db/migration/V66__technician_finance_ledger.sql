CREATE TABLE commission_rule_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_type VARCHAR(40) NOT NULL,
    version_number INT NOT NULL,
    basis_type VARCHAR(24) NOT NULL,
    percentage_rate DECIMAL(7,4) NULL,
    fixed_amount DECIMAL(12,2) NULL,
    effective_from DATE NOT NULL,
    effective_until DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approved_by_user_id BIGINT NULL,
    approved_at DATETIME(6) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_commission_rule_version UNIQUE (service_type, version_number),
    CONSTRAINT fk_commission_rule_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_commission_rule_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_commission_rule_basis CHECK (
        (basis_type = 'NET_LABOUR_PERCENT' AND percentage_rate > 0 AND percentage_rate <= 100 AND fixed_amount IS NULL)
        OR (basis_type = 'FIXED_VISIT' AND fixed_amount > 0 AND percentage_rate IS NULL)
    ),
    CONSTRAINT ck_commission_rule_dates CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CONSTRAINT ck_commission_rule_status CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED'))
);
CREATE INDEX idx_commission_rules_effective ON commission_rule_versions(service_type, status, effective_from);

CREATE TABLE technician_commissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    technician_profile_id BIGINT NOT NULL,
    service_request_id BIGINT NOT NULL,
    invoice_id BIGINT NULL,
    rule_version_id BIGINT NOT NULL,
    eligible_work_key VARCHAR(80) NOT NULL,
    eligible_basis DECIMAL(12,2) NOT NULL,
    rate_snapshot DECIMAL(12,4) NOT NULL,
    calculated_amount DECIMAL(12,2) NOT NULL,
    approved_amount DECIMAL(12,2) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_by_user_id BIGINT NULL,
    approved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_commission_work UNIQUE (technician_profile_id, service_request_id, eligible_work_key, rule_version_id),
    CONSTRAINT fk_commission_technician FOREIGN KEY (technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_commission_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_commission_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_commission_rule FOREIGN KEY (rule_version_id) REFERENCES commission_rule_versions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_commission_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_commission_amounts CHECK (eligible_basis >= 0 AND calculated_amount >= 0 AND (approved_amount IS NULL OR approved_amount >= 0)),
    CONSTRAINT ck_commission_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_commission_status CHECK (status IN ('PENDING_APPROVAL', 'READY', 'HELD', 'PAID', 'ADJUSTED'))
);
CREATE INDEX idx_commissions_technician_status ON technician_commissions(technician_profile_id, status, created_at);

CREATE TABLE commission_adjustments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    commission_id BIGINT NOT NULL,
    source_payment_refund_id BIGINT NULL,
    adjustment_amount DECIMAL(12,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_commission_adjustment_commission FOREIGN KEY (commission_id) REFERENCES technician_commissions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_commission_adjustment_refund FOREIGN KEY (source_payment_refund_id) REFERENCES payment_refunds(id) ON DELETE RESTRICT,
    CONSTRAINT fk_commission_adjustment_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_commission_adjustment_refund UNIQUE (commission_id, source_payment_refund_id),
    CONSTRAINT ck_commission_adjustment_nonzero CHECK (adjustment_amount <> 0)
);

CREATE TABLE technician_payout_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_reference VARCHAR(80) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    approved_by_user_id BIGINT NULL,
    approved_at DATETIME(6) NULL,
    external_transfer_reference VARCHAR(120) NULL,
    provider_confirmation_reference VARCHAR(120) NULL,
    confirmed_at DATETIME(6) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_payout_batch_reference UNIQUE (batch_reference),
    CONSTRAINT uk_payout_external_transfer UNIQUE (external_transfer_reference),
    CONSTRAINT fk_payout_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payout_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_payout_period CHECK (period_end >= period_start),
    CONSTRAINT ck_payout_total CHECK (total_amount >= 0),
    CONSTRAINT ck_payout_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_payout_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'RECONCILIATION_REQUIRED'))
);

CREATE TABLE technician_payout_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    commission_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    active_commission_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('ACTIVE', 'PAID') THEN commission_id ELSE NULL END),
    CONSTRAINT fk_payout_item_batch FOREIGN KEY (batch_id) REFERENCES technician_payout_batches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payout_item_commission FOREIGN KEY (commission_id) REFERENCES technician_commissions(id) ON DELETE RESTRICT,
    CONSTRAINT uk_payout_item_batch_commission UNIQUE (batch_id, commission_id),
    CONSTRAINT uk_payout_item_active_commission UNIQUE (active_commission_id),
    CONSTRAINT ck_payout_item_amount CHECK (amount > 0),
    CONSTRAINT ck_payout_item_status CHECK (status IN ('ACTIVE', 'RELEASED', 'PAID'))
);

CREATE TABLE technician_payout_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payout_event_batch FOREIGN KEY (batch_id) REFERENCES technician_payout_batches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payout_event_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_payout_events_batch_time ON technician_payout_events(batch_id, created_at);

CREATE TABLE company_cash_receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_record_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    service_request_id BIGINT NOT NULL,
    technician_profile_id BIGINT NOT NULL,
    receipt_number VARCHAR(80) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    received_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_cash_receipt_payment UNIQUE (payment_record_id),
    CONSTRAINT uk_cash_receipt_number UNIQUE (receipt_number),
    CONSTRAINT fk_cash_receipt_payment FOREIGN KEY (payment_record_id) REFERENCES payment_records(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_receipt_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_receipt_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_receipt_technician FOREIGN KEY (technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cash_receipt_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_receipt_currency CHECK (currency = 'INR')
);
CREATE INDEX idx_cash_receipts_technician_time ON company_cash_receipts(technician_profile_id, received_at);

CREATE TABLE company_cash_remittances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    technician_profile_id BIGINT NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    claimed_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    method VARCHAR(30) NOT NULL,
    handover_reference VARCHAR(120) NULL,
    evidence_storage_key VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    verified_at DATETIME(6) NULL,
    verified_by_user_id BIGINT NULL,
    verification_reason VARCHAR(1000) NULL,
    CONSTRAINT uk_cash_remittance_key UNIQUE (technician_profile_id, idempotency_key),
    CONSTRAINT fk_cash_remittance_technician FOREIGN KEY (technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_remittance_verifier FOREIGN KEY (verified_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cash_remittance_amount CHECK (claimed_amount > 0),
    CONSTRAINT ck_cash_remittance_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_cash_remittance_status CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'DISPUTED'))
);
CREATE INDEX idx_cash_remittance_technician_status ON company_cash_remittances(technician_profile_id, status, submitted_at);

CREATE TABLE company_cash_remittance_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    remittance_id BIGINT NOT NULL,
    receipt_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT uk_cash_remittance_receipt UNIQUE (remittance_id, receipt_id),
    CONSTRAINT fk_cash_remittance_item_remittance FOREIGN KEY (remittance_id) REFERENCES company_cash_remittances(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_remittance_item_receipt FOREIGN KEY (receipt_id) REFERENCES company_cash_receipts(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cash_remittance_item_amount CHECK (amount > 0)
);
CREATE INDEX idx_cash_remittance_items_receipt ON company_cash_remittance_items(receipt_id);

CREATE TABLE technician_expense_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    technician_profile_id BIGINT NOT NULL,
    category VARCHAR(40) NOT NULL,
    description VARCHAR(500) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    purchase_date DATE NOT NULL,
    receipt_storage_key VARCHAR(500) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    review_reason VARCHAR(1000) NULL,
    reimbursement_reference VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_expense_request FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_technician FOREIGN KEY (technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_expense_amount CHECK (amount > 0),
    CONSTRAINT ck_expense_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_expense_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'REIMBURSED'))
);
CREATE INDEX idx_expense_technician_status ON technician_expense_claims(technician_profile_id, status, created_at);

-- Attribute only historical cash that a technician actually verified. Admin-verified
-- office cash is not technician-held cash. Refunded cash needs manual review.
INSERT INTO company_cash_receipts
    (payment_record_id, invoice_id, service_request_id, technician_profile_id, receipt_number, amount, currency, received_at)
SELECT p.id, p.invoice_id, p.service_request_id, t.id, CONCAT('VAL-CASH-', p.id), p.amount, p.currency, otp.verified_at
FROM payment_records p
JOIN cash_payment_otps otp ON otp.payment_id = p.id AND otp.verified_at IS NOT NULL
JOIN technician_profiles t ON t.user_id = otp.verified_by_user_id
WHERE p.status = 'SUCCEEDED' AND p.provider_reference LIKE 'CASH-%'
  AND p.invoice_id IS NOT NULL AND p.service_request_id IS NOT NULL;
