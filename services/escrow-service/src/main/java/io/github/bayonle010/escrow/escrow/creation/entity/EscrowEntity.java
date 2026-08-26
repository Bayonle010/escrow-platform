package io.github.bayonle010.escrow.escrow.creation.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.UuidGenerator;

import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "escrows")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EscrowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "escrow_id", nullable = false, updatable = false)
    private UUID escrowId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(name = "current_terms_version", nullable = false)
    private int currentTermsVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 40)
    private EscrowState state;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "inspection_period_days", nullable = false)
    private int inspectionPeriodDays;

    @Column(name = "delivery_deadline", nullable = false)
    private Instant deliveryDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public void acceptTerms(Instant acceptedAt) {
        state = EscrowState.AWAITING_FUNDING;
        updatedAt = acceptedAt;
    }
}
