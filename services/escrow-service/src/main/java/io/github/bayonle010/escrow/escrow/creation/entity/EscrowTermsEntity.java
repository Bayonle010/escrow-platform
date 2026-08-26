package io.github.bayonle010.escrow.escrow.creation.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "escrow_terms")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EscrowTermsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "terms_id", nullable = false, updatable = false)
    private UUID termsId;

    @Column(name = "escrow_id", nullable = false, updatable = false)
    private UUID escrowId;

    @Column(name = "terms_version", nullable = false, updatable = false)
    private int termsVersion;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "description", nullable = false, updatable = false, length = 2000)
    private String description;

    @Column(name = "category", nullable = false, updatable = false, length = 100)
    private String category;

    @Column(name = "delivery_deadline", nullable = false, updatable = false)
    private Instant deliveryDeadline;

    @Column(name = "inspection_period_days", nullable = false, updatable = false)
    private int inspectionPeriodDays;

    @Column(name = "release_conditions", nullable = false, updatable = false, length = 2000)
    private String releaseConditions;

    @Column(name = "refund_conditions", nullable = false, updatable = false, length = 2000)
    private String refundConditions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
}
