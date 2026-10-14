-- Dedicated Phase 1 installation-request foundation. Existing service requests remain unchanged.
CREATE TABLE installation_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    building_id BIGINT NULL,
    authorization_id BIGINT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    property_type VARCHAR(80) NULL,
    intended_use VARCHAR(120) NULL,
    passenger_or_goods VARCHAR(40) NULL,
    estimated_floor_count INT UNSIGNED NULL,
    estimated_capacity INT UNSIGNED NULL,
    preferred_location VARCHAR(200) NULL,
    requirements TEXT NULL,
    site_contact_name VARCHAR(160) NULL,
    site_contact_phone VARCHAR(30) NULL,
    site_contact_email VARCHAR(254) NULL,
    client_request_key VARCHAR(100) NULL,
    submission_key VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    submitted_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_installation_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_installation_building FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE RESTRICT,
    CONSTRAINT ck_installation_status CHECK (status IN ('DRAFT','SUBMITTED','CANCELLED')),
    CONSTRAINT ck_installation_capacity CHECK (estimated_capacity >= 0),
    CONSTRAINT ck_installation_floors CHECK (estimated_floor_count >= 0),
    CONSTRAINT uk_installation_client_key UNIQUE (customer_id, client_request_key),
    CONSTRAINT uk_installation_submission_key UNIQUE (customer_id, submission_key)
);
CREATE INDEX idx_installation_customer_status ON installation_requests(customer_id, status, created_at);
CREATE INDEX idx_installation_building_status ON installation_requests(building_id, status, created_at);

CREATE TABLE installation_property_authorizations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    owner_name VARCHAR(160) NULL,
    organization_name VARCHAR(200) NULL,
    relationship_type VARCHAR(80) NULL,
    authorization_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    authorization_reference VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_installation_auth_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT ck_installation_auth_status CHECK (authorization_status IN ('PENDING_REVIEW','APPROVED','REJECTED'))
);
CREATE INDEX idx_installation_auth_customer_status ON installation_property_authorizations(customer_id, authorization_status);

ALTER TABLE installation_requests
  ADD CONSTRAINT fk_installation_authorization FOREIGN KEY (authorization_id) REFERENCES installation_property_authorizations(id) ON DELETE RESTRICT;

CREATE TABLE installation_request_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    installation_request_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    from_status VARCHAR(40) NULL,
    to_status VARCHAR(40) NOT NULL,
    notes VARCHAR(2000) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_installation_history_request FOREIGN KEY (installation_request_id) REFERENCES installation_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_installation_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_installation_history_request_changed ON installation_request_history(installation_request_id, changed_at, id);

CREATE TABLE installation_request_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    installation_request_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_installation_document_request FOREIGN KEY (installation_request_id) REFERENCES installation_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_installation_document_user FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_installation_document_storage UNIQUE (storage_key),
    CONSTRAINT ck_installation_document_size CHECK (file_size > 0)
);
CREATE INDEX idx_installation_document_request ON installation_request_documents(installation_request_id, created_at);
