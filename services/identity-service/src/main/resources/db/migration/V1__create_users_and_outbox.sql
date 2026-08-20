CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_users_normalized_email UNIQUE (normalized_email),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'RESTRICTED', 'CLOSED'))
);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE status = 'PENDING';
