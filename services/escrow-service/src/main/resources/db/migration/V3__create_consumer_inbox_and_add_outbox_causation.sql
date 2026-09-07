CREATE TABLE consumer_inbox (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE INDEX idx_escrow_consumer_inbox_processed
    ON consumer_inbox (processed_at);

ALTER TABLE outbox_events
    ADD COLUMN causation_id UUID;
