ALTER TABLE payment_records DROP CONSTRAINT ck_payments_purpose;
ALTER TABLE payment_records
    ADD CONSTRAINT ck_payments_purpose CHECK (purpose IN (
        'SERVICE_REQUEST', 'AMC_RENEWAL', 'INVOICE', 'WALLET_TOP_UP', 'WALLET_PAYMENT', 'OTHER'
    ));

CREATE TABLE wallet_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_wallet_accounts_customer UNIQUE (customer_id),
    CONSTRAINT fk_wallet_accounts_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT ck_wallet_accounts_currency CHECK (currency = 'INR')
);

CREATE TABLE wallet_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    reference VARCHAR(120) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL,
    description VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_wallet_transactions_reference UNIQUE (reference),
    CONSTRAINT fk_wallet_transactions_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_wallet_transactions_payment FOREIGN KEY (payment_id) REFERENCES payment_records(id) ON DELETE RESTRICT,
    CONSTRAINT ck_wallet_transactions_type CHECK (transaction_type IN ('TOP_UP', 'INVOICE_PAYMENT', 'REFUND')),
    CONSTRAINT ck_wallet_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_wallet_transactions_currency CHECK (currency = 'INR')
);

CREATE INDEX idx_wallet_transactions_customer_created ON wallet_transactions(customer_id, created_at, id);
