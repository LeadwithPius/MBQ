package com.MBQ.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "blood_inventory_operation",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_blood_inventory_operation_idempotency_key",
        columnNames = "idempotency_key"
    )
)
@Getter
@Setter
public class BloodInventoryOperation {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30)
    private BloodInventoryOperationType operationType;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb")
    private String resultPayload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private StaffUser recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
