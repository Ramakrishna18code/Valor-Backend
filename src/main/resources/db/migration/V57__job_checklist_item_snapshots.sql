CREATE TABLE job_checklist_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_checklist_id BIGINT NOT NULL,
    source_item_id BIGINT NULL,
    label VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL,
    input_type VARCHAR(30) NOT NULL DEFAULT 'CHECKBOX',
    snapshot_quality VARCHAR(20) NOT NULL DEFAULT 'RECONSTRUCTED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_job_checklist_item_job FOREIGN KEY (job_checklist_id) REFERENCES job_checklists(id) ON DELETE CASCADE,
    CONSTRAINT ck_job_checklist_item_input_type CHECK (input_type IN ('CHECKBOX','TEXT','NUMBER','PHOTO_NOTE')),
    CONSTRAINT ck_job_checklist_item_snapshot_quality CHECK (snapshot_quality IN ('VERIFIED','RECONSTRUCTED')),
    CONSTRAINT uk_job_checklist_item_source UNIQUE (job_checklist_id, source_item_id)
);

CREATE INDEX idx_job_checklist_items_job_sort ON job_checklist_items(job_checklist_id, sort_order, id);

ALTER TABLE checklist_responses
    ADD COLUMN job_checklist_item_id BIGINT NULL;
