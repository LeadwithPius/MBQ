package com.MBQ.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BloodInventoryDtos {

    private BloodInventoryDtos() {}

    public record ReceiveBatchRequest(
            @NotNull Long stockId,
            @NotBlank @Size(max = 100) String batchReference,
            @NotNull LocalDate expiryDate,
            @Min(1) int units,
            @Size(max = 255) String sourceReference,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record IssueRequest(
            @NotNull Long stockId,
            @Min(1) int units,
            @NotBlank @Size(max = 500) String reason,
            /** Optional: when set, the stock's blood group must be compatible with this patient. */
            Long patientId
    ) {}

    /**
     * Remove units that can no longer be used. EXPIRED writes off everything left in an
     * expired batch (units is ignored); WASTED removes the given units (broken bag, failed
     * crossmatch storage, etc.) from any batch.
     */
    public record WriteOffRequest(
            @NotNull Long batchId,
            @NotNull WriteOffType type,
            Integer units,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public enum WriteOffType { EXPIRED, WASTED }

    public record BatchAllocation(
            Long batchId,
            String batchReference,
            LocalDate expiryDate,
            int units
    ) {}

    public record InventoryOperationResponse(
            UUID operationId,
            String operationType,
            Long stockId,
            int unitsChanged,
            int quantityBefore,
            int quantityAfter,
            List<BatchAllocation> allocations,
            boolean replayed
    ) {}
}
