package io.github.bayonle010.escrow.ledger.funding.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.ledger.shared.api.ErrorCode;
import io.github.bayonle010.escrow.ledger.shared.exception.LedgerApiException;

@Component
public class InternalEventAuthenticator {

    private final byte[] expectedSecret;

    public InternalEventAuthenticator(@Value("${ledger.internal-event-secret}") String expectedSecret) {
        this.expectedSecret = expectedSecret.getBytes(StandardCharsets.UTF_8);
    }

    public void authenticate(String providedSecret) {
        byte[] providedBytes = providedSecret == null
                ? new byte[0]
                : providedSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedSecret, providedBytes)) {
            throw new LedgerApiException(
                    ErrorCode.INTERNAL_EVENT_UNAUTHORIZED,
                    HttpStatus.UNAUTHORIZED,
                    null,
                    "The internal event request could not be authenticated.");
        }
    }
}
