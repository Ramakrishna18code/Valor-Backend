CREATE TABLE insurance_providers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NULL,
    website_url VARCHAR(500) NULL,
    support_email VARCHAR(254) NULL,
    support_phone VARCHAR(30) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_insurance_provider_name UNIQUE (name),
    CONSTRAINT fk_insurance_provider_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_insurance_providers_active ON insurance_providers(active, name);

CREATE TABLE insurance_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    insured_asset_type VARCHAR(20) NOT NULL,
    coverage_summary VARCHAR(2000) NOT NULL,
    eligibility VARCHAR(2000) NULL,
    minimum_premium DECIMAL(12,2) NULL,
    maximum_premium DECIMAL(12,2) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_insurance_product_provider FOREIGN KEY (provider_id) REFERENCES insurance_providers(id) ON DELETE RESTRICT,
    CONSTRAINT ck_insurance_product_asset_type CHECK (insured_asset_type IN ('TECHNICIAN','LIFT')),
    CONSTRAINT ck_insurance_product_premium CHECK (minimum_premium IS NULL OR minimum_premium >= 0),
    CONSTRAINT ck_insurance_product_premium_range CHECK (maximum_premium IS NULL OR minimum_premium IS NULL OR maximum_premium >= minimum_premium)
);
CREATE INDEX idx_insurance_products_catalog ON insurance_products(active, insured_asset_type, provider_id, name);

CREATE TABLE technician_insurance_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    technician_profile_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    product_id BIGINT NULL,
    insurer_policy_number VARCHAR(120) NOT NULL,
    coverage_start_date DATE NOT NULL,
    coverage_end_date DATE NOT NULL,
    premium_amount DECIMAL(12,2) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL DEFAULT 'DECLARED',
    insurer_reference VARCHAR(160) NULL,
    insurer_review_note VARCHAR(1000) NULL,
    notes VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_technician_policy_owner FOREIGN KEY (technician_profile_id) REFERENCES technician_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_technician_policy_provider FOREIGN KEY (provider_id) REFERENCES insurance_providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_technician_policy_product FOREIGN KEY (product_id) REFERENCES insurance_products(id) ON DELETE RESTRICT,
    CONSTRAINT ck_technician_policy_dates CHECK (coverage_end_date >= coverage_start_date),
    CONSTRAINT ck_technician_policy_premium CHECK (premium_amount IS NULL OR premium_amount >= 0),
    CONSTRAINT ck_technician_policy_status CHECK (status IN ('DECLARED','INSURER_CONFIRMED_ACTIVE','INSURER_CONFIRMED_INACTIVE'))
);
CREATE INDEX idx_technician_policy_owner_expiry ON technician_insurance_policies(technician_profile_id, coverage_end_date, id);
CREATE INDEX idx_technician_policy_number ON technician_insurance_policies(insurer_policy_number);

CREATE TABLE lift_insurance_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lift_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    product_id BIGINT NULL,
    insurer_policy_number VARCHAR(120) NOT NULL,
    coverage_start_date DATE NOT NULL,
    coverage_end_date DATE NOT NULL,
    premium_amount DECIMAL(12,2) NULL,
    insured_value DECIMAL(14,2) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL DEFAULT 'DECLARED',
    insurer_reference VARCHAR(160) NULL,
    insurer_review_note VARCHAR(1000) NULL,
    notes VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_lift_policy_lift FOREIGN KEY (lift_id) REFERENCES lifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lift_policy_provider FOREIGN KEY (provider_id) REFERENCES insurance_providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lift_policy_product FOREIGN KEY (product_id) REFERENCES insurance_products(id) ON DELETE RESTRICT,
    CONSTRAINT ck_lift_policy_dates CHECK (coverage_end_date >= coverage_start_date),
    CONSTRAINT ck_lift_policy_amounts CHECK ((premium_amount IS NULL OR premium_amount >= 0) AND (insured_value IS NULL OR insured_value >= 0)),
    CONSTRAINT ck_lift_policy_status CHECK (status IN ('DECLARED','INSURER_CONFIRMED_ACTIVE','INSURER_CONFIRMED_INACTIVE'))
);
CREATE INDEX idx_lift_policy_lift_expiry ON lift_insurance_policies(lift_id, coverage_end_date, id);
CREATE INDEX idx_lift_policy_number ON lift_insurance_policies(insurer_policy_number);

CREATE TABLE lift_insurance_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_profile_id BIGINT NOT NULL,
    lift_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    product_id BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    coverage_requested VARCHAR(2000) NOT NULL,
    insured_value DECIMAL(14,2) NULL,
    quote_amount DECIMAL(12,2) NULL,
    quote_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    quote_expires_on DATE NULL,
    insurer_quote_reference VARCHAR(160) NULL,
    insurer_notes VARCHAR(1000) NULL,
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_lift_application_customer FOREIGN KEY (customer_profile_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lift_application_lift FOREIGN KEY (lift_id) REFERENCES lifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lift_application_provider FOREIGN KEY (provider_id) REFERENCES insurance_providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lift_application_product FOREIGN KEY (product_id) REFERENCES insurance_products(id) ON DELETE RESTRICT,
    CONSTRAINT ck_lift_application_status CHECK (status IN ('SUBMITTED','QUOTE_RECEIVED','CUSTOMER_ACCEPTED','CUSTOMER_DECLINED','CANCELLED')),
    CONSTRAINT ck_lift_application_amounts CHECK ((insured_value IS NULL OR insured_value >= 0) AND (quote_amount IS NULL OR quote_amount >= 0))
);
CREATE INDEX idx_lift_application_customer ON lift_insurance_applications(customer_profile_id, submitted_at, id);
CREATE INDEX idx_lift_application_status ON lift_insurance_applications(status, submitted_at);

CREATE TABLE insurance_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_reference VARCHAR(40) NOT NULL,
    technician_policy_id BIGINT NULL,
    lift_policy_id BIGINT NULL,
    claimant_user_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'SUBMITTED',
    incident_date DATE NOT NULL,
    description VARCHAR(4000) NOT NULL,
    claimed_amount DECIMAL(14,2) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    insurer_claim_reference VARCHAR(160) NULL,
    insurer_response_note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_insurance_claim_reference UNIQUE (claim_reference),
    CONSTRAINT fk_insurance_claim_technician_policy FOREIGN KEY (technician_policy_id) REFERENCES technician_insurance_policies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_claim_lift_policy FOREIGN KEY (lift_policy_id) REFERENCES lift_insurance_policies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_claimant FOREIGN KEY (claimant_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_insurance_claim_one_policy CHECK ((technician_policy_id IS NOT NULL AND lift_policy_id IS NULL) OR (technician_policy_id IS NULL AND lift_policy_id IS NOT NULL)),
    CONSTRAINT ck_insurance_claim_status CHECK (status IN ('SUBMITTED','DOCUMENTS_REQUESTED','FORWARDED_TO_INSURER','INSURER_DECISION_PENDING','INSURER_APPROVED','INSURER_REJECTED','WITHDRAWN')),
    CONSTRAINT ck_insurance_claim_amount CHECK (claimed_amount IS NULL OR claimed_amount >= 0)
);
CREATE INDEX idx_insurance_claim_claimant ON insurance_claims(claimant_user_id, created_at, id);
CREATE INDEX idx_insurance_claim_status ON insurance_claims(status, updated_at);

CREATE TABLE insurance_renewal_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    technician_policy_id BIGINT NULL,
    lift_policy_id BIGINT NULL,
    requested_by_user_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    requested_start_date DATE NULL,
    note VARCHAR(1000) NULL,
    insurer_reference VARCHAR(160) NULL,
    insurer_response_note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_insurance_renewal_technician_policy FOREIGN KEY (technician_policy_id) REFERENCES technician_insurance_policies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_renewal_lift_policy FOREIGN KEY (lift_policy_id) REFERENCES lift_insurance_policies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_renewal_requester FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_insurance_renewal_one_policy CHECK ((technician_policy_id IS NOT NULL AND lift_policy_id IS NULL) OR (technician_policy_id IS NULL AND lift_policy_id IS NOT NULL)),
    CONSTRAINT ck_insurance_renewal_status CHECK (status IN ('REQUESTED','UNDER_REVIEW','INSURER_CONFIRMED','INSURER_DECLINED','CANCELLED'))
);
CREATE INDEX idx_insurance_renewal_requester ON insurance_renewal_requests(requested_by_user_id, created_at, id);
CREATE INDEX idx_insurance_renewal_status ON insurance_renewal_requests(status, updated_at);

CREATE TABLE insurance_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id BIGINT NULL,
    lift_application_id BIGINT NULL,
    technician_policy_id BIGINT NULL,
    lift_policy_id BIGINT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_insurance_document_claim FOREIGN KEY (claim_id) REFERENCES insurance_claims(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_document_application FOREIGN KEY (lift_application_id) REFERENCES lift_insurance_applications(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_document_technician_policy FOREIGN KEY (technician_policy_id) REFERENCES technician_insurance_policies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_document_lift_policy FOREIGN KEY (lift_policy_id) REFERENCES lift_insurance_policies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_document_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_insurance_document_storage UNIQUE (storage_key),
    CONSTRAINT ck_insurance_document_target CHECK (
        (claim_id IS NOT NULL AND lift_application_id IS NULL AND technician_policy_id IS NULL AND lift_policy_id IS NULL) OR
        (claim_id IS NULL AND lift_application_id IS NOT NULL AND technician_policy_id IS NULL AND lift_policy_id IS NULL) OR
        (claim_id IS NULL AND lift_application_id IS NULL AND technician_policy_id IS NOT NULL AND lift_policy_id IS NULL) OR
        (claim_id IS NULL AND lift_application_id IS NULL AND technician_policy_id IS NULL AND lift_policy_id IS NOT NULL)),
    CONSTRAINT ck_insurance_document_size CHECK (file_size > 0)
);
CREATE INDEX idx_insurance_documents_claim ON insurance_documents(claim_id, created_at);
CREATE INDEX idx_insurance_documents_application ON insurance_documents(lift_application_id, created_at);

CREATE TABLE insurance_expiry_notices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    policy_type VARCHAR(20) NOT NULL,
    policy_id BIGINT NOT NULL,
    days_before_expiry SMALLINT NOT NULL,
    expiry_date DATE NOT NULL,
    message VARCHAR(500) NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_insurance_notice_recipient FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_insurance_notice_stage UNIQUE (policy_type, policy_id, days_before_expiry),
    CONSTRAINT ck_insurance_notice_type CHECK (policy_type IN ('TECHNICIAN','LIFT')),
    CONSTRAINT ck_insurance_notice_days CHECK (days_before_expiry IN (30,15,7,0))
);
CREATE INDEX idx_insurance_notices_recipient ON insurance_expiry_notices(recipient_user_id, read_at, created_at);

CREATE TABLE insurance_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visible_to_user_id BIGINT NULL,
    actor_user_id BIGINT NULL,
    activity_type VARCHAR(80) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    summary VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_insurance_activity_visible_user FOREIGN KEY (visible_to_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_insurance_activity_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_insurance_activity_user ON insurance_activity(visible_to_user_id, created_at, id);
CREATE INDEX idx_insurance_activity_recent ON insurance_activity(created_at, id);
