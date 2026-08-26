ALTER TABLE outbox_events
    ADD COLUMN correlation_id UUID;

UPDATE outbox_events
SET correlation_id = event_id
WHERE correlation_id IS NULL;

ALTER TABLE outbox_events
    ALTER COLUMN correlation_id SET NOT NULL;
