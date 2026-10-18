ALTER TABLE invoices ADD COLUMN installation_milestone_id BIGINT NULL;
ALTER TABLE invoices ADD CONSTRAINT fk_invoice_installation_milestone FOREIGN KEY (installation_milestone_id) REFERENCES installation_milestones(id) ON DELETE RESTRICT;
CREATE INDEX idx_invoices_installation_milestone ON invoices(installation_milestone_id, status, created_at);
