-- Stage 2: customer assets. V1 remains unchanged; no legacy tables or data are modified.

CREATE TABLE buildings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    building_name VARCHAR(200) NOT NULL,
    building_type VARCHAR(80) NULL,
    address VARCHAR(500) NULL,
    city VARCHAR(100) NULL,
    state VARCHAR(100) NULL,
    pincode VARCHAR(20) NULL,
    emergency_contact_name VARCHAR(160) NULL,
    emergency_contact_phone VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_buildings_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT ck_buildings_status CHECK (CHAR_LENGTH(TRIM(status)) > 0)
);
CREATE INDEX idx_buildings_customer_status ON buildings(customer_id, is_active, status);

CREATE TABLE lifts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    lift_number VARCHAR(80) NULL,
    model VARCHAR(120) NULL,
    manufacturer VARCHAR(120) NULL,
    capacity INT UNSIGNED NULL,
    floor_count INT UNSIGNED NULL,
    serial_number VARCHAR(120) NULL,
    installation_date DATE NULL,
    location VARCHAR(200) NULL,
    current_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    warranty_status VARCHAR(80) NULL,
    warranty_start_date DATE NULL,
    warranty_end_date DATE NULL,
    last_maintenance_date DATE NULL,
    next_maintenance_date DATE NULL,
    health_score TINYINT UNSIGNED NULL,
    machine_room VARCHAR(200) NULL,
    qr_code VARCHAR(255) NULL,
    specifications TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_lifts_building FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE RESTRICT,
    CONSTRAINT uk_lifts_serial UNIQUE (serial_number),
    CONSTRAINT uk_lifts_qr UNIQUE (qr_code),
    CONSTRAINT ck_lifts_status CHECK (current_status IN ('ACTIVE', 'DOWN', 'MAINTENANCE', 'OUT_OF_SERVICE')),
    CONSTRAINT ck_lifts_capacity CHECK (capacity >= 0),
    CONSTRAINT ck_lifts_floor_count CHECK (floor_count >= 0),
    CONSTRAINT ck_lifts_health CHECK (health_score BETWEEN 0 AND 100),
    CONSTRAINT ck_lifts_warranty_dates CHECK (warranty_end_date >= warranty_start_date)
);
CREATE INDEX idx_lifts_building_status ON lifts(building_id, is_active, current_status);

CREATE TABLE amc_contracts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lift_id BIGINT NOT NULL,
    amc_number VARCHAR(80) NOT NULL,
    plan VARCHAR(80) NOT NULL,
    coverage_details TEXT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    renewal_date DATE NULL,
    last_reminder_sent_at DATETIME(6) NULL,
    renewal_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_amc_lift FOREIGN KEY (lift_id) REFERENCES lifts(id) ON DELETE RESTRICT,
    CONSTRAINT uk_amc_number UNIQUE (amc_number),
    CONSTRAINT ck_amc_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'NON_AMC', 'CANCELLED', 'RENEWED')),
    CONSTRAINT ck_amc_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_amc_renewal_count CHECK (renewal_count >= 0)
);
CREATE INDEX idx_amc_lift_status_end ON amc_contracts(lift_id, status, end_date);
