package io.github.bayonle010.escrow.payment.messaging.outbox;

public record SerializedOutboxEvent(String partitionKey, String value) {
}
