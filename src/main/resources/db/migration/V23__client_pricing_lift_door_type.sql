ALTER TABLE lifts
    ADD COLUMN door_type VARCHAR(20) NULL;

ALTER TABLE lifts
    ADD CONSTRAINT ck_lifts_door_type CHECK (door_type IS NULL OR door_type IN ('MANUAL', 'AUTO'));
