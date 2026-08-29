CREATE TABLE ledger_accounts (
    account_id UUID PRIMARY KEY,
    owner_type VARCHAR(32) NOT NULL,
    owner_reference VARCHAR(200) NOT NULL,
    account_type VARCHAR(48) NOT NULL,
    normal_side VARCHAR(6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ledger_account_identity
        UNIQUE (owner_type, owner_reference, account_type, currency),
    CONSTRAINT uq_ledger_account_id_currency UNIQUE (account_id, currency),
    CONSTRAINT chk_ledger_account_owner_type
        CHECK (owner_type IN ('ESCROW', 'PROVIDER', 'SYSTEM', 'USER', 'PLATFORM')),
    CONSTRAINT chk_ledger_account_type
        CHECK (account_type IN (
            'ESCROW_HELD', 'PROVIDER_CLEARING', 'SELLER_AVAILABLE',
            'USER_AVAILABLE', 'PAYOUT_RESERVED', 'REFUND_PAYABLE',
            'PLATFORM_REVENUE', 'SUSPENSE'
        )),
    CONSTRAINT chk_ledger_account_normal_side CHECK (normal_side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_account_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_ledger_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE TABLE ledger_account_balances (
    account_id UUID PRIMARY KEY REFERENCES ledger_accounts (account_id),
    posted_balance_minor BIGINT NOT NULL DEFAULT 0,
    available_balance_minor BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_ledger_posted_balance_nonnegative CHECK (posted_balance_minor >= 0),
    CONSTRAINT chk_ledger_available_balance_nonnegative CHECK (available_balance_minor >= 0)
);

CREATE TABLE ledger_journals (
    journal_id UUID PRIMARY KEY,
    business_reference VARCHAR(240) NOT NULL,
    journal_type VARCHAR(48) NOT NULL,
    payment_id UUID NOT NULL,
    escrow_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_reference VARCHAR(200) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ledger_journal_business_reference UNIQUE (business_reference),
    CONSTRAINT uq_ledger_journal_payment UNIQUE (payment_id),
    CONSTRAINT uq_ledger_journal_id_currency UNIQUE (journal_id, currency),
    CONSTRAINT chk_ledger_journal_type CHECK (journal_type IN ('ESCROW_FUNDING')),
    CONSTRAINT chk_ledger_journal_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_ledger_journal_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_ledger_journals_escrow_created
    ON ledger_journals (escrow_id, created_at DESC);

CREATE TABLE ledger_entries (
    entry_id UUID PRIMARY KEY,
    journal_id UUID NOT NULL,
    account_id UUID NOT NULL,
    direction VARCHAR(6) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    sequence INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ledger_entry_sequence UNIQUE (journal_id, sequence),
    CONSTRAINT fk_ledger_entry_journal_currency
        FOREIGN KEY (journal_id, currency) REFERENCES ledger_journals (journal_id, currency),
    CONSTRAINT fk_ledger_entry_account_currency
        FOREIGN KEY (account_id, currency) REFERENCES ledger_accounts (account_id, currency),
    CONSTRAINT chk_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entry_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_ledger_entry_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_ledger_entry_sequence CHECK (sequence > 0)
);

CREATE INDEX idx_ledger_entries_account_created
    ON ledger_entries (account_id, created_at DESC, entry_id DESC);

CREATE TABLE consumer_inbox (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE INDEX idx_consumer_inbox_processed
    ON consumer_inbox (processed_at);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    partition_key UUID NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_ledger_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_ledger_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_ledger_outbox_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE status = 'PENDING';

CREATE FUNCTION reject_ledger_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% records are immutable; create a reversal journal instead', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER ledger_journals_are_immutable
    BEFORE UPDATE OR DELETE ON ledger_journals
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_history_mutation();

CREATE TRIGGER ledger_entries_are_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_history_mutation();

CREATE FUNCTION assert_ledger_journal_balanced()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    target_journal_id UUID;
    entry_count BIGINT;
    debit_total NUMERIC;
    credit_total NUMERIC;
BEGIN
    target_journal_id := CASE
        WHEN TG_TABLE_NAME = 'ledger_journals' THEN NEW.journal_id
        ELSE COALESCE(NEW.journal_id, OLD.journal_id)
    END;

    SELECT
        COUNT(*),
        COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'DEBIT'), 0),
        COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'CREDIT'), 0)
    INTO entry_count, debit_total, credit_total
    FROM ledger_entries
    WHERE journal_id = target_journal_id;

    IF entry_count < 2 OR debit_total <> credit_total THEN
        RAISE EXCEPTION 'posted journal % is not balanced', target_journal_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ledger_journal_balance_from_journal
    AFTER INSERT ON ledger_journals
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_ledger_journal_balanced();

CREATE CONSTRAINT TRIGGER ledger_journal_balance_from_entry
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_ledger_journal_balanced();
