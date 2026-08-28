ALTER TABLE payments
    ADD CONSTRAINT uq_payments_provider_reference
    UNIQUE (provider, provider_reference);
