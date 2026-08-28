package io.github.bayonle010.escrow.payment.funding.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.bayonle010.escrow.payment.funding.domain.ConfirmedPayment;
import io.github.bayonle010.escrow.payment.funding.dto.ConfirmPaymentRequest;
import io.github.bayonle010.escrow.payment.funding.dto.ConfirmedPaymentResponse;
import io.github.bayonle010.escrow.payment.funding.service.PaymentConfirmationService;
import io.github.bayonle010.escrow.payment.funding.service.SimulatedProviderCallbackAuthenticator;
import io.github.bayonle010.escrow.payment.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.payment.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/providers/simulated/payments")
@Tag(name = "Simulated Provider", description = "Local payment-provider simulation operations")
public class SimulatedProviderCallbackController {

    public static final String CALLBACK_SECRET_HEADER = "X-Simulated-Provider-Secret";

    private final SimulatedProviderCallbackAuthenticator authenticator;
    private final PaymentConfirmationService confirmationService;

    public SimulatedProviderCallbackController(
            SimulatedProviderCallbackAuthenticator authenticator,
            PaymentConfirmationService confirmationService) {
        this.authenticator = authenticator;
        this.confirmationService = confirmationService;
    }

    @PostMapping("/{paymentId}/confirm")
    @Operation(
            summary = "Confirm a simulated payment",
            description = "Authenticates a simulated provider callback and commits the SUCCEEDED status "
                    + "with a PaymentSucceeded outbox event in one transaction.")
    public ApiResponse<ConfirmedPaymentResponse> confirm(
            @PathVariable UUID paymentId,
            @RequestHeader(name = CALLBACK_SECRET_HEADER, required = false) String callbackSecret,
            @Valid @RequestBody ConfirmPaymentRequest request,
            @Parameter(hidden = true)
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE_NAME) UUID correlationId) {
        authenticator.authenticate(callbackSecret);
        ConfirmedPayment payment = confirmationService.confirm(
                paymentId,
                request.providerReference(),
                correlationId);
        return new ApiResponse<>(
                ConfirmedPaymentResponse.from(payment),
                correlationId.toString());
    }
}
