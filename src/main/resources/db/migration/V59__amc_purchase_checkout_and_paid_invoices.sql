ALTER TABLE payment_records DROP CONSTRAINT ck_payments_purpose;
ALTER TABLE payment_records
    ADD CONSTRAINT ck_payments_purpose CHECK (purpose IN (
        'SERVICE_REQUEST', 'AMC_RENEWAL', 'AMC_PURCHASE', 'INVOICE', 'WALLET_TOP_UP', 'WALLET_PAYMENT', 'OTHER'
    ));

CREATE TABLE amc_purchase_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    lift_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    invoice_id BIGINT NULL,
    amc_contract_id BIGINT NULL,
    plan_code VARCHAR(40) NOT NULL,
    plan_name VARCHAR(80) NOT NULL,
    base_months INT NOT NULL,
    bonus_months INT NOT NULL DEFAULT 0,
    subtotal DECIMAL(12,2) NOT NULL,
    tax_amount DECIMAL(12,2) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_amc_purchase_payment UNIQUE (payment_id),
    CONSTRAINT uk_amc_purchase_invoice UNIQUE (invoice_id),
    CONSTRAINT uk_amc_purchase_contract UNIQUE (amc_contract_id),
    CONSTRAINT fk_amc_purchase_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_purchase_lift FOREIGN KEY (lift_id) REFERENCES lifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_purchase_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_purchase_payment FOREIGN KEY (payment_id) REFERENCES payment_records(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_purchase_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_amc_purchase_contract FOREIGN KEY (amc_contract_id) REFERENCES amc_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT ck_amc_purchase_status CHECK (status IN ('PAYMENT_PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_amc_purchase_plan CHECK (base_months > 0 AND bonus_months >= 0),
    CONSTRAINT ck_amc_purchase_amounts CHECK (subtotal > 0 AND tax_amount >= 0 AND total_amount = subtotal + tax_amount),
    CONSTRAINT ck_amc_purchase_currency CHECK (currency = 'INR')
);
CREATE INDEX idx_amc_purchase_customer_created ON amc_purchase_orders(customer_id, created_at, id);
CREATE INDEX idx_amc_purchase_lift_status ON amc_purchase_orders(lift_id, status, created_at);
