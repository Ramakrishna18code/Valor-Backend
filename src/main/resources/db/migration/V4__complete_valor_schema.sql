-- Flyway migration: complete Valor Lift Services & Maintenance schema
-- This creates the normalized database structure with foreign keys, indexes, and audit fields.

CREATE TABLE IF NOT EXISTS customers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  alternate_phone VARCHAR(20),
  address TEXT,
  city VARCHAR(100),
  state VARCHAR(100),
  pincode VARCHAR(20),
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_customers_email (email),
  UNIQUE KEY uk_customers_phone (phone),
  KEY idx_customers_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buildings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  building_name VARCHAR(255) NOT NULL,
  building_type VARCHAR(100),
  address TEXT,
  city VARCHAR(100),
  state VARCHAR(100),
  pincode VARCHAR(20),
  number_of_lifts INT DEFAULT 0,
  emergency_contact_name VARCHAR(255),
  emergency_contact_phone VARCHAR(20),
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_buildings_customer_id (customer_id),
  CONSTRAINT fk_buildings_customer FOREIGN KEY (customer_id)
    REFERENCES customers (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lifts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  building_id BIGINT NOT NULL,
  lift_number VARCHAR(100) NOT NULL,
  name VARCHAR(255) NOT NULL,
  model VARCHAR(100),
  manufacturer VARCHAR(100),
  capacity INT,
  floor_count INT,
  serial_number VARCHAR(200),
  installation_date DATE,
  location VARCHAR(255),
  current_status VARCHAR(50) NOT NULL DEFAULT 'OPERATIONAL',
  warranty_status VARCHAR(50),
  warranty_start_date DATE,
  warranty_end_date DATE,
  last_maintenance_date DATE,
  next_maintenance_date DATE,
  total_breakdowns INT DEFAULT 0,
  health_score INT DEFAULT 100,
  machine_room VARCHAR(255),
  qr_code VARCHAR(255),
  specifications TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_lifts_building_lift_number (building_id, lift_number),
  KEY idx_lifts_building_id (building_id),
  KEY idx_lifts_serial_number (serial_number),
  KEY idx_lifts_lift_number (lift_number),
  CONSTRAINT fk_lifts_building FOREIGN KEY (building_id)
    REFERENCES buildings (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS technicians (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_id VARCHAR(100) NOT NULL,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone VARCHAR(20),
  assigned_area VARCHAR(255),
  specialization VARCHAR(255),
  current_workload INT DEFAULT 0,
  pending_jobs INT DEFAULT 0,
  rating DECIMAL(3,2) DEFAULT 0.00,
  role VARCHAR(50) NOT NULL DEFAULT 'TECHNICIAN',
  availability_status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_technicians_employee_id (employee_id),
  UNIQUE KEY uk_technicians_email (email),
  KEY idx_technicians_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admins (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_id VARCHAR(100) NOT NULL,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone VARCHAR(20),
  designation VARCHAR(100),
  role VARCHAR(50) NOT NULL DEFAULT 'ADMIN',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_admins_employee_id (employee_id),
  UNIQUE KEY uk_admins_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS amcs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lift_id BIGINT NOT NULL,
  amc_number VARCHAR(100) NOT NULL,
  plan VARCHAR(100),
  coverage_details TEXT,
  start_date DATE,
  end_date DATE,
  free_month_end_date DATE,
  renewal_date DATE,
  amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  pending_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  renewal_count INT DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_amcs_amc_number (amc_number),
  KEY idx_amcs_lift_id (lift_id),
  CONSTRAINT fk_amcs_lift FOREIGN KEY (lift_id)
    REFERENCES lifts (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  lift_id BIGINT NOT NULL,
  assigned_technician_id BIGINT,
  service_number VARCHAR(100) NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  issue_category VARCHAR(100),
  priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  service_type VARCHAR(50) NOT NULL DEFAULT 'BREAKDOWN',
  customer_remarks TEXT,
  technician_remarks TEXT,
  service_requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  preferred_visit_date DATE,
  preferred_time_slot VARCHAR(50),
  started_at DATETIME(6),
  completed_at DATETIME(6),
  cancellation_reason TEXT,
  internal_admin_notes TEXT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_service_requests_number (service_number),
  KEY idx_service_requests_customer_id (customer_id),
  KEY idx_service_requests_lift_id (lift_id),
  KEY idx_service_requests_assigned_technician_id (assigned_technician_id),
  KEY idx_service_requests_status (status),
  KEY idx_service_requests_priority (priority),
  CONSTRAINT fk_service_requests_customer FOREIGN KEY (customer_id)
    REFERENCES customers (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  CONSTRAINT fk_service_requests_lift FOREIGN KEY (lift_id)
    REFERENCES lifts (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  CONSTRAINT fk_service_requests_technician FOREIGN KEY (assigned_technician_id)
    REFERENCES technicians (id)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  service_request_id BIGINT NOT NULL,
  technician_id BIGINT,
  action VARCHAR(50) NOT NULL,
  status VARCHAR(50) NOT NULL,
  remarks TEXT,
  started_at DATETIME(6),
  completed_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_service_history_request_id (service_request_id),
  CONSTRAINT fk_service_history_service_request FOREIGN KEY (service_request_id)
    REFERENCES service_requests (id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_service_history_technician FOREIGN KEY (technician_id)
    REFERENCES technicians (id)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_photos (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  service_request_id BIGINT NOT NULL,
  technician_id BIGINT,
  photo_url VARCHAR(2048) NOT NULL,
  photo_type VARCHAR(50) NOT NULL,
  description TEXT,
  uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_service_photos_request_id (service_request_id),
  CONSTRAINT fk_service_photos_service_request FOREIGN KEY (service_request_id)
    REFERENCES service_requests (id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_service_photos_technician FOREIGN KEY (technician_id)
    REFERENCES technicians (id)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_feedback (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  service_request_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment TEXT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_service_feedback_request_id (service_request_id),
  KEY idx_service_feedback_customer_id (customer_id),
  CONSTRAINT fk_service_feedback_service_request FOREIGN KEY (service_request_id)
    REFERENCES service_requests (id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_service_feedback_customer FOREIGN KEY (customer_id)
    REFERENCES customers (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_name VARCHAR(255) NOT NULL,
  sku VARCHAR(100) NOT NULL,
  description TEXT,
  stock_quantity INT NOT NULL DEFAULT 0,
  reorder_level INT NOT NULL DEFAULT 0,
  unit VARCHAR(50),
  location VARCHAR(255),
  status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_inventory_sku (sku),
  KEY idx_inventory_sku (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  inventory_id BIGINT NOT NULL,
  technician_id BIGINT,
  service_request_id BIGINT,
  transaction_type VARCHAR(50) NOT NULL,
  quantity INT NOT NULL,
  reference_number VARCHAR(150),
  remarks TEXT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_inventory_transactions_inventory_id (inventory_id),
  KEY idx_inventory_transactions_technician_id (technician_id),
  KEY idx_inventory_transactions_service_request_id (service_request_id),
  CONSTRAINT fk_inventory_transactions_inventory FOREIGN KEY (inventory_id)
    REFERENCES inventory (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  CONSTRAINT fk_inventory_transactions_technician FOREIGN KEY (technician_id)
    REFERENCES technicians (id)
    ON DELETE SET NULL
    ON UPDATE CASCADE,
  CONSTRAINT fk_inventory_transactions_service_request FOREIGN KEY (service_request_id)
    REFERENCES service_requests (id)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  amc_id BIGINT,
  service_request_id BIGINT,
  amount DECIMAL(12,2) NOT NULL,
  payment_method VARCHAR(50) NOT NULL,
  payment_status VARCHAR(50) NOT NULL,
  transaction_id VARCHAR(150),
  payment_date DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_payments_transaction_id (transaction_id),
  KEY idx_payments_customer_id (customer_id),
  KEY idx_payments_amc_id (amc_id),
  CONSTRAINT fk_payments_customer FOREIGN KEY (customer_id)
    REFERENCES customers (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  CONSTRAINT fk_payments_amc FOREIGN KEY (amc_id)
    REFERENCES amcs (id)
    ON DELETE SET NULL
    ON UPDATE CASCADE,
  CONSTRAINT fk_payments_service_request FOREIGN KEY (service_request_id)
    REFERENCES service_requests (id)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recipient_type VARCHAR(50) NOT NULL,
  recipient_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  channel VARCHAR(50) NOT NULL,
  status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  scheduled_at DATETIME(6),
  sent_at DATETIME(6),
  read_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_notifications_recipient (recipient_type, recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
