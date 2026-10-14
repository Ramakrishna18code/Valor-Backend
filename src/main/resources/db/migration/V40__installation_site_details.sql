-- Phase 3: nullable structured site details for installations without a canonical Building.
ALTER TABLE installation_requests ADD COLUMN site_address_line1 VARCHAR(200) NULL;
ALTER TABLE installation_requests ADD COLUMN site_address_line2 VARCHAR(200) NULL;
ALTER TABLE installation_requests ADD COLUMN site_locality VARCHAR(120) NULL;
ALTER TABLE installation_requests ADD COLUMN site_city VARCHAR(120) NULL;
ALTER TABLE installation_requests ADD COLUMN site_state VARCHAR(120) NULL;
ALTER TABLE installation_requests ADD COLUMN site_postal_code VARCHAR(30) NULL;
ALTER TABLE installation_requests ADD COLUMN site_country VARCHAR(120) NULL;
ALTER TABLE installation_requests ADD COLUMN site_property_description VARCHAR(500) NULL;
