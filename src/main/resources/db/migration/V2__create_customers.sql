-- Flyway migration: create customers table (minimal columns)
CREATE TABLE IF NOT EXISTS customers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NOT NULL UNIQUE,
  alternate_phone VARCHAR(20),
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(200) NOT NULL,
  role VARCHAR(50),
  company_name VARCHAR(255),
  enabled BOOLEAN DEFAULT TRUE,
  account_status VARCHAR(50),
  rating DOUBLE,
  last_service_date DATE,
  next_scheduled_service_date DATE,
  total_previous_services INT DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers(email);
