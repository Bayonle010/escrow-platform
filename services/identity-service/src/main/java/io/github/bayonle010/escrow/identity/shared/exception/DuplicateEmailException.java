package io.github.bayonle010.escrow.identity.shared.exception;

public final class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account already exists for this email address.");
    }
}
