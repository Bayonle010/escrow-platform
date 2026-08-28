package io.github.bayonle010.escrow.payment.funding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

class SimulatedProviderCallbackAuthenticatorTest {

    private final SimulatedProviderCallbackAuthenticator authenticator =
            new SimulatedProviderCallbackAuthenticator("callback-secret");

    @Test
    void acceptsTheConfiguredSecret() {
        assertThatCode(() -> authenticator.authenticate("callback-secret"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrIncorrectSecrets() {
        assertUnauthorized(null);
        assertUnauthorized("wrong-secret");
    }

    private void assertUnauthorized(String secret) {
        assertThatThrownBy(() -> authenticator.authenticate(secret))
                .isInstanceOfSatisfying(PaymentApiException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.PROVIDER_CALLBACK_UNAUTHORIZED);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}
