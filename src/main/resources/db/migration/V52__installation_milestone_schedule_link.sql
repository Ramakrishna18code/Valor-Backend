ALTER TABLE installation_milestones ADD COLUMN schedule_id BIGINT NULL;
ALTER TABLE installation_milestones ADD CONSTRAINT fk_installation_milestone_schedule FOREIGN KEY (schedule_id) REFERENCES installation_schedules(id) ON DELETE RESTRICT;
CREATE INDEX idx_installation_milestone_schedule ON installation_milestones(schedule_id);
