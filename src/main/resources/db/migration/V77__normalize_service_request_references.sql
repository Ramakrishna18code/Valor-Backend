-- Keep previous references so existing links and development seed lookups remain usable.
ALTER TABLE service_requests ADD COLUMN legacy_service_id VARCHAR(80) NULL;

UPDATE service_requests
SET legacy_service_id = service_id,
    service_id = CASE
        WHEN lift_id IS NULL THEN CONCAT('SR-NEW-', id)
        ELSE CONCAT('SR-LFT-', lift_id, '-', id)
    END
WHERE service_id <> CASE
    WHEN lift_id IS NULL THEN CONCAT('SR-NEW-', id)
    ELSE CONCAT('SR-LFT-', lift_id, '-', id)
END;

CREATE UNIQUE INDEX uk_service_requests_legacy_service_id ON service_requests(legacy_service_id);
