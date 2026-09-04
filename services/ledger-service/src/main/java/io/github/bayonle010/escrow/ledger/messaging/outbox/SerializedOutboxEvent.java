package io.github.bayonle010.escrow.ledger.messaging.outbox;

public record SerializedOutboxEvent(String partitionKey, String value) {
}
