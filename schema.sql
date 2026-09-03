-- Valor Lift Services initial MySQL schema.
-- The application keeps Hibernate ddl-auto=update enabled so existing installations
-- can receive newly-added audit columns safely. This file is for a fresh database.

CREATE DATABASE IF NOT EXISTS valor_lift_db;
USE valor_lift_db;

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL UNIQUE,
    alternate_phone VARCHAR(255), email VARCHAR(255) NOT NULL UNIQUE, password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'CUSTOMER', company_name VARCHAR(255),
    door_no VARCHAR(255), building_name VARCHAR(255), street VARCHAR(255), area VARCHAR(255),
    city VARCHAR(255), district VARCHAR(255), state VARCHAR(255), country VARCHAR(255), pincode VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE, account_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    rating DOUBLE, last_service_date DATE, next_scheduled_service_date DATE, total_previous_services INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, created_by VARCHAR(255), updated_by VARCHAR(255),
    deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, token VARCHAR(255) NOT NULL UNIQUE, customer_id BIGINT,
    expires_at DATETIME NOT NULL, revoked BOOLEAN DEFAULT FALSE, FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE IF NOT EXISTS otp_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, mobile_number VARCHAR(20) NOT NULL, otp_hash VARCHAR(100) NOT NULL,
    attempts_remaining INT NOT NULL, max_attempts INT NOT NULL, expires_at DATETIME NOT NULL, locked_until DATETIME,
    verified BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME NOT NULL, verified_at DATETIME,
    INDEX idx_otp_mobile_number (mobile_number), INDEX idx_otp_mobile_created_at (mobile_number, created_at),
    INDEX idx_otp_mobile_status (mobile_number, verified, expires_at, locked_until)
);

CREATE TABLE IF NOT EXISTS buildings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT NOT NULL, building_name VARCHAR(255) NOT NULL,
    building_type VARCHAR(255), address TEXT, city VARCHAR(255), state VARCHAR(255), pincode VARCHAR(255),
    number_of_lifts INT DEFAULT 0, emergency_contact_name VARCHAR(255), emergency_contact_phone VARCHAR(255),
    status VARCHAR(50) DEFAULT 'ACTIVE', created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    created_by VARCHAR(255), updated_by VARCHAR(255), deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE IF NOT EXISTS lifts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT, building_id BIGINT, name VARCHAR(255) NOT NULL,
    lift_number VARCHAR(255), model VARCHAR(255), manufacturer VARCHAR(255), capacity INT, floor_count INT,
    serial_number VARCHAR(255), installation_date DATE, location VARCHAR(255), current_status VARCHAR(50) DEFAULT 'ACTIVE',
    amc_status VARCHAR(50) DEFAULT 'NON_AMC', warranty_status VARCHAR(255), warranty_start_date DATE, warranty_end_date DATE,
    last_maintenance_date DATE, next_maintenance_date DATE, total_breakdowns INT DEFAULT 0, health_score INT DEFAULT 100,
    machine_room VARCHAR(255), qr_code VARCHAR(255), specifications VARCHAR(255), created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    created_by VARCHAR(255), updated_by VARCHAR(255), deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (customer_id) REFERENCES customers(id), FOREIGN KEY (building_id) REFERENCES buildings(id)
);

CREATE TABLE IF NOT EXISTS amcs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, lift_id BIGINT, amc_number VARCHAR(255), plan VARCHAR(255), coverage_details VARCHAR(255),
    start_date DATE, end_date DATE, status VARCHAR(50), renewal_date DATE, last_reminder_sent_at DATE, renewal_count INT DEFAULT 0,
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, created_by VARCHAR(255), updated_by VARCHAR(255),
    deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE, FOREIGN KEY (lift_id) REFERENCES lifts(id)
);

CREATE TABLE IF NOT EXISTS technicians (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, employee_id VARCHAR(255) UNIQUE, name VARCHAR(255), email VARCHAR(255),
    password VARCHAR(255), phone VARCHAR(255), assigned_area VARCHAR(255), specialization VARCHAR(255),
    current_workload INT DEFAULT 0, pending_jobs INT DEFAULT 0, rating DOUBLE, last_working_day DATE, last_active_at DATETIME,
    role VARCHAR(50) DEFAULT 'TECHNICIAN', availability_status VARCHAR(50) DEFAULT 'AVAILABLE', created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL, created_by VARCHAR(255), updated_by VARCHAR(255), deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS service_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, service_id VARCHAR(255) NOT NULL UNIQUE, customer_id BIGINT, lift_id BIGINT, assigned_technician_id BIGINT,
    title VARCHAR(255), description VARCHAR(255), issue_category VARCHAR(255), priority VARCHAR(50), status VARCHAR(50), service_type VARCHAR(50),
    customer_remarks VARCHAR(255), technician_remarks VARCHAR(255), service_requested_at DATETIME, preferred_visit_date DATE, preferred_time_slot VARCHAR(255),
    internal_admin_notes VARCHAR(255), assigned_at DATETIME, started_at DATETIME, paused_at DATETIME, resumed_at DATETIME, completed_at DATETIME,
    customer_signature_path VARCHAR(255), service_report_path VARCHAR(255), estimated_completion_minutes INT, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    created_by VARCHAR(255), updated_by VARCHAR(255), deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (customer_id) REFERENCES customers(id), FOREIGN KEY (lift_id) REFERENCES lifts(id), FOREIGN KEY (assigned_technician_id) REFERENCES technicians(id)
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT NOT NULL, amc_id BIGINT, invoice_number VARCHAR(255), amount DECIMAL(19,2),
    gst_amount DECIMAL(19,2), total_amount DECIMAL(19,2), payment_mode VARCHAR(255), status VARCHAR(50), payment_date_time DATETIME, receipt_number VARCHAR(255),
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, created_by VARCHAR(255), updated_by VARCHAR(255), deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (customer_id) REFERENCES customers(id), FOREIGN KEY (amc_id) REFERENCES amcs(id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, recipient_type VARCHAR(255), recipient_id BIGINT, title VARCHAR(255), message VARCHAR(2000),
    channel VARCHAR(50), status VARCHAR(50), scheduled_at DATETIME, sent_at DATETIME, read_at DATETIME, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    created_by VARCHAR(255), updated_by VARCHAR(255), deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS admins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, employee_id VARCHAR(255) UNIQUE, name VARCHAR(255) NOT NULL, email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, phone VARCHAR(255), designation VARCHAR(255), role VARCHAR(50) NOT NULL DEFAULT 'ADMIN', active BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, created_by VARCHAR(255), updated_by VARCHAR(255), deleted_at DATETIME, deleted_by VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
