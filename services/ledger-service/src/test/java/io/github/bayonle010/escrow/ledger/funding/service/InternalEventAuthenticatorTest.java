package io.github.bayonle010.escrow.ledger.funding.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.ledger.shared.api.ErrorCode;
import io.github.bayonle010.escrow.ledger.shared.exception.LedgerApiException;

class InternalEventAuthenticatorTest {

    private final InternalEventAuthenticator authenticator =
            new InternalEventAuthenticator("expected-secret");

    @Test
    void acceptsMatchingSecret() {
        assertThatCode(() -> authenticator.authenticate("expected-secret"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrIncorrectSecret() {
        assertUnauthorized(null);
        assertUnauthorized("incorrect-secret");
    }

    private void assertUnauthorized(String secret) {
        assertThatThrownBy(() -> authenticator.authenticate(secret))
                .isInstanceOfSatisfying(
                        LedgerApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_EVENT_UNAUTHORIZED));
    }
}
