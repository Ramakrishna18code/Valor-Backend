-- Flyway migration: create lifts, amc, technicians (minimal)

CREATE TABLE IF NOT EXISTS lifts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  lift_number VARCHAR(100),
  model VARCHAR(100),
  manufacturer VARCHAR(100),
  capacity INT,
  floor_count INT,
  serial_number VARCHAR(200),
  installation_date DATE,
  location VARCHAR(255),
  current_status VARCHAR(50),
  amc_status VARCHAR(50),
  warranty_status VARCHAR(100),
  warranty_start_date DATE,
  warranty_end_date DATE,
  last_maintenance_date DATE,
  next_maintenance_date DATE,
  total_breakdowns INT,
  health_score INT,
  machine_room VARCHAR(255),
  qr_code VARCHAR(255),
  specifications TEXT,
  customer_id BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS amc (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lift_id BIGINT,
  start_date DATE,
  end_date DATE,
  provider VARCHAR(255),
  details TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS technicians (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  phone VARCHAR(20),
  email VARCHAR(255),
  skillset VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
