package com.MBQ.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.MBQ.model.BloodGroup;
import com.MBQ.model.PriorityLevel;
import com.MBQ.model.TicketStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes for the blood bank screen (FR5-FR7). */
public final class BloodBankDtos {

    private BloodBankDtos() {}

    /**
     * One blood group's stock at a facility.
     * issuableUnits = verified, unexpired units that can actually be issued (FEFO).
     * expiringSoonUnits = issuable units expiring within EXPIRING_SOON_DAYS.
     */
    public record StockLevelResponse(
            Long stockId,
            Long facilityId,
            BloodGroup bloodGroup,
            int unitsAvailable,
            int lowStockThreshold,
            boolean low,
            int issuableUnits,
            int expiringSoonUnits,
            LocalDateTime lastUpdated) {}

    public record BatchResponse(
            Long id,
            String batchReference,
            LocalDate expiryDate,
            int openingQuantity,
            int remainingQuantity,
            String verificationStatus,
            String sourceReference,
            boolean expired,
            boolean expiringSoon) {}

    public record MovementResponse(
            Long id,
            String movementType,
            int unitsChanged,
            int quantityBefore,
            int quantityAfter,
            String reason,
            String batchReference,
            String recordedBy,
            LocalDateTime createdAt) {}

    public record ThresholdRequest(
            @Min(0) @Max(1000) int lowStockThreshold) {}

    public record DonorRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull BloodGroup bloodGroup,
            @Size(max = 255) String contact,
            Boolean eligible) {}

    public record DonorResponse(
            Long id,
            String name,
            BloodGroup bloodGroup,
            String contact,
            boolean eligible) {}

    public record AlertResponse(
            Long id,
            Long stockId,
            BloodGroup bloodGroup,
            Long donorId,
            String donorName,
            String donorContact,
            LocalDateTime dateSent,
            String responseStatus) {}

    /** Result of "notify donors": alerts are simulated (logged, no real SMS). */
    public record DonorAlertResult(
            Long stockId,
            BloodGroup bloodGroup,
            int donorsNotified,
            int donorsSkipped,
            java.util.List<AlertResponse> alerts) {}

    public record AlertReplyRequest(
            @NotNull @Pattern(regexp = "ACCEPTED|DECLINED") String responseStatus) {}

    /** A patient in the queue flagged by triage as needing blood. */
    public record BloodRequestResponse(
            Long ticketId,
            String ticketNumber,
            Long patientId,
            String patientName,
            BloodGroup patientBloodGroup,
            String departmentName,
            PriorityLevel priorityLevel,
            TicketStatus status,
            LocalDateTime timeIssued) {}
}
