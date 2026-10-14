ALTER TABLE notifications ADD COLUMN event_key VARCHAR(180) NULL;
CREATE UNIQUE INDEX ux_notifications_event_key ON notifications(event_key);
