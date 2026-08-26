package io.github.bayonle010.escrow.escrow.shared.exception;

import java.util.UUID;

public class EscrowNotFoundException extends RuntimeException {

    public EscrowNotFoundException(UUID escrowId) {
        super("Escrow " + escrowId + " was not found.");
    }
}
