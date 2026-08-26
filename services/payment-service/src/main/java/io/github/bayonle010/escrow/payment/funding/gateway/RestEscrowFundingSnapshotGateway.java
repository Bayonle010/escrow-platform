package io.github.bayonle010.escrow.payment.funding.gateway;

import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.bayonle010.escrow.payment.funding.domain.EscrowFundingSnapshot;
import io.github.bayonle010.escrow.payment.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.payment.shared.api.ApiResponse;
import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

@Component
public class RestEscrowFundingSnapshotGateway implements EscrowFundingSnapshotGateway {

    private final RestClient restClient;

    public RestEscrowFundingSnapshotGateway(RestClient escrowRestClient) {
        this.restClient = escrowRestClient;
    }

    @Override
    public EscrowFundingSnapshot get(UUID escrowId, UUID correlationId) {
        try {
            ApiResponse<EscrowFundingSnapshot> response = restClient.get()
                    .uri("/api/v1/escrows/{escrowId}", escrowId)
                    .header(CorrelationIdFilter.HEADER_NAME, correlationId.toString())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            if (response == null || response.data() == null) {
                throw unavailable();
            }
            return response.data();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new PaymentApiException(
                    ErrorCode.ESCROW_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "escrowId",
                    "Escrow " + escrowId + " was not found.");
        } catch (PaymentApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private PaymentApiException unavailable() {
        return new PaymentApiException(
                ErrorCode.ESCROW_SERVICE_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                null,
                "The escrow service is temporarily unavailable.");
    }
}
