package io.github.bayonle010.escrow.payment.funding.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.payment.funding.domain.InitiatedPayment;
import io.github.bayonle010.escrow.payment.funding.repository.PaymentRepository;
import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

@Service
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    public PaymentQueryService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public InitiatedPayment get(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(payment -> FundingInitiationService.toDomain(payment, false))
                .orElseThrow(() -> new PaymentApiException(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "paymentId",
                        "Payment " + paymentId + " was not found."));
    }
}
