-- Flyway migration: drop obsolete customer_id from lifts once buildings are in place

ALTER TABLE lifts
  DROP FOREIGN KEY IF EXISTS fk_lifts_customer;

ALTER TABLE lifts
  DROP COLUMN IF EXISTS customer_id;
