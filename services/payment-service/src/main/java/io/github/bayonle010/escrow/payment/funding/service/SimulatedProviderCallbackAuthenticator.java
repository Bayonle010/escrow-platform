package io.github.bayonle010.escrow.payment.funding.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

@Component
public class SimulatedProviderCallbackAuthenticator {

    private final byte[] expectedSecret;

    public SimulatedProviderCallbackAuthenticator(
            @Value("${payment-provider.simulated.callback-secret}") String expectedSecret) {
        this.expectedSecret = expectedSecret.getBytes(StandardCharsets.UTF_8);
    }

    public void authenticate(String providedSecret) {
        byte[] providedBytes = providedSecret == null
                ? new byte[0]
                : providedSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedSecret, providedBytes)) {
            throw new PaymentApiException(
                    ErrorCode.PROVIDER_CALLBACK_UNAUTHORIZED,
                    HttpStatus.UNAUTHORIZED,
                    null,
                    "The simulated provider callback could not be authenticated.");
        }
    }
}
