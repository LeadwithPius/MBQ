package com.MBQ.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "blood_stock_batch",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_blood_stock_batch_reference",
        columnNames = {"stock_id", "batch_reference"}
    )
)
@Getter
@Setter
public class BloodStockBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private BloodStock stock;

    @Column(name = "batch_reference", nullable = false, length = 100)
    private String batchReference;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "opening_quantity", nullable = false)
    private int openingQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private BloodBatchVerificationStatus verificationStatus;

    @Column(name = "source_reference")
    private String sourceReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private StaffUser recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** created_at is NOT NULL; without this the first insert fails. */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
