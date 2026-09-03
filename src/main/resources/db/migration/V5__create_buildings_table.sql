-- Flyway migration: create buildings table and add building relation to lifts

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

ALTER TABLE lifts
  ADD COLUMN building_id BIGINT,
  ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_lifts_building_id ON lifts(building_id);

ALTER TABLE lifts
  ADD CONSTRAINT fk_lifts_building FOREIGN KEY (building_id)
    REFERENCES buildings (id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE;
