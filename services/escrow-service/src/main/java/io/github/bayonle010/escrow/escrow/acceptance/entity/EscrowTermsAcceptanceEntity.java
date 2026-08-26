package io.github.bayonle010.escrow.escrow.acceptance.entity;

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
@Table(name = "escrow_terms_acceptances")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EscrowTermsAcceptanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "acceptance_reference", nullable = false, updatable = false)
    private UUID acceptanceReference;

    @Column(name = "escrow_id", nullable = false, updatable = false)
    private UUID escrowId;

    @Column(name = "terms_version", nullable = false, updatable = false)
    private int termsVersion;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private UUID participantId;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;
}
