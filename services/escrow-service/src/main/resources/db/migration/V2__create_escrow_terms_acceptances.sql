CREATE TABLE escrow_terms_acceptances (
    acceptance_reference UUID PRIMARY KEY,
    escrow_id UUID NOT NULL,
    terms_version INTEGER NOT NULL,
    participant_id UUID NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_terms_acceptance_version
        FOREIGN KEY (escrow_id, terms_version)
        REFERENCES escrow_terms (escrow_id, terms_version),
    CONSTRAINT uq_terms_acceptance_participant
        UNIQUE (escrow_id, terms_version, participant_id),
    CONSTRAINT chk_terms_acceptance_version CHECK (terms_version > 0)
);

CREATE INDEX idx_terms_acceptance_participant
    ON escrow_terms_acceptances (participant_id, accepted_at DESC);
