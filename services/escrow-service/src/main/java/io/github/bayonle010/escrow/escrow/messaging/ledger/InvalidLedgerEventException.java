package io.github.bayonle010.escrow.escrow.messaging.ledger;

public class InvalidLedgerEventException extends RuntimeException {

    public InvalidLedgerEventException(String message) {
        super(message);
    }

    public InvalidLedgerEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
